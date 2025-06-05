package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SubscriptionService;
import com.genymobile.scrcpy.wrappers.ConnectivityManager;
import com.genymobile.scrcpy.wrappers.TelephonyManager;
import com.genymobile.scrcpy.Ln;
import com.genymobile.scrcpy.FakeContext;

import android.os.Bundle;
import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ShellMain {

    private static final String EUICC_SERVICE = "econtroller";
    private static final String EUICC_INTERFACE = "com.android.internal.telephony.euicc.IEuiccController";
    private static final String CALLING_PACKAGE = FakeContext.PACKAGE_NAME;
    private static final String TELEPHONY_SERVICE = "phone";
    private static final String TELEPHONY_INTERFACE = "com.android.internal.telephony.ITelephony";
    private static final String SUBSCRIPTION_SERVICE = "isub";
    private static final String SUBSCRIPTION_INTERFACE = "com.android.internal.telephony.ISub";
    private static final String STATUS_SEPARATOR = "=" + "=".repeat(59);
    private static final String UNAVAILABLE_PHONE = "Not available";
    private static final String UNKNOWN_PLACEHOLDER = "???????"; 
    
    private static final Scanner scanner = new Scanner(System.in);
    
    private static final class MenuOptions {
        static final String ACTIVATE_EXISTING = "1";
        static final String DOWNLOAD_NEW = "2";
        static final String SATELLITE_DEMO_MODE = "3";
        static final String EXIT = "4";
    }
    
    private static final class StatusEmojis {
        static final String SUBSCRIPTIONS = "📶";
        static final String TELEPHONY = "📡";
        static final String SATELLITE = "🛰️";
        static final String VALIDATED = "✅";
        static final String HOME = "🏠";
        static final String ROAMING = "✈️";
        static final String INACTIVE = "⭕";
        static final String YES = "✅ YES";
        static final String NO = "❌ NO";
        static final String ERROR = "⚠️ ERROR";
        static final String ACTIVE = "✅";
    }

    public static void main(String[] args) {
        if (isRootAvailable() && !isRooted()) {
            System.out.print("Run as root? (y/n): ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("y")) {
                relaunchAsRoot();
                return;
            }
        }

        System.out.println("\n" + STATUS_SEPARATOR);
        
        displaySystemStatus();
        
        System.out.println("\n" + STATUS_SEPARATOR + "\n");
        
        runInteractiveMenu();
    }
    
    private static void displaySystemStatus() {
        try {
            printActiveSubscriptionsAndNetworks();
            printTelephonyStates();
        } catch (Exception e) {
            Ln.e("Error displaying system status", e);
        }
    }
    
    private static void runInteractiveMenu() {
        while (true) {
            displayMenuOptions();
            String choice = scanner.nextLine().trim();
            
            try {
                if (handleMenuChoice(choice)) {
                    break;
                }
            } catch (Exception e) {
                Ln.e("Error in menu operation", e);
            }
        }
    }
    
    private static void displayMenuOptions() {
        System.out.println("1. Activate an existing eSIM profile");
        System.out.println("2. Download a new eSIM profile");
        System.out.println("3. Enable satellite demo mode");
        System.out.println("4. Exit");
        System.out.print("\nChoose an option: ");
    }
    
    private static boolean handleMenuChoice(String choice) {
        switch (choice) {
            case MenuOptions.ACTIVATE_EXISTING:
                activateExistingProfile();
                return false;
            case MenuOptions.DOWNLOAD_NEW:
                activateNewProfile();
                return false;
            case MenuOptions.SATELLITE_DEMO_MODE:
                enableSatelliteDemoMode();
                return false;
            case MenuOptions.EXIT:
                System.out.println("Exiting...");
                return true;
            default:
                System.out.println("Invalid choice, try again.\n");
                return false;
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
        SubscriptionDisplayContext context = createSubscriptionDisplayContext();
        if (context == null) {
            return;
        }
        
        System.out.println("\n" + StatusEmojis.SUBSCRIPTIONS + " Active Subscriptions:");
        
        Map<Integer, List<SubscriptionInfo>> slotToSubs = groupSubscriptionsBySlot(context.availableSubscriptions);
        Map<Integer, List<NetworkInfo>> subIdToNetworks = buildSubscriptionNetworkMap(context.connectivityManager);
        
        for (int slot = 0; slot < 2; slot++) {
            displaySlotInformation(slot, slotToSubs, subIdToNetworks, context);
        }
    }
    
    private static class SubscriptionDisplayContext {
        final SubscriptionService subscriptionService;
        final List<SubscriptionInfo> availableSubscriptions;
        final ConnectivityManager connectivityManager;
        final IInterface telephonyService;
        final TelephonyManager telephonyManager;
        
        SubscriptionDisplayContext(SubscriptionService ss, List<SubscriptionInfo> subs, 
                                 ConnectivityManager cm, IInterface tel, TelephonyManager tm) {
            this.subscriptionService = ss;
            this.availableSubscriptions = subs;
            this.connectivityManager = cm;
            this.telephonyService = tel;
            this.telephonyManager = tm;
        }
    }
    
    private static SubscriptionDisplayContext createSubscriptionDisplayContext() {
        try {
            SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
            if (subscriptionService == null) {
                System.out.println("\n" + StatusEmojis.SUBSCRIPTIONS + " Active Subscriptions: Service unavailable");
                return null;
            }
            
            List<SubscriptionInfo> availableSubscriptions = subscriptionService.getAvailableSubscriptionInfoList();
            if (availableSubscriptions == null || availableSubscriptions.isEmpty()) {
                System.out.println("\n" + StatusEmojis.SUBSCRIPTIONS + " Active Subscriptions: No subscriptions found");
                return null;
            }
            
            ConnectivityManager connectivityManager = ServiceManager.getConnectivityManager();
            if (connectivityManager == null) {
                System.out.println("\n" + StatusEmojis.SUBSCRIPTIONS + " Active Subscriptions: Network service unavailable");
                return null;
            }
            
            IInterface telephonyService = ServiceManager.getService(TELEPHONY_SERVICE, TELEPHONY_INTERFACE);
            TelephonyManager telephonyManager = ServiceManager.getTelephonyManager();
            
            return new SubscriptionDisplayContext(subscriptionService, availableSubscriptions, 
                                                connectivityManager, telephonyService, telephonyManager);
        } catch (Exception e) {
            System.out.println("\n" + StatusEmojis.SUBSCRIPTIONS + " Active Subscriptions: Error accessing service");
            Ln.e("Error creating subscription display context", e);
            return null;
        }
    }
    
    private static Map<Integer, List<SubscriptionInfo>> groupSubscriptionsBySlot(List<SubscriptionInfo> subscriptions) {
        Map<Integer, List<SubscriptionInfo>> slotToSubs = new HashMap<>();
        for (SubscriptionInfo subscription : subscriptions) {
            int slot = subscription.getSimSlotIndex();
            slotToSubs.computeIfAbsent(slot, k -> new ArrayList<>()).add(subscription);
        }
        return slotToSubs;
    }
    
    private static void displaySlotInformation(int slot, Map<Integer, List<SubscriptionInfo>> slotToSubs, 
                                             Map<Integer, List<NetworkInfo>> subIdToNetworks, 
                                             SubscriptionDisplayContext context) {
        String slotImei = getImeiForSlot(context.telephonyService, slot);
        List<SubscriptionInfo> slotSubscriptions = slotToSubs.get(slot);
        
        if (slotSubscriptions == null || slotSubscriptions.isEmpty()) {
            displayEmptySlot(slot, slotImei);
            return;
        }
        
        for (SubscriptionInfo subscription : slotSubscriptions) {
            displaySubscriptionDetails(slot, subscription, slotImei, subIdToNetworks, context);
        }
    }
    
    private static void displayEmptySlot(int slot, String imei) {
        System.out.printf("\n  Slot %d:%n", slot);
        if (imei != null && !imei.isEmpty()) {
            System.out.printf("    IMEI: %s%n", imei);
        }
    }
    
    private static void displaySubscriptionDetails(int slot, SubscriptionInfo subscription, String imei, 
                                                  Map<Integer, List<NetworkInfo>> subIdToNetworks, 
                                                  SubscriptionDisplayContext context) {
        System.out.printf("\n  Slot %d: %s%n", slot, subscription.getDisplayName());
        
        if (imei != null && !imei.isEmpty()) {
            System.out.printf("    IMEI: %s%n", imei);
        }
        
        System.out.printf("    ICCID: %s%n", subscription.getIccId());
        
        displayPhoneNumber(subscription, context.telephonyManager);
        displayNetworkStatus(subscription, subIdToNetworks);
    }
    
    private static String getImeiForSlot(IInterface telephonyService, int slot) {
        if (telephonyService != null) {
            return invokeTelephonyStringMethod(telephonyService, "getImeiForSlot", slot, CALLING_PACKAGE);
        }
        return null;
    }
    
    private static void displayPhoneNumber(SubscriptionInfo subscription, TelephonyManager telephonyManager) {
        if (telephonyManager != null) {
            String phoneNumber = getPhoneNumberForSubscription(telephonyManager, subscription.getSubscriptionId());
            if (phoneNumber != null && !phoneNumber.isEmpty() && !phoneNumber.equals(UNKNOWN_PLACEHOLDER)) {
                System.out.printf("    Phone Number: %s%n", phoneNumber);
            } else {
                System.out.printf("    Phone Number: %s%n", UNAVAILABLE_PHONE);
            }
        }
    }
    
    private static void displayNetworkStatus(SubscriptionInfo subscription, Map<Integer, List<NetworkInfo>> subIdToNetworks) {
        if (isSubscriptionActive(subscription.getSubscriptionId())) {
            displayActiveNetworks(subscription.getSubscriptionId(), subIdToNetworks);
        } else {
            System.out.println("    Status: " + StatusEmojis.INACTIVE + " Inactive");
        }
    }
    
    private static void displayActiveNetworks(int subscriptionId, Map<Integer, List<NetworkInfo>> subIdToNetworks) {
        List<NetworkInfo> networks = subIdToNetworks.get(subscriptionId);
        if (networks != null && !networks.isEmpty()) {
            System.out.println("    Network Connections:");
            for (NetworkInfo networkInfo : networks) {
                displayNetworkDetails(networkInfo);
            }
        } else {
            System.out.println("    Network Connections: None active");
        }
    }
    
    private static void displayNetworkDetails(NetworkInfo networkInfo) {
        String[] typeParts = networkInfo.type.split(" \\[");
        String networkType = typeParts[0];
        String transport = typeParts.length > 1 ? typeParts[1].replace("]", "") : "";
        
        System.out.printf("      • %s: %s%n", networkType, networkInfo.interface_);
        
        if (!transport.isEmpty()) {
            System.out.printf("        - Transport: %s%n", transport);
        }
        
        if (networkInfo.ipAddresses != null && !networkInfo.ipAddresses.isEmpty()) {
            for (String ipAddress : networkInfo.ipAddresses) {
                System.out.printf("        - %s%n", ipAddress);
            }
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
    
    private static boolean isSubscriptionActive(int subscriptionId) {
        try {
            IInterface subscriptionService = ServiceManager.getService(SUBSCRIPTION_SERVICE, SUBSCRIPTION_INTERFACE);
            if (subscriptionService == null) {
                return false;
            }
            
            return checkSubscriptionActiveStatus(subscriptionService, subscriptionId);
        } catch (Exception e) {
            return true; 
        }
    }
    
    private static boolean checkSubscriptionActiveStatus(IInterface subscriptionService, int subscriptionId) {
        FakeContext context = FakeContext.get();
        
        try {
            Method getActiveSubscriptionInfoMethod = subscriptionService.getClass().getMethod(
                "getActiveSubscriptionInfo", int.class, String.class, String.class);
            Object result = getActiveSubscriptionInfoMethod.invoke(subscriptionService, subscriptionId, 
                context.getOpPackageName(), null);
            return result != null;
        } catch (NoSuchMethodException e) {
            return checkSubscriptionActiveStatusFallback(subscriptionService, subscriptionId);
        } catch (Exception e) {
            return true;
        }
    }
    
    private static boolean checkSubscriptionActiveStatusFallback(IInterface subscriptionService, int subscriptionId) {
        try {
            Method getActiveSubscriptionInfoMethod = subscriptionService.getClass().getMethod(
                "getActiveSubscriptionInfo", int.class);
            Object result = getActiveSubscriptionInfoMethod.invoke(subscriptionService, subscriptionId);
            return result != null;
        } catch (Exception e) {
            return true;
        }
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

    private static String getPhoneNumberForSubscription(TelephonyManager telephonyManager, int subId) {
        // Method 1: Try direct SIM card number
        String phoneNumber = telephonyManager.getLine1NumberForSubscriber(subId, CALLING_PACKAGE);
        if (isValidPhoneNumber(phoneNumber)) {
            return normalizePhoneNumber(phoneNumber);
        }
        
        // Method 2: Try SubscriptionManager phone number
        phoneNumber = telephonyManager.getPhoneNumberFromSubscriptionManager(subId);
        if (isValidPhoneNumber(phoneNumber)) {
            return normalizePhoneNumber(phoneNumber);
        }
        
        // Method 3: Try subscription property "number"
        phoneNumber = telephonyManager.getSubscriptionProperty(subId, "number");
        if (isValidPhoneNumber(phoneNumber)) {
            return normalizePhoneNumber(phoneNumber);
        }
        
        // Method 4: Try subscription property "phone_number"
        phoneNumber = telephonyManager.getSubscriptionProperty(subId, "phone_number");
        if (isValidPhoneNumber(phoneNumber)) {
            return normalizePhoneNumber(phoneNumber);
        }
        
        return null;
    }
    
    private static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return phoneNumber;
        }
        
        // Remove any whitespace and special characters except + and digits
        String cleaned = phoneNumber.replaceAll("[^+\\d]", "");
        
        // If it already starts with +, return as is
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        
        // For US/Canada numbers, add +1 if it's a 10 or 11 digit number
        if (cleaned.length() == 10) {
            return "+1" + cleaned;
        } else if (cleaned.length() == 11 && cleaned.startsWith("1")) {
            return "+" + cleaned;
        }
        
        // For other formats, try to detect country code
        // This is a basic implementation - could be enhanced
        return "+" + cleaned;
    }
    
    private static boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && 
               !phoneNumber.isEmpty() && 
               !phoneNumber.equals("???????") &&
               !phoneNumber.equals("") &&
               !phoneNumber.equals("null");
    }




    private static void printTelephonyStates() {
        IInterface telephony = ServiceManager.getService(TELEPHONY_SERVICE, TELEPHONY_INTERFACE);
        if (telephony == null) {
            Ln.e("ITelephony unavailable");
            return;
        }

        int activeSubscriptionId = getActiveSubscriptionId();
        System.out.println("\n" + StatusEmojis.TELEPHONY + " Telephony Status:\n");
        
        displayTelephonyCapabilities(telephony, activeSubscriptionId);
    }
    
    private static void displayTelephonyCapabilities(IInterface telephony, int subscriptionId) {
        Map<String, Object[]> capabilityMethods = createTelephonyCapabilityMethods(subscriptionId);
        
        for (Map.Entry<String, Object[]> methodEntry : capabilityMethods.entrySet()) {
            displayTelephonyCapability(telephony, methodEntry.getKey(), methodEntry.getValue());
        }
        
        // Display satellite capabilities
        displaySatelliteCapabilities();
    }
    
    private static Map<String, Object[]> createTelephonyCapabilityMethods(int subscriptionId) {
        Map<String, Object[]> methods = new LinkedHashMap<>();
        methods.put("isConcurrentVoiceAndDataAllowed", new Object[]{subscriptionId});
        methods.put("isDataConnectivityPossible", new Object[]{subscriptionId});
        methods.put("isDataEnabled", new Object[]{subscriptionId});
        methods.put("isHearingAidCompatibilitySupported", new Object[]{});
        methods.put("isIdle", new Object[]{CALLING_PACKAGE});
        methods.put("isIdleForSubscriber", new Object[]{subscriptionId, CALLING_PACKAGE});
        methods.put("isImsRegistered", new Object[]{subscriptionId});
        methods.put("isOffhook", new Object[]{CALLING_PACKAGE});
        methods.put("isOffhookForSubscriber", new Object[]{subscriptionId, CALLING_PACKAGE});
        methods.put("isRadioOn", new Object[]{CALLING_PACKAGE});
        methods.put("isRadioOnForSubscriber", new Object[]{subscriptionId, CALLING_PACKAGE});
        methods.put("isRinging", new Object[]{CALLING_PACKAGE});
        methods.put("isRingingForSubscriber", new Object[]{subscriptionId, CALLING_PACKAGE});
        methods.put("isTtyModeSupported", new Object[]{});
        methods.put("isUserDataEnabled", new Object[]{subscriptionId});
        methods.put("isVideoCallingEnabled", new Object[]{CALLING_PACKAGE});
        methods.put("isVideoTelephonyAvailable", new Object[]{subscriptionId});
        methods.put("isVolteAvailable", new Object[]{subscriptionId});
        methods.put("isWifiCallingAvailable", new Object[]{subscriptionId});
        methods.put("isWorldPhone", new Object[]{});
        return methods;
    }

    private static void displayTelephonyCapability(IInterface service, String methodName, Object... arguments) {
        try {
            Class<?>[] parameterTypes = Arrays.stream(arguments)
                    .map(arg -> arg instanceof Integer ? int.class : String.class)
                    .toArray(Class[]::new);
            Method method = service.getClass().getMethod(methodName, parameterTypes);
            boolean result = (boolean) method.invoke(service, arguments);
            System.out.printf("  - %s: %s%n", methodName, result ? StatusEmojis.YES : StatusEmojis.NO);
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device, skip silently
        } catch (Exception e) {
            Ln.e("Reflection error: " + methodName, e);
            System.out.printf("  - %s: %s%n", methodName, StatusEmojis.ERROR);
        }
    }

    private static void displaySatelliteCapabilities() {
        System.out.println("\n  Satellite Status:");
        
        try {
            TelephonyManager telephonyManager = ServiceManager.getTelephonyManager();
            if (telephonyManager == null) {
                System.out.println("    - Satellite status: " + StatusEmojis.ERROR + " (TelephonyManager unavailable)");
                return;
            }
            
            // Display basic satellite status
            Boolean isSupported = telephonyManager.requestIsSatelliteSupported();
            Boolean isEnabled = telephonyManager.requestIsSatelliteEnabled();
            Boolean isProvisioned = telephonyManager.requestIsSatelliteProvisioned();
            Boolean isDemoMode = telephonyManager.requestIsDemoModeEnabled();
            Integer nbIotSubscriptionId = telephonyManager.requestSelectedNbIotSatelliteSubscriptionId();
            
            System.out.printf("    - Satellite supported: %s%n", 
                isSupported != null ? (isSupported ? StatusEmojis.YES : StatusEmojis.NO) : StatusEmojis.ERROR + " (Unknown)");
            System.out.printf("    - Satellite enabled: %s%n", 
                isEnabled != null ? (isEnabled ? StatusEmojis.YES : StatusEmojis.NO) : StatusEmojis.ERROR + " (Unknown)");
            System.out.printf("    - Satellite provisioned: %s%n", 
                isProvisioned != null ? (isProvisioned ? StatusEmojis.YES : StatusEmojis.NO) : StatusEmojis.ERROR + " (Unknown)");
            System.out.printf("    - Demo mode enabled: %s%n", 
                isDemoMode != null ? (isDemoMode ? StatusEmojis.YES : StatusEmojis.NO) : StatusEmojis.ERROR + " (Unknown)");
            System.out.printf("    - NB-IoT satellite subscription ID: %s%n", 
                nbIotSubscriptionId != null ? nbIotSubscriptionId : StatusEmojis.ERROR + " (Unknown)");
            
            // Display detailed capabilities if available
            Bundle satelliteCapabilities = telephonyManager.requestSatelliteCapabilities();
            if (satelliteCapabilities != null && !satelliteCapabilities.isEmpty()) {
                System.out.println("\n  Satellite Capabilities:");
                
                // Common satellite capability keys based on Android documentation
                String[] capabilityKeys = {
                    "satellite_supported",
                    "satellite_pointing_ui_supported", 
                    "satellite_emergency_mode_supported",
                    "satellite_demo_mode_supported",
                    "max_bytes_per_out_going_datagram",
                    "antenna_position_keys",
                    "supported_radio_technologies"
                };
                
                boolean hasCapabilities = false;
                for (String key : capabilityKeys) {
                    if (satelliteCapabilities.containsKey(key)) {
                        hasCapabilities = true;
                        Object value = satelliteCapabilities.get(key);
                        if (value instanceof Boolean) {
                            System.out.printf("    - %s: %s%n", key, (Boolean) value ? StatusEmojis.YES : StatusEmojis.NO);
                        } else {
                            System.out.printf("    - %s: %s%n", key, value);
                        }
                    }
                }
                
                // If no known keys were found, display all keys in the bundle
                if (!hasCapabilities) {
                    for (String key : satelliteCapabilities.keySet()) {
                        Object value = satelliteCapabilities.get(key);
                        if (value instanceof Boolean) {
                            System.out.printf("    - %s: %s%n", key, (Boolean) value ? StatusEmojis.YES : StatusEmojis.NO);
                        } else {
                            System.out.printf("    - %s: %s%n", key, value);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Ln.e("Error retrieving satellite information", e);
            System.out.println("    - Satellite status: " + StatusEmojis.ERROR + " (Error occurred)");
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
            IInterface subscriptionService = ServiceManager.getService(SUBSCRIPTION_SERVICE, SUBSCRIPTION_INTERFACE);
            Method getDefaultSubIdMethod = subscriptionService.getClass().getMethod("getDefaultSubId");
            return (int) getDefaultSubIdMethod.invoke(subscriptionService);
        } catch (Exception e) {
            Ln.e("Active subscription ID error", e);
            return -1;
        }
    }

    private static void activateNewProfile() {
        String activationCode = promptForActivationCode();
        if (activationCode == null) {
            return;
        }
        
        try {
            EuiccOperationResult result = downloadAndActivateProfile(activationCode);
            handleProfileActivationResult(result, "new profile");
        } catch (Exception e) {
            Ln.e("Error activating new profile", e);
        }
    }
    
    private static void enableSatelliteDemoMode() {
        System.out.println("\n" + StatusEmojis.SATELLITE + " Satellite Demo Mode Configuration\n");
        
        // First check current satellite status
        TelephonyManager telephonyManager = ServiceManager.getTelephonyManager();
        if (telephonyManager == null) {
            System.out.println(StatusEmojis.ERROR + " TelephonyManager unavailable");
            return;
        }
        
        // Display current status
        Boolean isSupported = telephonyManager.requestIsSatelliteSupported();
        Boolean isEnabled = telephonyManager.requestIsSatelliteEnabled();
        Boolean isDemoMode = telephonyManager.requestIsDemoModeEnabled();
        
        System.out.println("Current Status:");
        System.out.printf("  - Satellite supported: %s%n", 
            isSupported != null ? (isSupported ? StatusEmojis.YES : StatusEmojis.NO) : "Unknown");
        System.out.printf("  - Satellite enabled: %s%n", 
            isEnabled != null ? (isEnabled ? StatusEmojis.YES : StatusEmojis.NO) : "Unknown");
        System.out.printf("  - Demo mode enabled: %s%n", 
            isDemoMode != null ? (isDemoMode ? StatusEmojis.YES : StatusEmojis.NO) : "Unknown");
        
        if (isSupported != null && !isSupported) {
            System.out.println("\n" + StatusEmojis.ERROR + " Satellite is not supported on this device");
            return;
        }
        
        // Prompt user for action
        System.out.print("\nEnable satellite demo mode? (y/n): ");
        String choice = scanner.nextLine().trim().toLowerCase();
        
        if (!choice.equals("y")) {
            System.out.println("Operation cancelled.");
            return;
        }
        
        System.out.println("\nEnabling satellite demo mode...");
        
        try {
            // Enable satellite with demo mode
            int result = telephonyManager.requestSatelliteEnabled(true, true, false);
            
            switch (result) {
                case 0:
                    System.out.println(StatusEmojis.YES + " Satellite demo mode enabled successfully!");
                    break;
                case 1:
                    System.out.println(StatusEmojis.ERROR + " Request in progress, please wait...");
                    break;
                case 2:
                    System.out.println(StatusEmojis.ERROR + " Modem error occurred");
                    break;
                case 3:
                    System.out.println(StatusEmojis.ERROR + " Invalid telephony state");
                    break;
                case 4:
                    System.out.println(StatusEmojis.ERROR + " Invalid modem state");
                    break;
                case 5:
                    System.out.println(StatusEmojis.ERROR + " Request failed");
                    break;
                case 6:
                    System.out.println(StatusEmojis.ERROR + " Radio not available");
                    break;
                case 7:
                    System.out.println(StatusEmojis.ERROR + " Request not supported");
                    break;
                case 8:
                    System.out.println(StatusEmojis.ERROR + " Service provision in progress");
                    break;
                case 9:
                    System.out.println(StatusEmojis.ERROR + " Service not provisioned");
                    break;
                case 10:
                    System.out.println(StatusEmojis.ERROR + " Network error");
                    break;
                case 11:
                    System.out.println(StatusEmojis.ERROR + " No resources available");
                    break;
                case 12:
                    System.out.println(StatusEmojis.ERROR + " Request cancelled");
                    break;
                case 13:
                    System.out.println(StatusEmojis.ERROR + " Access barred");
                    break;
                case 14:
                    System.out.println(StatusEmojis.ERROR + " Network timeout");
                    break;
                case 15:
                    System.out.println(StatusEmojis.ERROR + " Not reachable");
                    break;
                case 16:
                    System.out.println(StatusEmojis.ERROR + " Not allowed");
                    break;
                case 17:
                    System.out.println(StatusEmojis.ERROR + " Aborted");
                    break;
                default:
                    System.out.println(StatusEmojis.ERROR + " Unknown error code: " + result);
                    break;
            }
            
            // Check status again after operation
            if (result == 0) {
                Thread.sleep(1000); // Give it a moment to apply
                Boolean newDemoMode = telephonyManager.requestIsDemoModeEnabled();
                Boolean newEnabled = telephonyManager.requestIsSatelliteEnabled();
                
                System.out.println("\nNew Status:");
                System.out.printf("  - Satellite enabled: %s%n", 
                    newEnabled != null ? (newEnabled ? StatusEmojis.YES : StatusEmojis.NO) : "Unknown");
                System.out.printf("  - Demo mode enabled: %s%n", 
                    newDemoMode != null ? (newDemoMode ? StatusEmojis.YES : StatusEmojis.NO) : "Unknown");
            }
            
        } catch (Exception e) {
            Ln.e("Error enabling satellite demo mode", e);
            System.out.println(StatusEmojis.ERROR + " Failed to enable satellite demo mode");
        }
    }
    
    private static String promptForActivationCode() {
        System.out.print("Enter eSIM activation code (LPA format): ");
        String activationCode = scanner.nextLine().trim();
        
        if (activationCode.isEmpty()) {
            Ln.e("Activation code was empty!");
            return null;
        }
        
        return activationCode;
    }
    
    private static class EuiccOperationResult {
        final boolean success;
        final String errorMessage;
        
        EuiccOperationResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        static EuiccOperationResult success() {
            return new EuiccOperationResult(true, null);
        }
        
        static EuiccOperationResult failure(String errorMessage) {
            return new EuiccOperationResult(false, errorMessage);
        }
    }
    
    private static EuiccOperationResult downloadAndActivateProfile(String activationCode) {
        try {
            IInterface euiccController = ServiceManager.getService(EUICC_SERVICE, EUICC_INTERFACE);
            if (euiccController == null) {
                return EuiccOperationResult.failure("Could not get IEuiccController service");
            }
            
            DownloadableSubscription subscription = DownloadableSubscription.forActivationCode(activationCode);
            
            Method downloadSubscriptionMethod = euiccController.getClass().getMethod(
                "downloadSubscription",
                DownloadableSubscription.class, boolean.class,
                String.class, android.app.PendingIntent.class
            );
            
            Ln.i("Activating new eSIM profile...");
            downloadSubscriptionMethod.invoke(euiccController, subscription, true, CALLING_PACKAGE, null);
            
            return EuiccOperationResult.success();
        } catch (Exception e) {
            return EuiccOperationResult.failure(e.getMessage());
        }
    }
    
    private static void handleProfileActivationResult(EuiccOperationResult result, String profileType) {
        if (result.success) {
            Ln.i("Requested eSIM profile activation (" + profileType + "). Check device.");
        } else {
            Ln.e("Failed to activate " + profileType + ": " + result.errorMessage);
        }
    }

    private static void activateExistingProfile() {
        try {
            List<SubscriptionInfo> availableProfiles = getAvailableProfiles();
            if (availableProfiles == null) {
                return;
            }
            
            displayAvailableProfiles(availableProfiles);
            
            Integer selectedProfileId = promptForProfileSelection(availableProfiles);
            if (selectedProfileId == null) {
                return;
            }
            
            EuiccOperationResult result = switchToExistingProfile(selectedProfileId);
            handleProfileActivationResult(result, "existing profile");

        } catch (NumberFormatException e) {
            Ln.e("Invalid number format", e);
        } catch (Exception e) {
            Ln.e("Error activating existing profile", e);
        }
    }
    
    private static List<SubscriptionInfo> getAvailableProfiles() {
        SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
        List<SubscriptionInfo> subscriptions = subscriptionService.getAvailableSubscriptionInfoList();
        
        if (subscriptions == null || subscriptions.isEmpty()) {
            System.out.println("No existing eSIM profiles found.");
            return null;
        }
        
        return subscriptions;
    }
    
    private static void displayAvailableProfiles(List<SubscriptionInfo> subscriptions) {
        int activeSubscriptionId = getActiveSubscriptionId();
        
        System.out.println("\n📑 Existing eSIM profiles:");
        
        for (int index = 0; index < subscriptions.size(); index++) {
            SubscriptionInfo subscription = subscriptions.get(index);
            boolean isActive = subscription.getSubscriptionId() == activeSubscriptionId;
            
            System.out.printf("%d. %s (ICCID: %s)%s%n",
                    index + 1,
                    subscription.getDisplayName(),
                    subscription.getIccId(),
                    isActive ? " [ACTIVE " + StatusEmojis.ACTIVE + "]" : ""
            );
        }
    }
    
    private static Integer promptForProfileSelection(List<SubscriptionInfo> subscriptions) {
        System.out.print("\nSelect profile number to activate: ");
        int choice = Integer.parseInt(scanner.nextLine().trim());
        
        if (choice < 1 || choice > subscriptions.size()) {
            System.out.println("Invalid selection.");
            return null;
        }
        
        return subscriptions.get(choice - 1).getSubscriptionId();
    }
    
    private static EuiccOperationResult switchToExistingProfile(int subscriptionId) {
        try {
            IInterface euiccController = ServiceManager.getService(EUICC_SERVICE, EUICC_INTERFACE);
            if (euiccController == null) {
                return EuiccOperationResult.failure("Could not get IEuiccController service");
            }
            
            Method switchToSubscriptionMethod = euiccController.getClass().getMethod(
                "switchToSubscription",
                int.class, String.class, android.app.PendingIntent.class
            );
            
            Ln.i("Activating existing eSIM profile...");
            switchToSubscriptionMethod.invoke(euiccController, subscriptionId, CALLING_PACKAGE, null);
            
            return EuiccOperationResult.success();
        } catch (Exception e) {
            return EuiccOperationResult.failure(e.getMessage());
        }
    }
}

