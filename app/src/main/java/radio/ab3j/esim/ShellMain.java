package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SubscriptionService;
import com.genymobile.scrcpy.wrappers.ConnectivityManager;
import com.genymobile.scrcpy.wrappers.TelephonyManager;
import com.genymobile.scrcpy.Ln;
import com.genymobile.scrcpy.FakeContext;

import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

public class ShellMain {

    private static final String EUICC_SERVICE = "econtroller";
    private static final String EUICC_INTERFACE = "com.android.internal.telephony.euicc.IEuiccController";
    private static final String CALLING_PACKAGE = FakeContext.PACKAGE_NAME;

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        if (isRootAvailable() && !isRooted()) {
            System.out.print("Run as root? (y/n): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("y")) {
                relaunchAsRoot();
                return;
            }
        }

        // Print separator at the beginning
        System.out.println("\n" + "=".repeat(60));

        try {
            printActiveSubscriptionsAndNetworks();
            printTelephonyStates();
        } catch (Exception e) {
            Ln.e("Error initializing ShellMain", e);
        }

        // Print separator between status report and CLI menu
        System.out.println("\n" + "=".repeat(60) + "\n");

        while (true) {
            System.out.println("1. Activate an existing eSIM profile");
            System.out.println("2. Download a new eSIM profile");
            System.out.println("3. Exit");
            System.out.print("\nChoose an option: ");

            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1":
                        activateExistingProfile();
                        break;
                    case "2":
                        activateNewProfile();
                        break;
                    case "3":
                        System.out.println("Exiting...");
                        return;
                    default:
                        System.out.println("Invalid choice, try again.\n");
                }
            } catch (Exception e) {
                Ln.e("Error in main loop", e);
            }
        }
    }

    private static boolean isRootAvailable() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "which su"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = reader.readLine();
            p.destroy();
            return result != null && !result.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRooted() {
        try {
            Process p = Runtime.getRuntime().exec("id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = reader.readLine();
            p.destroy();
            return result != null && result.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private static void relaunchAsRoot() {
        try {
            String classPath = System.getProperty("java.class.path");
            String[] cmd = {"su", "-c", "app_process -Djava.class.path=" + classPath + " / radio.ab3j.esim.ShellMain"};

            Process p = new ProcessBuilder(cmd)
                    .inheritIO()
                    .start();

            p.waitFor();
        } catch (Exception e) {
            Ln.e("Failed relaunching as root", e);
        }
    }

    private static void printActiveSubscriptionsAndNetworks() {
        try {
            // Get subscription service
            SubscriptionService ss = ServiceManager.getSubscriptionService();
            if (ss == null) {
                System.out.println("\n📶 Active Subscriptions: Service unavailable");
                return;
            }
            
            // Get available subscriptions
            List<SubscriptionInfo> availableSubs = ss.getAvailableSubscriptionInfoList();
            if (availableSubs == null || availableSubs.isEmpty()) {
                System.out.println("\n📶 Active Subscriptions: No subscriptions found");
                return;
            }
            
            // Get connectivity manager for network info
            ConnectivityManager connectivityManager = ServiceManager.getConnectivityManager();
            if (connectivityManager == null) {
                System.out.println("\n📶 Active Subscriptions: Network service unavailable");
                return;
            }
            
            // Build a map of subId to networks
            Map<Integer, List<NetworkInfo>> subIdToNetworks = buildSubscriptionNetworkMap(connectivityManager);
            
            System.out.println("\n📶 Active Subscriptions:");
            
            // Group subscriptions by slot
            Map<Integer, List<SubscriptionInfo>> slotToSubs = new HashMap<>();
            for (SubscriptionInfo sub : availableSubs) {
                int slot = sub.getSimSlotIndex();
                slotToSubs.computeIfAbsent(slot, k -> new ArrayList<>()).add(sub);
            }
            
            // Get telephony service for IMEI info
            IInterface telephony = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
            
            // Get TelephonyManager for phone number retrieval
            TelephonyManager telephonyManager = ServiceManager.getTelephonyManager();
            
            // Display by slot
            for (int slot = 0; slot < 2; slot++) {
                // Get IMEI for this slot
                String imei = null;
                if (telephony != null) {
                    imei = invokeTelephonyStringMethod(telephony, "getImeiForSlot", slot, CALLING_PACKAGE);
                }
                
                List<SubscriptionInfo> slotSubs = slotToSubs.get(slot);
                if (slotSubs == null || slotSubs.isEmpty()) {
                    System.out.printf("\n  Slot %d:%n", slot);
                    if (imei != null && !imei.isEmpty()) {
                        System.out.printf("    IMEI: %s%n", imei);
                    }
                    continue;
                }
                
                for (SubscriptionInfo sub : slotSubs) {
                    System.out.printf("\n  Slot %d: %s%n", slot, sub.getDisplayName());
                    if (imei != null && !imei.isEmpty()) {
                        System.out.printf("    IMEI: %s%n", imei);
                    }
                    System.out.printf("    ICCID: %s%n", sub.getIccId());
                    
                    // Get and display phone number if available
                    if (telephonyManager != null) {
                        String phoneNumber = telephonyManager.getLine1NumberForSubscriber(
                            sub.getSubscriptionId(), CALLING_PACKAGE);
                        if (phoneNumber != null && !phoneNumber.isEmpty() && !phoneNumber.equals("???????")) {
                            System.out.printf("    Phone Number: %s%n", phoneNumber);
                        } else {
                            System.out.printf("    Phone Number: Not available%n");
                        }
                    }
                    
                    // Check if this subscription is active
                    if (isSubscriptionActive(sub.getSubscriptionId())) {
                        // Show associated networks
                        List<NetworkInfo> networks = subIdToNetworks.get(sub.getSubscriptionId());
                        if (networks != null && !networks.isEmpty()) {
                            System.out.println("    Network Connections:");
                            for (NetworkInfo netInfo : networks) {
                                System.out.printf("      • %s: %s%n", netInfo.type, netInfo.interface_);
                                if (netInfo.ipAddresses != null && !netInfo.ipAddresses.isEmpty()) {
                                    for (String ip : netInfo.ipAddresses) {
                                        System.out.printf("        - %s%n", ip);
                                    }
                                }
                            }
                        } else {
                            System.out.println("    Network Connections: None active");
                        }
                    } else {
                        System.out.println("    Status: ⭕ Inactive");
                    }
                }
            }
            
        } catch (Exception e) {
            System.out.println("\n📶 Active Subscriptions: Error accessing service");
            Ln.e("Error in printActiveSubscriptionsAndNetworks", e);
        }
    }
    
    private static class NetworkInfo {
        String type;
        String interface_;
        List<String> ipAddresses;
        String status;
        
        NetworkInfo(String type, String interface_, List<String> ipAddresses, String status) {
            this.type = type;
            this.interface_ = interface_;
            this.ipAddresses = ipAddresses;
            this.status = status;
        }
    }
    
    private static Map<Integer, List<NetworkInfo>> buildSubscriptionNetworkMap(ConnectivityManager cm) {
        Map<Integer, List<NetworkInfo>> map = new HashMap<>();
        
        try {
            Object[] networks = cm.getAllNetworks();
            if (networks == null) return map;
            
            for (Object network : networks) {
                Object capabilities = cm.getNetworkCapabilities(network);
                if (capabilities == null) continue;
                
                // Get subscription ID from network specifier
                Object networkSpecifier = cm.getNetworkSpecifier(capabilities);
                if (networkSpecifier == null) continue;
                
                int subId = parseSubIdFromSpecifier(networkSpecifier.toString());
                if (subId == -1) continue;
                
                // Get transport types
                String transportTypes = getTransportTypes(cm, capabilities);
                
                // Determine network type
                String networkType = determineNetworkType(cm, capabilities);
                
                // Get link properties
                Object linkProperties = cm.getLinkProperties(network);
                String interfaceName = "unknown";
                List<String> ipAddresses = new ArrayList<>();
                
                if (linkProperties != null) {
                    String iface = cm.getInterfaceName(linkProperties);
                    if (iface != null) {
                        interfaceName = iface;
                    }
                    
                    List<?> linkAddresses = cm.getLinkAddresses(linkProperties);
                    if (linkAddresses != null) {
                        for (Object addr : linkAddresses) {
                            String addrStr = addr.toString();
                            // IPv6 addresses contain colons (not just double colons)
                            // IPv4 addresses contain dots
                            if (addrStr.contains(":") && !addrStr.contains(".")) {
                                ipAddresses.add("IPv6: " + addrStr);
                            } else if (addrStr.contains(".")) {
                                ipAddresses.add("IPv4: " + addrStr);
                            } else {
                                ipAddresses.add(addrStr);
                            }
                        }
                    }
                }
                
                // Build status string
                List<String> statusFlags = new ArrayList<>();
                if (cm.hasCapability(capabilities, ConnectivityManager.NET_CAPABILITY_VALIDATED)) {
                    statusFlags.add("✅ Validated");
                }
                if (cm.hasCapability(capabilities, ConnectivityManager.NET_CAPABILITY_NOT_ROAMING)) {
                    statusFlags.add("🏠 Home");
                } else {
                    statusFlags.add("✈️ Roaming");
                }
                String status = String.join(", ", statusFlags);
                
                // Include transport types in network type
                String fullNetworkType = networkType + " [" + transportTypes + "]";
                
                NetworkInfo netInfo = new NetworkInfo(fullNetworkType, interfaceName, ipAddresses, status);
                map.computeIfAbsent(subId, k -> new ArrayList<>()).add(netInfo);
            }
        } catch (Exception e) {
            Ln.e("Error building network map", e);
        }
        
        return map;
    }
    
    private static boolean isSubscriptionActive(int subId) {
        try {
            IInterface subService = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
            if (subService == null) return false;
            
            FakeContext mContext = FakeContext.get();
            
            // Try to get active subscription info
            Method getActiveSubscriptionInfoMethod = null;
            try {
                getActiveSubscriptionInfoMethod = subService.getClass().getMethod("getActiveSubscriptionInfo", 
                    int.class, String.class, String.class);
                Object result = getActiveSubscriptionInfoMethod.invoke(subService, subId, 
                    mContext.getOpPackageName(), null);
                return result != null;
            } catch (NoSuchMethodException e) {
                try {
                    getActiveSubscriptionInfoMethod = subService.getClass().getMethod("getActiveSubscriptionInfo", int.class);
                    Object result = getActiveSubscriptionInfoMethod.invoke(subService, subId);
                    return result != null;
                } catch (Exception e2) {
                    // Can't determine active status
                    return true; // Assume active if we can't check
                }
            }
        } catch (Exception e) {
            return true; // Assume active if error
        }
    }
    
    private static Set<Integer> getActiveSubscriptionIds() {
        Set<Integer> activeSubIds = new HashSet<>();
        try {
            SubscriptionService ss = ServiceManager.getSubscriptionService();
            if (ss != null) {
                List<SubscriptionInfo> subs = ss.getAvailableSubscriptionInfoList();
                if (subs != null) {
                    for (SubscriptionInfo sub : subs) {
                        activeSubIds.add(sub.getSubscriptionId());
                    }
                }
            }
        } catch (Exception e) {
            Ln.e("Error getting active subscription IDs", e);
        }
        return activeSubIds;
    }
    
    private static Map<Integer, String> getSubIdToCarrierMap() {
        Map<Integer, String> map = new HashMap<>();
        try {
            SubscriptionService ss = ServiceManager.getSubscriptionService();
            if (ss != null) {
                List<SubscriptionInfo> subs = ss.getAvailableSubscriptionInfoList();
                if (subs != null) {
                    for (SubscriptionInfo sub : subs) {
                        map.put(sub.getSubscriptionId(), sub.getDisplayName().toString());
                    }
                }
            }
        } catch (Exception e) {
            Ln.e("Error building carrier map", e);
        }
        return map;
    }
    
    private static int parseSubIdFromSpecifier(String specifier) {
        // Parse "TelephonyNetworkSpecifier [mSubId = 3]" format
        int startIdx = specifier.indexOf("mSubId = ");
        if (startIdx != -1) {
            startIdx += "mSubId = ".length();
            int endIdx = specifier.indexOf("]", startIdx);
            if (endIdx != -1) {
                try {
                    return Integer.parseInt(specifier.substring(startIdx, endIdx));
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return -1;
    }
    
    private static String determineNetworkType(ConnectivityManager cm, Object capabilities) {
        if (cm.hasCapability(capabilities, ConnectivityManager.NET_CAPABILITY_IMS)) {
            return "IMS/VoLTE";
        } else if (cm.hasCapability(capabilities, ConnectivityManager.NET_CAPABILITY_INTERNET)) {
            return "Internet/Data";
        } else if (cm.hasCapability(capabilities, ConnectivityManager.NET_CAPABILITY_MMS)) {
            return "MMS";
        } else {
            return "Other";
        }
    }
    
    private static String getTransportTypes(ConnectivityManager cm, Object capabilities) {
        List<String> transports = new ArrayList<>();
        
        // Check all known transport types
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_CELLULAR)) {
            transports.add("CELLULAR");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_WIFI)) {
            transports.add("WIFI");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_BLUETOOTH)) {
            transports.add("BLUETOOTH");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_ETHERNET)) {
            transports.add("ETHERNET");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_VPN)) {
            transports.add("VPN");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_WIFI_AWARE)) {
            transports.add("WIFI_AWARE");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_LOWPAN)) {
            transports.add("LOWPAN");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_TEST)) {
            transports.add("TEST");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_USB)) {
            transports.add("USB");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_THREAD)) {
            transports.add("THREAD");
        }
        if (cm.hasTransport(capabilities, ConnectivityManager.TRANSPORT_SATELLITE)) {
            transports.add("SATELLITE");
        }
        
        return transports.isEmpty() ? "UNKNOWN" : String.join("+", transports);
    }




    private static void printTelephonyStates() {
        IInterface telephony = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        if (telephony == null) {
            Ln.e("ITelephony unavailable");
            return;
        }

        int subId = getActiveSubscriptionId();
        System.out.println("\n📡 Telephony Status:\n");

        Map<String, Object[]> methods = new LinkedHashMap<>();
        methods.put("isConcurrentVoiceAndDataAllowed", new Object[]{subId});
        methods.put("isDataConnectivityPossible", new Object[]{subId});
        methods.put("isDataEnabled", new Object[]{subId});
        methods.put("isHearingAidCompatibilitySupported", new Object[]{});
        methods.put("isIdle", new Object[]{CALLING_PACKAGE});
        methods.put("isIdleForSubscriber", new Object[]{subId, CALLING_PACKAGE});
        methods.put("isImsRegistered", new Object[]{subId});
        methods.put("isOffhook", new Object[]{CALLING_PACKAGE});
        methods.put("isOffhookForSubscriber", new Object[]{subId, CALLING_PACKAGE});
        methods.put("isRadioOn", new Object[]{CALLING_PACKAGE});
        methods.put("isRadioOnForSubscriber", new Object[]{subId, CALLING_PACKAGE});
        methods.put("isRinging", new Object[]{CALLING_PACKAGE});
        methods.put("isRingingForSubscriber", new Object[]{subId, CALLING_PACKAGE});
        methods.put("isTtyModeSupported", new Object[]{});
        methods.put("isUserDataEnabled", new Object[]{subId});
        methods.put("isVideoCallingEnabled", new Object[]{CALLING_PACKAGE});
        methods.put("isVideoTelephonyAvailable", new Object[]{subId});
        methods.put("isVolteAvailable", new Object[]{subId});
        methods.put("isWifiCallingAvailable", new Object[]{subId});
        methods.put("isWorldPhone", new Object[]{});

        for (String method : methods.keySet()) {
            printBoolMethod(telephony, method, methods.get(method));
        }
    }

    private static void printBoolMethod(IInterface srv, String method, Object... args) {
        try {
            Class<?>[] types = Arrays.stream(args)
                    .map(a -> a instanceof Integer ? int.class : String.class)
                    .toArray(Class[]::new);
            Method m = srv.getClass().getMethod(method, types);
            boolean res = (boolean) m.invoke(srv, args);
            System.out.printf("  - %s: %s%n", method, res ? "✅ YES" : "❌ NO");
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device, skip silently
        } catch (Exception e) {
            Ln.e("Reflection error: " + method, e);
            System.out.printf("  - %s: ⚠️ ERROR%n", method);
        }
    }

    private static String invokeTelephonyStringMethod(IInterface srv, String method, Object... args) {
        try {
            // Special handling for getImeiForSlot
            if ("getImeiForSlot".equals(method) && args.length == 2) {
                // First try with 3 parameters (slot, package, featureId)
                try {
                    Method m = srv.getClass().getMethod(method, int.class, String.class, String.class);
                    return (String) m.invoke(srv, args[0], args[1], null);
                } catch (NoSuchMethodException e) {
                    // Fall back to 2-parameter version
                    try {
                        Method m = srv.getClass().getMethod(method, int.class, String.class);
                        return (String) m.invoke(srv, args[0], args[1]);
                    } catch (NoSuchMethodException e2) {
                        // Neither version exists
                        return null;
                    }
                }
            }
            
            Class<?>[] types = Arrays.stream(args)
                    .map(a -> a instanceof Integer ? int.class : String.class)
                    .toArray(Class[]::new);
            Method m = srv.getClass().getMethod(method, types);
            return (String) m.invoke(srv, args);
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device, return null silently
            return null;
        } catch (Exception e) {
            Ln.e("Reflection error: " + method, e);
            return null;
        }
    }

    private static int getActiveSubscriptionId() {
        try {
            IInterface subService = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
            Method m = subService.getClass().getMethod("getDefaultSubId");
            return (int) m.invoke(subService);
        } catch (Exception e) {
            Ln.e("Active subscription ID error", e);
            return -1;
        }
    }

    private static void activateNewProfile() {
        try {
            System.out.print("Enter eSIM activation code (LPA format): ");
            String activationCode = scanner.nextLine().trim();

            if (activationCode.isEmpty()) {
                Ln.e("Activation code was empty!");
                return;
            }

            IInterface euiccController = ServiceManager.getService(EUICC_SERVICE, EUICC_INTERFACE);
            if (euiccController == null) {
                Ln.e("Could not get IEuiccController service");
                return;
            }

            DownloadableSubscription subscription = DownloadableSubscription.forActivationCode(activationCode);

            Method downloadSubscription = euiccController.getClass().getMethod(
                "downloadSubscription",
                DownloadableSubscription.class, boolean.class,
                String.class, android.app.PendingIntent.class
            );

            Ln.i("Activating new eSIM profile...");
            downloadSubscription.invoke(euiccController,
                    subscription,
                    true,
                    CALLING_PACKAGE,
                    null
            );

            Ln.i("Requested eSIM profile activation (new profile). Check device.");

        } catch (Exception e) {
            Ln.e("Error activating new profile", e);
        }
    }

    private static void activateExistingProfile() {
        try {
            SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
            List<SubscriptionInfo> subscriptions = subscriptionService.getAvailableSubscriptionInfoList();

            if (subscriptions == null || subscriptions.isEmpty()) {
                System.out.println("No existing eSIM profiles found.");
                return;
            }

            int activeSubId = getActiveSubscriptionId();

            System.out.println("\n📑 Existing eSIM profiles:");
            int index = 1;
            for (SubscriptionInfo sub : subscriptions) {
                boolean isActive = sub.getSubscriptionId() == activeSubId;
                System.out.printf("%d. %s (ICCID: %s)%s%n",
                        index++,
                        sub.getDisplayName(),
                        sub.getIccId(),
                        isActive ? " [ACTIVE ✅]" : ""
                );
            }

            System.out.print("\nSelect profile number to activate: ");
            int choice = Integer.parseInt(scanner.nextLine().trim());

            if (choice < 1 || choice > subscriptions.size()) {
                System.out.println("Invalid selection.");
                return;
            }

            int subscriptionId = subscriptions.get(choice - 1).getSubscriptionId();

            IInterface euiccController = ServiceManager.getService(EUICC_SERVICE, EUICC_INTERFACE);
            if (euiccController == null) {
                Ln.e("Could not get IEuiccController service");
                return;
            }

            Method switchToSubscription = euiccController.getClass().getMethod(
                "switchToSubscription",
                int.class, String.class, android.app.PendingIntent.class
            );

            Ln.i("Activating existing eSIM profile...");
            switchToSubscription.invoke(euiccController,
                    subscriptionId,
                    CALLING_PACKAGE,
                    null
            );

            Ln.i("Requested activation for existing eSIM profile. Check device.");

        } catch (NumberFormatException e) {
            Ln.e("Invalid number format", e);
        } catch (Exception e) {
            Ln.e("Error activating existing profile", e);
        }
    }
}

