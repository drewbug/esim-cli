package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SubscriptionService;
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

        try {
            printDeviceImeis();
            printActiveSubscription();
            printTelephonyStates();
        } catch (Exception e) {
            Ln.e("Error initializing ShellMain", e);
        }

        while (true) {
            System.out.println("\n📶 eSIM Activation CLI 📶");
            System.out.println("1. Activate an existing eSIM profile");
            System.out.println("2. Download a new eSIM profile");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");

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
                        System.out.println("Invalid choice, try again.");
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

    private static void printDeviceImeis() {
        IInterface telephony = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        if (telephony == null) {
            Ln.e("ITelephony unavailable");
            return;
        }

        System.out.println("\n📱 Device IMEI(s):");
        for (int slot = 0; slot < 2; slot++) {
            String imei = invokeTelephonyStringMethod(telephony, "getImeiForSlot", slot, CALLING_PACKAGE);
            if (imei != null && !imei.isEmpty())
                System.out.printf("  - Slot %d: %s%n", slot, imei);
        }
    }

    private static void printActiveSubscription() {
        int activeSubId = getActiveSubscriptionId();
        SubscriptionService ss = ServiceManager.getSubscriptionService();
        List<SubscriptionInfo> subs = ss.getAvailableSubscriptionInfoList();

        if (subs != null) {
            for (SubscriptionInfo sub : subs)
                if (sub.getSubscriptionId() == activeSubId) {
                    System.out.printf("\n📶 Active eSIM Profile: %s (ICCID: %s)%n",
                            sub.getDisplayName(), sub.getIccId());
                    return;
                }
        }
        System.out.println("\n📶 Active eSIM Profile: Unknown");
    }

    private static void printTelephonyStates() {
        IInterface telephony = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        if (telephony == null) {
            Ln.e("ITelephony unavailable");
            return;
        }

        int subId = getActiveSubscriptionId();
        System.out.println("\n📡 Telephony States:");

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
        } catch (Exception e) {
            Ln.e("Reflection error: " + method, e);
            System.out.printf("  - %s: ⚠️ ERROR%n", method);
        }
    }

    private static String invokeTelephonyStringMethod(IInterface srv, String method, Object... args) {
        try {
            Class<?>[] types = Arrays.stream(args)
                    .map(a -> a instanceof Integer ? int.class : String.class)
                    .toArray(Class[]::new);
            Method m = srv.getClass().getMethod(method, types);
            return (String) m.invoke(srv, args);
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

