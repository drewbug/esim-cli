package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SubscriptionService;
import com.genymobile.scrcpy.wrappers.ConnectivityManager;
import com.genymobile.scrcpy.wrappers.TelephonyManager;
import com.genymobile.scrcpy.Ln;
import com.genymobile.scrcpy.FakeContext;

import android.os.Bundle;
import android.os.IInterface;
import android.os.Parcel;
import android.os.PersistableBundle;
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
    private static final String CARRIER_CONFIG_SERVICE = "carrier_config";
    private static final String CARRIER_CONFIG_INTERFACE = "com.android.internal.telephony.ICarrierConfigLoader";
    private static final String STATUS_SEPARATOR = "=" + "=".repeat(59);
    private static final String UNAVAILABLE_PHONE = "Not available";
    private static final String UNKNOWN_PLACEHOLDER = "???????"; 
    
    private static final Scanner scanner = new Scanner(System.in);
    
    private static final class MenuOptions {
        static final String ACTIVATE_EXISTING = "1";
        static final String DOWNLOAD_NEW = "2";
        static final String SATELLITE_DEMO_MODE = "3";
        static final String DUMP_TELEPHONY_METHODS = "4";
        static final String DUMP_CARRIER_CONFIG = "5";
        static final String SELECT_NETWORK_OPERATOR = "6";
        static final String TEST_RIL_RADIO_SERVICE = "7";
        static final String EXIT = "8";
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
            printCarrierConfigurations();
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
        System.out.println("4. Dump ITelephony interface methods");
        System.out.println("5. Dump carrier configuration (all keys)");
        System.out.println("6. Select network operator");
        System.out.println("7. Test RIL Radio Service Proxy");
        System.out.println("8. Exit");
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
            case MenuOptions.DUMP_TELEPHONY_METHODS:
                dumpTelephonyMethods();
                return false;
            case MenuOptions.DUMP_CARRIER_CONFIG:
                dumpCarrierConfiguration();
                return false;
            case MenuOptions.SELECT_NETWORK_OPERATOR:
                selectNetworkOperator();
                return false;
            case MenuOptions.TEST_RIL_RADIO_SERVICE:
                testRilRadioServiceProxy();
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
    
    private static void printCarrierConfigurations() {
        try {
            IInterface carrierConfigLoader = ServiceManager.getService(CARRIER_CONFIG_SERVICE, CARRIER_CONFIG_INTERFACE);
            if (carrierConfigLoader == null) {
                System.out.println("\n📱 Carrier Configuration: Service unavailable");
                return;
            }
            
            SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
            if (subscriptionService == null) {
                System.out.println("\n📱 Carrier Configuration: Subscription service unavailable");
                return;
            }
            
            List<SubscriptionInfo> availableSubscriptions = subscriptionService.getAvailableSubscriptionInfoList();
            if (availableSubscriptions == null || availableSubscriptions.isEmpty()) {
                System.out.println("\n📱 Carrier Configuration: No subscriptions found");
                return;
            }
            
            System.out.println("\n📱 Carrier Configuration (per subscription):");
            
            for (SubscriptionInfo subscription : availableSubscriptions) {
                displayCarrierConfigForSubscription(carrierConfigLoader, subscription);
            }
            
        } catch (Exception e) {
            Ln.e("Error printing carrier configurations", e);
            System.out.println("\n📱 Carrier Configuration: Error accessing service");
        }
    }
    
    private static void displayCarrierConfigForSubscription(IInterface carrierConfigLoader, SubscriptionInfo subscription) {
        try {
            int subId = subscription.getSubscriptionId();
            String displayName = subscription.getDisplayName().toString();
            
            System.out.printf("\n  %s (SubId: %d):%n", displayName, subId);
            
            // Try different method signatures for getConfigForSubId
            Object configBundle = null;
            
            // First try: getConfigForSubId(int subId, String callingPackage)
            try {
                Method getConfigMethod = carrierConfigLoader.getClass().getMethod("getConfigForSubId", int.class, String.class);
                configBundle = getConfigMethod.invoke(carrierConfigLoader, subId, CALLING_PACKAGE);
            } catch (NoSuchMethodException e) {
                // Second try: getConfigForSubId(int subId)
                try {
                    Method getConfigMethod = carrierConfigLoader.getClass().getMethod("getConfigForSubId", int.class);
                    configBundle = getConfigMethod.invoke(carrierConfigLoader, subId);
                } catch (NoSuchMethodException e2) {
                    System.out.println("    - getConfigForSubId method not found");
                    return;
                }
            }
            
            if (configBundle == null) {
                System.out.println("    - No carrier configuration available (null returned)");
            } else if (configBundle instanceof PersistableBundle) {
                PersistableBundle bundle = (PersistableBundle) configBundle;
                if (bundle.isEmpty()) {
                    System.out.println("    - No carrier configuration available (empty bundle)");
                } else {
                    System.out.println("    - Configuration keys found: " + bundle.size());
                    
                    // Display first 10 key-value pairs as examples
                    int count = 0;
                    for (String key : bundle.keySet()) {
                        if (count >= 10) {
                            System.out.println("    - ... and " + (bundle.size() - 10) + " more configuration entries");
                            break;
                        }
                        Object value = bundle.get(key);
                        System.out.printf("    - %s = %s%n", key, formatConfigValue(value));
                        count++;
                    }
                }
            } else if (configBundle instanceof Bundle) {
                Bundle bundle = (Bundle) configBundle;
                if (bundle.isEmpty()) {
                    System.out.println("    - No carrier configuration available (empty bundle)");
                } else {
                    System.out.println("    - Configuration keys found: " + bundle.size());
                    
                    // Display first 10 key-value pairs as examples
                    int count = 0;
                    for (String key : bundle.keySet()) {
                        if (count >= 10) {
                            System.out.println("    - ... and " + (bundle.size() - 10) + " more configuration entries");
                            break;
                        }
                        Object value = bundle.get(key);
                        System.out.printf("    - %s = %s%n", key, formatConfigValue(value));
                        count++;
                    }
                }
            } else {
                System.out.println("    - Unable to retrieve configuration (unexpected type: " + configBundle.getClass().getName() + ")");
            }
            
        } catch (Exception e) {
            System.out.println("    - Error retrieving configuration: " + e.getMessage());
            Ln.e("Error getting carrier config for subscription " + subscription.getSubscriptionId(), e);
        }
    }
    
    private static String formatConfigValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof Integer || value instanceof Long) {
            return value.toString();
        } else if (value instanceof String) {
            String str = (String) value;
            if (str.length() > 100) {
                return str.substring(0, 97) + "...";
            }
            return str;
        } else if (value.getClass().isArray()) {
            // Handle different array types
            if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            } else if (value instanceof String[]) {
                return Arrays.toString((String[]) value);
            } else if (value instanceof boolean[]) {
                return Arrays.toString((boolean[]) value);
            } else if (value instanceof long[]) {
                return Arrays.toString((long[]) value);
            } else {
                return "[Array of " + value.getClass().getComponentType().getSimpleName() + "]";
            }
        } else {
            return value.getClass().getSimpleName() + ": " + value.toString();
        }
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
            
            // Display basic satellite status - show all keys from each bundle
            Bundle supportedBundle = telephonyManager.invokeSatelliteMethodWithResultReceiver("requestIsSatelliteSupported");
            if (supportedBundle != null) {
                displayBundleContents("Satellite Supported Result", supportedBundle, "    ");
            }
            
            Bundle enabledBundle = telephonyManager.invokeSatelliteMethodWithResultReceiver("requestIsSatelliteEnabled");
            if (enabledBundle != null) {
                displayBundleContents("Satellite Enabled Result", enabledBundle, "    ");
            }
            
            Bundle provisionedBundle = telephonyManager.invokeSatelliteMethodWithResultReceiver("requestIsSatelliteProvisioned");
            if (provisionedBundle != null) {
                displayBundleContents("Satellite Provisioned Result", provisionedBundle, "    ");
            }
            
            Bundle demoModeBundle = telephonyManager.invokeSatelliteMethodWithResultReceiver("requestIsDemoModeEnabled");
            if (demoModeBundle != null) {
                displayBundleContents("Demo Mode Result", demoModeBundle, "    ");
            }
            
            Bundle nbIotBundle = telephonyManager.invokeSatelliteMethodWithResultReceiver("requestSelectedNbIotSatelliteSubscriptionId");
            if (nbIotBundle != null) {
                displayBundleContents("NB-IoT Subscription Result", nbIotBundle, "    ");
            }
            
            // Display detailed capabilities if available
            Bundle satelliteCapabilities = telephonyManager.requestSatelliteCapabilities();
            if (satelliteCapabilities != null && !satelliteCapabilities.isEmpty()) {
                System.out.println("\n  Satellite Capabilities:");
                displayBundleContents(null, satelliteCapabilities, "    ");
            }
            
            // Display communication allowed status for all subscriptions
            displayCommunicationAllowedStatus(telephonyManager);
            
        } catch (Exception e) {
            Ln.e("Error retrieving satellite information", e);
            System.out.println("    - Satellite status: " + StatusEmojis.ERROR + " (Error occurred)");
        }
    }
    
    private static void displayCommunicationAllowedStatus(TelephonyManager telephonyManager) {
        System.out.println("\n  Communication Allowed Status (per subscription):");
        
        try {
            // Get all available subscriptions
            SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
            if (subscriptionService == null) {
                System.out.println("    - " + StatusEmojis.ERROR + " (SubscriptionService unavailable)");
                return;
            }
            
            List<SubscriptionInfo> availableSubscriptions = subscriptionService.getAvailableSubscriptionInfoList();
            if (availableSubscriptions == null || availableSubscriptions.isEmpty()) {
                System.out.println("    - No subscriptions found");
                return;
            }
            
            // Check communication allowed for each subscription
            for (SubscriptionInfo subscription : availableSubscriptions) {
                int subId = subscription.getSubscriptionId();
                String displayName = subscription.getDisplayName().toString();
                
                System.out.printf("    - %s (SubId: %d):%n", displayName, subId);
                
                Bundle result = telephonyManager.invokeCommunicationAllowedMethodWithResultReceiver(
                    "requestIsCommunicationAllowedForCurrentLocation", subId);
                
                if (result != null) {
                    displayBundleContents(null, result, "      ");
                } else {
                    System.out.println("      " + StatusEmojis.ERROR + " (No response)");
                }
            }
        } catch (Exception e) {
            Ln.e("Error checking communication allowed status", e);
            System.out.println("    - " + StatusEmojis.ERROR + " (Error occurred)");
        }
    }
    
    private static void displayBundleContents(String title, Bundle bundle, String indent) {
        if (title != null) {
            System.out.println(indent + title + ":");
            indent = indent + "  ";
        }
        
        if (bundle == null || bundle.isEmpty()) {
            System.out.println(indent + "- (Empty bundle)");
            return;
        }
        
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof Boolean) {
                System.out.printf("%s- %s: %s%n", indent, key, (Boolean) value ? StatusEmojis.YES : StatusEmojis.NO);
            } else {
                System.out.printf("%s- %s: %s%n", indent, key, value);
            }
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
            
            // Special handling for network operator methods
            if (("getNetworkOperator".equals(method) || "getNetworkOperatorName".equals(method)) && args.length == 0) {
                // Try with calling package parameter
                try {
                    Method m = srv.getClass().getMethod(method, String.class);
                    return (String) m.invoke(srv, CALLING_PACKAGE);
                } catch (NoSuchMethodException e) {
                    // Try without parameters
                    try {
                        Method m = srv.getClass().getMethod(method);
                        return (String) m.invoke(srv);
                    } catch (NoSuchMethodException e2) {
                        return null;
                    }
                }
            }
            
            // Special handling for getNetworkOperatorForPhone methods
            if (("getNetworkOperatorForPhone".equals(method) || "getNetworkOperatorNameForPhone".equals(method)) && args.length == 1) {
                // Try with phoneId parameter only
                try {
                    Method m = srv.getClass().getMethod(method, int.class);
                    return (String) m.invoke(srv, args[0]);
                } catch (NoSuchMethodException e) {
                    // Try with phoneId and calling package
                    try {
                        Method m = srv.getClass().getMethod(method, int.class, String.class);
                        return (String) m.invoke(srv, args[0], CALLING_PACKAGE);
                    } catch (NoSuchMethodException e2) {
                        // Try with phoneId, calling package, and feature id
                        try {
                            Method m = srv.getClass().getMethod(method, int.class, String.class, String.class);
                            return (String) m.invoke(srv, args[0], CALLING_PACKAGE, null);
                        } catch (NoSuchMethodException e3) {
                            return null;
                        }
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
    
    private static void dumpTelephonyMethods() {
        System.out.println("\n" + StatusEmojis.TELEPHONY + " ITelephony Interface Methods Dump\n");
        
        try {
            IInterface telephony = ServiceManager.getService(TELEPHONY_SERVICE, TELEPHONY_INTERFACE);
            if (telephony == null) {
                System.out.println(StatusEmojis.ERROR + " Could not get ITelephony service");
                return;
            }
            
            // Get all methods from the ITelephony interface
            Method[] methods = telephony.getClass().getMethods();
            
            System.out.println("Total methods found: " + methods.length);
            System.out.println(STATUS_SEPARATOR);
            
            // Sort methods alphabetically for easier reading
            java.util.Arrays.sort(methods, (m1, m2) -> m1.getName().compareTo(m2.getName()));
            
            for (Method method : methods) {
                // Skip methods from Object class
                if (method.getDeclaringClass().equals(Object.class)) {
                    continue;
                }
                
                // Build method signature
                StringBuilder signature = new StringBuilder();
                signature.append(method.getName()).append("(");
                
                Class<?>[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0) signature.append(", ");
                    signature.append(paramTypes[i].getSimpleName());
                }
                signature.append(")");
                
                // Add return type
                signature.append(" → ").append(method.getReturnType().getSimpleName());
                
                System.out.println("  " + signature.toString());
            }
            
            System.out.println(STATUS_SEPARATOR);
            
            // Also print some additional info
            System.out.println("\nAdditional Information:");
            System.out.println("  - Service Name: " + TELEPHONY_SERVICE);
            System.out.println("  - Interface: " + TELEPHONY_INTERFACE);
            System.out.println("  - Implementation Class: " + telephony.getClass().getName());
            System.out.println("  - Package: " + telephony.getClass().getPackage().getName());
            
            // Count methods by prefix
            Map<String, Integer> prefixCounts = new HashMap<>();
            for (Method method : methods) {
                if (method.getDeclaringClass().equals(Object.class)) {
                    continue;
                }
                String name = method.getName();
                String prefix = "";
                if (name.startsWith("get")) prefix = "get";
                else if (name.startsWith("set")) prefix = "set";
                else if (name.startsWith("is")) prefix = "is";
                else if (name.startsWith("has")) prefix = "has";
                else if (name.startsWith("enable")) prefix = "enable";
                else if (name.startsWith("disable")) prefix = "disable";
                else if (name.startsWith("request")) prefix = "request";
                else prefix = "other";
                
                prefixCounts.put(prefix, prefixCounts.getOrDefault(prefix, 0) + 1);
            }
            
            System.out.println("\nMethod Count by Prefix:");
            for (Map.Entry<String, Integer> entry : prefixCounts.entrySet()) {
                System.out.printf("  - %s*: %d methods%n", entry.getKey(), entry.getValue());
            }
            
        } catch (Exception e) {
            Ln.e("Error dumping ITelephony methods", e);
            System.out.println(StatusEmojis.ERROR + " Failed to dump ITelephony methods");
        }
    }
    
    private static void dumpCarrierConfiguration() {
        System.out.println("\n📱 Carrier Configuration Full Dump\n");
        
        try {
            IInterface carrierConfigLoader = ServiceManager.getService(CARRIER_CONFIG_SERVICE, CARRIER_CONFIG_INTERFACE);
            if (carrierConfigLoader == null) {
                System.out.println(StatusEmojis.ERROR + " Could not get ICarrierConfigLoader service");
                return;
            }
            
            // First, let's dump the available methods
            System.out.println("ICarrierConfigLoader Methods:");
            System.out.println(STATUS_SEPARATOR);
            Method[] methods = carrierConfigLoader.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getDeclaringClass().equals(Object.class)) {
                    StringBuilder sig = new StringBuilder();
                    sig.append("  ").append(method.getName()).append("(");
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        if (i > 0) sig.append(", ");
                        sig.append(params[i].getSimpleName());
                    }
                    sig.append(") → ").append(method.getReturnType().getSimpleName());
                    System.out.println(sig.toString());
                }
            }
            System.out.println(STATUS_SEPARATOR);
            
            SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
            if (subscriptionService == null) {
                System.out.println(StatusEmojis.ERROR + " Subscription service unavailable");
                return;
            }
            
            List<SubscriptionInfo> availableSubscriptions = subscriptionService.getAvailableSubscriptionInfoList();
            if (availableSubscriptions == null || availableSubscriptions.isEmpty()) {
                System.out.println("No subscriptions found");
                return;
            }
            
            System.out.println("\nCarrier Configuration by Subscription:");
            
            for (SubscriptionInfo subscription : availableSubscriptions) {
                dumpFullCarrierConfigForSubscription(carrierConfigLoader, subscription);
            }
            
        } catch (Exception e) {
            Ln.e("Error dumping carrier configuration", e);
            System.out.println(StatusEmojis.ERROR + " Failed to dump carrier configuration");
        }
    }
    
    private static void dumpFullCarrierConfigForSubscription(IInterface carrierConfigLoader, SubscriptionInfo subscription) {
        try {
            int subId = subscription.getSubscriptionId();
            String displayName = subscription.getDisplayName().toString();
            
            System.out.println("\n" + STATUS_SEPARATOR);
            System.out.printf("%s (SubId: %d):%n", displayName, subId);
            System.out.println(STATUS_SEPARATOR);
            
            // Try different method signatures for getConfigForSubId
            Object configBundle = null;
            
            // First try: getConfigForSubId(int subId, String callingPackage)
            try {
                Method getConfigMethod = carrierConfigLoader.getClass().getMethod("getConfigForSubId", int.class, String.class);
                configBundle = getConfigMethod.invoke(carrierConfigLoader, subId, CALLING_PACKAGE);
            } catch (NoSuchMethodException e) {
                // Second try: getConfigForSubId(int subId)
                try {
                    Method getConfigMethod = carrierConfigLoader.getClass().getMethod("getConfigForSubId", int.class);
                    configBundle = getConfigMethod.invoke(carrierConfigLoader, subId);
                } catch (NoSuchMethodException e2) {
                    System.out.println("getConfigForSubId method not found");
                    return;
                }
            }
            
            if (configBundle == null) {
                System.out.println("No carrier configuration available (null returned)");
            } else if (configBundle instanceof PersistableBundle) {
                PersistableBundle bundle = (PersistableBundle) configBundle;
                dumpPersistableBundle(bundle);
            } else if (configBundle instanceof Bundle) {
                Bundle bundle = (Bundle) configBundle;
                dumpBundle(bundle);
            } else {
                System.out.println("Unable to retrieve configuration (unexpected type: " + configBundle.getClass().getName());
            }
            
        } catch (Exception e) {
            System.out.println("Error retrieving configuration: " + e.getMessage());
            Ln.e("Error dumping carrier config for subscription " + subscription.getSubscriptionId(), e);
        }
    }
    
    private static void dumpPersistableBundle(PersistableBundle bundle) {
        if (bundle.isEmpty()) {
            System.out.println("No carrier configuration available (empty bundle)");
        } else {
            System.out.println("Configuration entries: " + bundle.size());
            System.out.println();
            
            // Sort keys alphabetically
            List<String> sortedKeys = new ArrayList<>(bundle.keySet());
            java.util.Collections.sort(sortedKeys);
            
            for (String key : sortedKeys) {
                Object value = bundle.get(key);
                System.out.printf("  %s = %s%n", key, formatConfigValue(value));
            }
        }
    }
    
    private static void dumpBundle(Bundle bundle) {
        if (bundle.isEmpty()) {
            System.out.println("No carrier configuration available (empty bundle)");
        } else {
            System.out.println("Configuration entries: " + bundle.size());
            System.out.println();
            
            // Sort keys alphabetically
            List<String> sortedKeys = new ArrayList<>(bundle.keySet());
            java.util.Collections.sort(sortedKeys);
            
            for (String key : sortedKeys) {
                Object value = bundle.get(key);
                System.out.printf("  %s = %s%n", key, formatConfigValue(value));
            }
        }
    }
    
    private static void enableSatelliteDemoMode() {
        try {
            TelephonyManager telephonyManager = ServiceManager.getTelephonyManager();
            if (telephonyManager == null) {
                System.out.println(StatusEmojis.ERROR + " TelephonyManager unavailable");
                return;
            }

            int[] disallowedReasons = telephonyManager.getSatelliteDisallowedReasons();
            if (disallowedReasons != null && disallowedReasons.length > 0) {
                System.out.println("\n" + StatusEmojis.ERROR + " Satellite operations are currently disallowed:");
                displayDisallowedReasons(disallowedReasons);
            }

            // Enable satellite with demo mode
            int result = telephonyManager.requestSatelliteEnabled(true, false, false);
            System.out.printf("requestSatelliteEnabled result: %d\n", result);
        } catch (Exception e) {
            Ln.e("Error enabling satellite demo mode", e);
            System.out.println(StatusEmojis.ERROR + " Failed to enable satellite demo mode");
        }
    }
    
    private static void displaySatelliteDisallowedReasons(TelephonyManager telephonyManager) {
        int[] reasons = telephonyManager.getSatelliteDisallowedReasons();
        if (reasons != null && reasons.length > 0) {
            System.out.println("\n  Satellite is disallowed for the following reasons:");
            displayDisallowedReasons(reasons);
        }
    }
    
    private static void displayDisallowedReasons(int[] reasons) {
        for (int reason : reasons) {
            System.out.printf("(code: %d)%n", reason);
        }
    }
    
    private static void selectNetworkOperator() {
        System.out.println("\n📡 Network Operator Selection\n");
        
        try {
            // Get the active subscription ID for the network scan
            int subId = getActiveSubscriptionId();
            if (subId == -1) {
                System.out.println(StatusEmojis.ERROR + " No active subscription found");
                return;
            }
            
            IInterface telephony = ServiceManager.getService(TELEPHONY_SERVICE, TELEPHONY_INTERFACE);
            if (telephony == null) {
                System.out.println(StatusEmojis.ERROR + " ITelephony service unavailable");
                return;
            }
            
            // First display current network operator
            displayCurrentNetworkOperator(telephony, subId);
            
            System.out.println("\nOptions:");
            System.out.println("1. Scan for available networks");
            System.out.println("2. Set network selection mode to automatic");
            System.out.println("3. Manually select a network from scan");
            System.out.println("4. Manually connect to network by PLMN");
            System.out.println("5. Debug: List network-related methods");
            System.out.println("6. Back to main menu");
            System.out.print("\nChoose an option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    scanForNetworks(telephony, subId);
                    break;
                case "2":
                    setAutomaticNetworkSelection(telephony, subId);
                    break;
                case "3":
                    manuallySelectNetwork(telephony, subId);
                    break;
                case "4":
                    manuallyConnectByPLMN(telephony, subId);
                    break;
                case "5":
                    debugNetworkMethods(telephony);
                    break;
                case "6":
                    return;
                default:
                    System.out.println("Invalid choice");
            }
            
        } catch (Exception e) {
            Ln.e("Error in network operator selection", e);
            System.out.println(StatusEmojis.ERROR + " Failed to access network operator functions");
        }
    }
    
    private static void testRilRadioServiceProxy() {
        try {
            System.out.println("\n📻 RIL Radio Service Proxy Test\n");
            System.out.println(STATUS_SEPARATOR);
            
            // Check if RIL is available
            if (!ServiceManager.isRilAvailable()) {
                System.out.println(StatusEmojis.ERROR + " RIL class or instance not available");
                System.out.println("This may be due to:");
                System.out.println("  - Missing Android framework classes");
                System.out.println("  - Insufficient permissions");
                System.out.println("  - Device not supporting RIL access");
                return;
            }
            
            System.out.println(StatusEmojis.VALIDATED + " RIL is available");
            
            // Test getRadioServiceProxy with default HAL_SERVICE_NETWORK
            System.out.println("\nTesting getRadioServiceProxy() [default HAL_SERVICE_NETWORK]:");
            Object networkProxy = ServiceManager.getRadioServiceProxy();
            if (networkProxy != null) {
                System.out.println(StatusEmojis.VALIDATED + " Network service proxy obtained: " + networkProxy.getClass().getName());
            } else {
                System.out.println(StatusEmojis.ERROR + " Failed to get network service proxy");
            }
            
            // Test different HAL service types
            System.out.println("\nTesting different HAL service types:");
            
            // HAL_SERVICE_VOICE (0)
            Object voiceProxy = ServiceManager.getRadioServiceProxy(0);
            System.out.println("HAL_SERVICE_VOICE (0): " + 
                (voiceProxy != null ? StatusEmojis.VALIDATED + " Available" : StatusEmojis.ERROR + " Not available"));
            
            // HAL_SERVICE_DATA (1) 
            Object dataProxy = ServiceManager.getRadioServiceProxy(1);
            System.out.println("HAL_SERVICE_DATA (1): " + 
                (dataProxy != null ? StatusEmojis.VALIDATED + " Available" : StatusEmojis.ERROR + " Not available"));
            
            // HAL_SERVICE_NETWORK (2) - our default
            Object networkProxy2 = ServiceManager.getRadioServiceProxy(2);
            System.out.println("HAL_SERVICE_NETWORK (2): " + 
                (networkProxy2 != null ? StatusEmojis.VALIDATED + " Available" : StatusEmojis.ERROR + " Not available"));
            
            // HAL_SERVICE_MESSAGING (3)
            Object messagingProxy = ServiceManager.getRadioServiceProxy(3);
            System.out.println("HAL_SERVICE_MESSAGING (3): " + 
                (messagingProxy != null ? StatusEmojis.VALIDATED + " Available" : StatusEmojis.ERROR + " Not available"));
            
            System.out.println("\n" + STATUS_SEPARATOR);
            System.out.println("Test completed. Default service is HAL_SERVICE_NETWORK (2)");
            
        } catch (Exception e) {
            Ln.e("Error testing RIL Radio Service Proxy", e);
            System.out.println(StatusEmojis.ERROR + " Failed to test RIL Radio Service Proxy: " + e.getMessage());
        }
    }
    
    private static void debugNetworkMethods(IInterface telephony) {
        System.out.println("\n🔍 Network-related methods available:");
        System.out.println(STATUS_SEPARATOR);
        
        Method[] methods = telephony.getClass().getMethods();
        java.util.Arrays.sort(methods, (m1, m2) -> m1.getName().compareTo(m2.getName()));
        
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.toLowerCase().contains("network") || 
                methodName.toLowerCase().contains("operator") ||
                methodName.toLowerCase().contains("selection") ||
                methodName.toLowerCase().contains("scan") ||
                methodName.toLowerCase().contains("plmn")) {
                
                StringBuilder signature = new StringBuilder();
                signature.append(methodName).append("(");
                
                Class<?>[] paramTypes = method.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (i > 0) signature.append(", ");
                    signature.append(paramTypes[i].getSimpleName());
                }
                signature.append(")");
                
                signature.append(" → ").append(method.getReturnType().getSimpleName());
                System.out.println("  " + signature.toString());
            }
        }
        System.out.println(STATUS_SEPARATOR);
    }
    
    private static void displayCurrentNetworkOperator(IInterface telephony, int subId) {
        try {
            // Try multiple methods to get network operator info
            String operatorNumeric = null;
            String operatorName = null;
            
            // Method 1: Try with subId parameter
            operatorNumeric = invokeTelephonyStringMethod(telephony, "getNetworkOperatorForPhone", subId);
            operatorName = invokeTelephonyStringMethod(telephony, "getNetworkOperatorNameForPhone", subId);
            
            // Method 2: If that didn't work, try without subId
            if ((operatorNumeric == null || operatorNumeric.isEmpty()) && 
                (operatorName == null || operatorName.isEmpty())) {
                operatorNumeric = invokeTelephonyStringMethod(telephony, "getNetworkOperator");
                operatorName = invokeTelephonyStringMethod(telephony, "getNetworkOperatorName");
            }
            
            // Method 3: Try with phoneId instead of subId
            if ((operatorNumeric == null || operatorNumeric.isEmpty()) && 
                (operatorName == null || operatorName.isEmpty())) {
                // Get phoneId from subId
                try {
                    Method getPhoneIdMethod = telephony.getClass().getMethod("getPhoneId", int.class);
                    int phoneId = (int) getPhoneIdMethod.invoke(telephony, subId);
                    
                    operatorNumeric = invokeTelephonyStringMethod(telephony, "getNetworkOperatorForPhone", phoneId);
                    operatorName = invokeTelephonyStringMethod(telephony, "getNetworkOperatorNameForPhone", phoneId);
                } catch (Exception e) {
                    // Ignore, try next method
                }
            }
            
            // Display the results
            if (operatorName != null && !operatorName.isEmpty() && !operatorName.equals("")) {
                System.out.println("Current Network Operator: " + operatorName);
                if (operatorNumeric != null && !operatorNumeric.isEmpty()) {
                    System.out.println("Operator Code (MCC+MNC): " + operatorNumeric);
                }
            } else if (operatorNumeric != null && !operatorNumeric.isEmpty()) {
                System.out.println("Current Network Operator Code (MCC+MNC): " + operatorNumeric);
                System.out.println("Operator Name: Not available");
            } else {
                System.out.println("Current Network Operator: Not connected or unavailable");
                
                // Try to get more info about network state
                try {
                    Method getDataStateMethod = telephony.getClass().getMethod("getDataState");
                    int dataState = (int) getDataStateMethod.invoke(telephony);
                    System.out.println("Data State: " + getDataStateString(dataState));
                } catch (Exception e) {
                    // Ignore
                }
                
                try {
                    Method getServiceStateMethod = telephony.getClass().getMethod("getServiceStateForSubscriber", int.class, String.class);
                    Object serviceState = getServiceStateMethod.invoke(telephony, subId, CALLING_PACKAGE);
                    if (serviceState != null) {
                        System.out.println("Service State: " + serviceState.toString());
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            
            // Check network selection mode
            try {
                Method getNetworkSelectionModeMethod = telephony.getClass().getMethod("getNetworkSelectionMode", int.class);
                int selectionMode = (int) getNetworkSelectionModeMethod.invoke(telephony, subId);
                System.out.println("Network Selection Mode: " + (selectionMode == 0 ? "Automatic" : "Manual"));
            } catch (Exception e) {
                // Try alternative method
                try {
                    Method getNetworkSelectionModeMethod = telephony.getClass().getMethod("getNetworkSelectionModeForSubscriber", int.class);
                    int selectionMode = (int) getNetworkSelectionModeMethod.invoke(telephony, subId);
                    System.out.println("Network Selection Mode: " + (selectionMode == 0 ? "Automatic" : "Manual"));
                } catch (Exception e2) {
                    System.out.println("Network Selection Mode: Unable to determine");
                }
            }
            
        } catch (Exception e) {
            Ln.e("Error getting current network operator", e);
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    private static String getDataStateString(int state) {
        switch (state) {
            case 0: return "Disconnected";
            case 1: return "Connecting";
            case 2: return "Connected";
            case 3: return "Suspended";
            default: return "Unknown (" + state + ")";
        }
    }
    
    private static void scanForNetworks(IInterface telephony, int subId) {
        System.out.println("\nScanning for available networks... This may take up to 2 minutes.");
        
        try {
            // Try to use getCellNetworkScanResults method with correct signature: (int, String, String)
            Method scanMethod = telephony.getClass().getMethod("getCellNetworkScanResults", int.class, String.class, String.class);
            System.out.println("Using getCellNetworkScanResults method with 3 parameters...");
            Object scanResults = scanMethod.invoke(telephony, subId, CALLING_PACKAGE, null);
            
            if (scanResults != null) {
                System.out.println("Scan results received: " + scanResults.getClass().getName());
                
                // The result is usually a CellNetworkScanResult object
                Method getOperatorsMethod = scanResults.getClass().getMethod("getOperators");
                List<?> operators = (List<?>) getOperatorsMethod.invoke(scanResults);
                
                if (operators != null && !operators.isEmpty()) {
                    System.out.println("\nAvailable Networks:");
                    System.out.println(STATUS_SEPARATOR);
                    
                    int index = 1;
                    for (Object operator : operators) {
                        displayOperatorInfo(index++, operator);
                    }
                } else {
                    System.out.println("No networks found in scan (operators list is empty)");
                }
            } else {
                System.out.println("Network scan returned null results");
            }
            
        } catch (NoSuchMethodException e) {
            System.out.println("getCellNetworkScanResults method not found, trying alternatives...");
            tryAlternativeNetworkScan(telephony, subId);
        } catch (Exception e) {
            Ln.e("Error scanning for networks", e);
            System.out.println(StatusEmojis.ERROR + " Network scan failed: " + e.getMessage());
            
            // Try alternative methods if the main one fails
            System.out.println("Trying alternative scan methods...");
            tryAlternativeNetworkScan(telephony, subId);
        }
    }
    
    private static void tryAlternativeNetworkScan(IInterface telephony, int subId) {
        // Try different scanning methods that might be available
        
        // Method 1: Try getAvailableNetworks
        try {
            Method scanMethod = telephony.getClass().getMethod("getAvailableNetworks", int.class);
            System.out.println("Trying getAvailableNetworks method...");
            Object result = scanMethod.invoke(telephony, subId);
            
            if (result instanceof List) {
                List<?> operators = (List<?>) result;
                if (operators != null && !operators.isEmpty()) {
                    System.out.println("\nAvailable Networks (via getAvailableNetworks):");
                    System.out.println(STATUS_SEPARATOR);
                    
                    int index = 1;
                    for (Object operator : operators) {
                        displayOperatorInfo(index++, operator);
                    }
                    return; // Success, exit
                }
            }
            System.out.println("getAvailableNetworks returned no results");
            
        } catch (NoSuchMethodException e) {
            System.out.println("getAvailableNetworks method not found");
        } catch (Exception e) {
            System.out.println("getAvailableNetworks failed: " + e.getMessage());
        }
        
        // Method 2: Try queryAvailableNetworks
        try {
            Method scanMethod = telephony.getClass().getMethod("queryAvailableNetworks", int.class, String.class);
            System.out.println("Trying queryAvailableNetworks method...");
            Object result = scanMethod.invoke(telephony, subId, CALLING_PACKAGE);
            
            if (result instanceof List) {
                List<?> operators = (List<?>) result;
                if (operators != null && !operators.isEmpty()) {
                    System.out.println("\nAvailable Networks (via queryAvailableNetworks):");
                    System.out.println(STATUS_SEPARATOR);
                    
                    int index = 1;
                    for (Object operator : operators) {
                        displayOperatorInfo(index++, operator);
                    }
                    return; // Success, exit
                }
            }
            System.out.println("queryAvailableNetworks returned no results");
            
        } catch (NoSuchMethodException e) {
            System.out.println("queryAvailableNetworks method not found");
        } catch (Exception e) {
            System.out.println("queryAvailableNetworks failed: " + e.getMessage());
        }
        
        // Method 3: Try getCellNetworkScanResults without package parameter
        try {
            Method scanMethod = telephony.getClass().getMethod("getCellNetworkScanResults", int.class);
            System.out.println("Trying getCellNetworkScanResults without package parameter...");
            Object scanResults = scanMethod.invoke(telephony, subId);
            
            if (scanResults != null) {
                Method getOperatorsMethod = scanResults.getClass().getMethod("getOperators");
                List<?> operators = (List<?>) getOperatorsMethod.invoke(scanResults);
                
                if (operators != null && !operators.isEmpty()) {
                    System.out.println("\nAvailable Networks (via getCellNetworkScanResults):");
                    System.out.println(STATUS_SEPARATOR);
                    
                    int index = 1;
                    for (Object operator : operators) {
                        displayOperatorInfo(index++, operator);
                    }
                    return; // Success, exit
                }
            }
            System.out.println("getCellNetworkScanResults (no package) returned no results");
            
        } catch (NoSuchMethodException e) {
            System.out.println("getCellNetworkScanResults (no package) method not found");
        } catch (Exception e) {
            System.out.println("getCellNetworkScanResults (no package) failed: " + e.getMessage());
        }
        
        // Method 4: Try with phoneId instead of subId
        try {
            Method getPhoneIdMethod = telephony.getClass().getMethod("getPhoneId", int.class);
            int phoneId = (int) getPhoneIdMethod.invoke(telephony, subId);
            
            Method scanMethod = telephony.getClass().getMethod("getAvailableNetworks", int.class);
            System.out.println("Trying getAvailableNetworks with phoneId instead of subId...");
            Object result = scanMethod.invoke(telephony, phoneId);
            
            if (result instanceof List) {
                List<?> operators = (List<?>) result;
                if (operators != null && !operators.isEmpty()) {
                    System.out.println("\nAvailable Networks (via phoneId):");
                    System.out.println(STATUS_SEPARATOR);
                    
                    int index = 1;
                    for (Object operator : operators) {
                        displayOperatorInfo(index++, operator);
                    }
                    return; // Success, exit
                }
            }
            System.out.println("phoneId method returned no results");
            
        } catch (Exception e) {
            System.out.println("phoneId method failed: " + e.getMessage());
        }
        
        System.out.println("\nAll network scanning methods failed or are not supported on this device.");
        System.out.println("You can try option 4 to see what network-related methods are available.");
    }
    
    private static void displayOperatorInfo(int index, Object operator) {
        try {
            // OperatorInfo methods
            Method getOperatorAlphaLongMethod = operator.getClass().getMethod("getOperatorAlphaLong");
            Method getOperatorAlphaShortMethod = operator.getClass().getMethod("getOperatorAlphaShort");
            Method getOperatorNumericMethod = operator.getClass().getMethod("getOperatorNumeric");
            Method getStateMethod = operator.getClass().getMethod("getState");
            
            String alphaLong = (String) getOperatorAlphaLongMethod.invoke(operator);
            String alphaShort = (String) getOperatorAlphaShortMethod.invoke(operator);
            String numeric = (String) getOperatorNumericMethod.invoke(operator);
            Object state = getStateMethod.invoke(operator);
            
            String stateStr = "Unknown";
            if (state != null) {
                String stateName = state.toString();
                if (stateName.contains("CURRENT")) {
                    stateStr = "Current " + StatusEmojis.ACTIVE;
                } else if (stateName.contains("AVAILABLE")) {
                    stateStr = "Available";
                } else if (stateName.contains("FORBIDDEN")) {
                    stateStr = "Forbidden ⛔";
                } else {
                    stateStr = stateName;
                }
            }
            
            System.out.printf("%d. %s (%s)%n", index, 
                alphaLong != null ? alphaLong : alphaShort,
                numeric);
            System.out.printf("   Status: %s%n", stateStr);
            System.out.println();
            
        } catch (Exception e) {
            Ln.e("Error displaying operator info", e);
        }
    }
    
    private static void setAutomaticNetworkSelection(IInterface telephony, int subId) {
        try {
            // From the debug output, we see: setNetworkSelectionModeAutomatic(int) → void
            Method setNetworkSelectionModeAutoMethod = telephony.getClass().getMethod(
                "setNetworkSelectionModeAutomatic", int.class);
            
            setNetworkSelectionModeAutoMethod.invoke(telephony, subId);
            System.out.println("\n" + StatusEmojis.YES + " Network selection mode set to automatic");
            System.out.println("The device will now automatically select the best available network");
            
        } catch (Exception e) {
            Ln.e("Error setting automatic network selection", e);
            System.out.println(StatusEmojis.ERROR + " Failed to set automatic network selection: " + e.getMessage());
        }
    }
    
    private static void manuallySelectNetwork(IInterface telephony, int subId) {
        try {
            // First, we need to scan for networks
            System.out.println("\nScanning for available networks first...");
            
            List<?> operators = null;
            
            // Try multiple scanning methods
            try {
                Method scanMethod = telephony.getClass().getMethod("getCellNetworkScanResults", int.class, String.class, String.class);
                Object scanResults = scanMethod.invoke(telephony, subId, CALLING_PACKAGE, null);
                
                if (scanResults != null) {
                    Method getOperatorsMethod = scanResults.getClass().getMethod("getOperators");
                    operators = (List<?>) getOperatorsMethod.invoke(scanResults);
                }
            } catch (Exception e) {
                System.out.println("Primary scan method failed, trying alternatives...");
                
                // Try alternative scanning methods
                try {
                    Method scanMethod = telephony.getClass().getMethod("getAvailableNetworks", int.class);
                    Object result = scanMethod.invoke(telephony, subId);
                    if (result instanceof List) {
                        operators = (List<?>) result;
                    }
                } catch (Exception e2) {
                    // Try with calling package
                    try {
                        Method scanMethod = telephony.getClass().getMethod("queryAvailableNetworks", int.class, String.class);
                        Object result = scanMethod.invoke(telephony, subId, CALLING_PACKAGE);
                        if (result instanceof List) {
                            operators = (List<?>) result;
                        }
                    } catch (Exception e3) {
                        System.out.println("All scanning methods failed");
                    }
                }
            }
            
            if (operators != null && !operators.isEmpty()) {
                System.out.println("\nAvailable Networks:");
                System.out.println(STATUS_SEPARATOR);
                
                List<String> operatorNumericCodes = new ArrayList<>();
                int index = 1;
                
                for (Object operator : operators) {
                    displayOperatorInfo(index++, operator);
                    
                    // Get numeric code for selection
                    try {
                        Method getOperatorNumericMethod = operator.getClass().getMethod("getOperatorNumeric");
                        String numeric = (String) getOperatorNumericMethod.invoke(operator);
                        operatorNumericCodes.add(numeric);
                    } catch (Exception e) {
                        // If getOperatorNumeric fails, try alternative methods
                        try {
                            Method getOperatorNumericMethod = operator.getClass().getMethod("getNumeric");
                            String numeric = (String) getOperatorNumericMethod.invoke(operator);
                            operatorNumericCodes.add(numeric);
                        } catch (Exception e2) {
                            System.out.println("    Warning: Could not get numeric code for this operator");
                            operatorNumericCodes.add(null);
                        }
                    }
                }
                
                System.out.print("Select network number (or 0 to cancel): ");
                String choice = scanner.nextLine().trim();
                
                try {
                    int selection = Integer.parseInt(choice);
                    if (selection == 0) {
                        System.out.println("Cancelled");
                        return;
                    }
                    
                    if (selection > 0 && selection <= operatorNumericCodes.size()) {
                        String selectedOperatorNumeric = operatorNumericCodes.get(selection - 1);
                        
                        if (selectedOperatorNumeric == null) {
                            System.out.println("Cannot select this network (numeric code unavailable)");
                            return;
                        }
                        
                        // Get the OperatorInfo object for manual selection
                        Object selectedOperator = operators.get(selection - 1);
                        
                        // Try manual network selection with correct method signature: (int, OperatorInfo, boolean)
                        boolean success = false;
                        
                        try {
                            // Check if the selectedOperator is already the internal OperatorInfo type
                            Class<?> operatorInfoClass = Class.forName("com.android.internal.telephony.OperatorInfo");
                            
                            if (operatorInfoClass.isInstance(selectedOperator)) {
                                // Direct use if it's already the right type
                                Method setNetworkSelectionManualMethod = telephony.getClass().getMethod(
                                    "setNetworkSelectionModeManual", int.class, operatorInfoClass, boolean.class);
                                
                                boolean result = (boolean) setNetworkSelectionManualMethod.invoke(
                                    telephony, subId, selectedOperator, false);
                                success = result;
                            } else {
                                throw new Exception("Not internal OperatorInfo type");
                            }
                        } catch (Exception e) {
                            System.out.println("Direct use failed: " + e.getMessage());
                            
                            // Fallback: create new internal OperatorInfo
                            try {
                                Class<?> operatorInfoClass = Class.forName("com.android.internal.telephony.OperatorInfo");
                                
                                // Get operator details from the selected operator
                                String operatorName = selectedOperatorNumeric;
                                String operatorShortName = selectedOperatorNumeric;
                                try {
                                    Method getAlphaLongMethod = selectedOperator.getClass().getMethod("getOperatorAlphaLong");
                                    Method getAlphaShortMethod = selectedOperator.getClass().getMethod("getOperatorAlphaShort");
                                    operatorName = (String) getAlphaLongMethod.invoke(selectedOperator);
                                    operatorShortName = (String) getAlphaShortMethod.invoke(selectedOperator);
                                } catch (Exception ex) {
                                    // Use numeric code as fallback
                                }
                                
                                // Create using Parcel
                                android.os.Parcel parcel = android.os.Parcel.obtain();
                                try {
                                    parcel.writeString(operatorName);
                                    parcel.writeString(operatorShortName);
                                    parcel.writeString(selectedOperatorNumeric);
                                    
                                    Class<?> stateClass = Class.forName("com.android.internal.telephony.OperatorInfo$State");
                                    Object availableState = Enum.valueOf((Class<Enum>) stateClass, "AVAILABLE");
                                    parcel.writeSerializable((java.io.Serializable) availableState);
                                    
                                    parcel.writeInt(0); // ran
                                    parcel.setDataPosition(0);
                                    
                                    java.lang.reflect.Field creatorField = operatorInfoClass.getField("CREATOR");
                                    android.os.Parcelable.Creator<?> creator = (android.os.Parcelable.Creator<?>) creatorField.get(null);
                                    Object operatorInfo = creator.createFromParcel(parcel);
                                    
                                    Method setNetworkSelectionManualMethod = telephony.getClass().getMethod(
                                        "setNetworkSelectionModeManual", int.class, operatorInfoClass, boolean.class);
                                    
                                    boolean result = (boolean) setNetworkSelectionManualMethod.invoke(
                                        telephony, subId, operatorInfo, false);
                                    success = result;
                                } finally {
                                    parcel.recycle();
                                }
                            } catch (Exception e2) {
                                System.out.println("Failed to create internal OperatorInfo: " + e2.getMessage());
                                return;
                            }
                        }
                        
                        if (success) {
                            System.out.println("\n" + StatusEmojis.YES + " Network selection successful");
                            System.out.println("Connecting to selected network: " + selectedOperatorNumeric);
                        } else {
                            System.out.println("\n" + StatusEmojis.ERROR + " Network selection failed");
                            System.out.println("The selected network may not be available or compatible");
                        }
                    } else {
                        System.out.println("Invalid selection");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input");
                }
            } else {
                System.out.println("No networks found or network scanning failed");
                System.out.println("You may need to:");
                System.out.println("1. Check if the device has network connectivity");
                System.out.println("2. Ensure the SIM card is properly inserted and activated");
                System.out.println("3. Try option 4 to see available network methods");
            }
            
        } catch (Exception e) {
            Ln.e("Error in manual network selection", e);
            System.out.println(StatusEmojis.ERROR + " Manual network selection failed: " + e.getMessage());
        }
    }
    
    private static void manuallyConnectByPLMN(IInterface telephony, int subId) {
        System.out.println("\n📡 Manual Network Connection by PLMN\n");
        
        System.out.println("Enter the PLMN (MCC+MNC) of the network you want to connect to.");
        System.out.println("Examples:");
        System.out.println("  - 310260 (T-Mobile US)");
        System.out.println("  - 310410 (AT&T US)");
        System.out.println("  - 313100 (FirstNet)");
        System.out.println("  - 311480 (Verizon US)");
        System.out.println();
        System.out.print("Enter PLMN code (or 0 to cancel): ");
        
        String plmn = scanner.nextLine().trim();
        
        if (plmn.equals("0")) {
            System.out.println("Cancelled");
            return;
        }
        
        // Validate PLMN format (should be 5 or 6 digits)
        if (!plmn.matches("\\d{5,6}")) {
            System.out.println(StatusEmojis.ERROR + " Invalid PLMN format. Must be 5 or 6 digits.");
            return;
        }
        
        try {
            // First, let's debug what methods are available
            System.out.println("\nDebug: Looking for network selection methods...");
            Method[] methods = telephony.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                if (methodName.contains("setNetworkSelectionMode") || 
                    methodName.contains("selectNetwork") ||
                    methodName.contains("NetworkManual")) {
                    System.out.println("Found method: " + methodName + " with params: " + 
                        Arrays.toString(method.getParameterTypes()));
                }
            }
            
            // First, try to create OperatorInfo and use the standard method
            boolean success = false;
            
            try {
                // Use the internal telephony OperatorInfo class
                Class<?> operatorInfoClass = Class.forName("com.android.internal.telephony.OperatorInfo");
                
                // Create OperatorInfo object using Parcel
                android.os.Parcel parcel = android.os.Parcel.obtain();
                try {
                    // Write the OperatorInfo data to parcel
                    parcel.writeString(plmn); // operatorAlphaLong
                    parcel.writeString(plmn); // operatorAlphaShort
                    parcel.writeString(plmn); // operatorNumeric
                    
                    // Write the State enum - AVAILABLE
                    Class<?> stateClass = Class.forName("com.android.internal.telephony.OperatorInfo$State");
                    Object availableState = Enum.valueOf((Class<Enum>) stateClass, "AVAILABLE");
                    parcel.writeSerializable((java.io.Serializable) availableState);
                    
                    // Write the RAN type (Radio Access Network) - 0 for unknown, or specific values
                    parcel.writeInt(0); // ran
                    
                    // Reset parcel position
                    parcel.setDataPosition(0);
                    
                    // Use CREATOR to create OperatorInfo from parcel
                    java.lang.reflect.Field creatorField = operatorInfoClass.getField("CREATOR");
                    android.os.Parcelable.Creator<?> creator = (android.os.Parcelable.Creator<?>) creatorField.get(null);
                    Object operatorInfo = creator.createFromParcel(parcel);
                    
                    Method setNetworkSelectionManualMethod = telephony.getClass().getMethod(
                        "setNetworkSelectionModeManual", int.class, operatorInfoClass, boolean.class);
                    
                    System.out.println("\nAttempting to connect to network with PLMN: " + plmn);
                    
                    boolean result = (boolean) setNetworkSelectionManualMethod.invoke(
                        telephony, subId, operatorInfo, false);
                    success = result;
                    
                } finally {
                    parcel.recycle();
                }
                
            } catch (Exception e) {
                System.out.println("Parcel-based method failed: " + e.getMessage());
                e.printStackTrace();
                
                // Try direct constructor approach with internal class
                try {
                    Class<?> operatorInfoClass = Class.forName("com.android.internal.telephony.OperatorInfo");
                    Class<?> stateClass = Class.forName("com.android.internal.telephony.OperatorInfo$State");
                    
                    // Get the constructor (String, String, String, State, int)
                    Object availableState = Enum.valueOf((Class<Enum>) stateClass, "AVAILABLE");
                    
                    // Try to find and use the constructor
                    Object operatorInfo = operatorInfoClass.getConstructor(
                        String.class, String.class, String.class, stateClass, int.class)
                        .newInstance(plmn, plmn, plmn, availableState, 0);
                    
                    Method setNetworkSelectionManualMethod = telephony.getClass().getMethod(
                        "setNetworkSelectionModeManual", int.class, operatorInfoClass, boolean.class);
                    
                    System.out.println("Trying direct constructor method...");
                    
                    boolean result = (boolean) setNetworkSelectionManualMethod.invoke(
                        telephony, subId, operatorInfo, false);
                    success = result;
                    
                } catch (Exception e2) {
                    System.out.println("Direct constructor method failed: " + e2.getMessage());
                    
                    // Last resort: try to call the method with null OperatorInfo
                    try {
                        // Some devices might accept null and use the PLMN from elsewhere
                        Class<?> operatorInfoClass = Class.forName("com.android.internal.telephony.OperatorInfo");
                        Method setNetworkSelectionManualMethod = telephony.getClass().getMethod(
                            "setNetworkSelectionModeManual", int.class, operatorInfoClass, boolean.class);
                        
                        System.out.println("Trying with null OperatorInfo...");
                        
                        // First set the network operator property
                        try {
                            Method setNetworkOperatorMethod = telephony.getClass().getMethod(
                                "setNetworkOperator", int.class, String.class);
                            setNetworkOperatorMethod.invoke(telephony, subId, plmn);
                        } catch (Exception ex) {
                            // Ignore if method doesn't exist
                        }
                        
                        boolean result = (boolean) setNetworkSelectionManualMethod.invoke(
                            telephony, subId, null, false);
                        success = result;
                        
                    } catch (Exception e3) {
                        System.out.println("Null OperatorInfo method failed: " + e3.getMessage());
                    }
                }
            }
            
            if (success) {
                System.out.println("\n" + StatusEmojis.YES + " Network connection request sent successfully");
                System.out.println("The device will attempt to connect to network with PLMN: " + plmn);
                System.out.println("\nNote: Connection may take a few seconds. The network must be:");
                System.out.println("  - Available in your current location");
                System.out.println("  - Compatible with your SIM/eSIM");
                System.out.println("  - Not forbidden by your carrier");
            } else {
                System.out.println("\n" + StatusEmojis.ERROR + " Failed to send network connection request");
                System.out.println("Possible reasons:");
                System.out.println("  - Invalid PLMN code");
                System.out.println("  - Network not available");
                System.out.println("  - SIM/eSIM not compatible with this network");
                System.out.println("  - Device restrictions");
            }
            
            // Wait a moment and check the current network
            Thread.sleep(3000);
            System.out.println("\nChecking current network status...");
            displayCurrentNetworkOperator(telephony, subId);
            
        } catch (Exception e) {
            Ln.e("Error connecting to network by PLMN", e);
            System.out.println(StatusEmojis.ERROR + " Failed to connect to network: " + e.getMessage());
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

