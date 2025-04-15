package radio.ab3j.esim;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SubscriptionService;
import com.genymobile.scrcpy.Ln;
import com.genymobile.scrcpy.FakeContext;

import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import android.telephony.euicc.DownloadableSubscription;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Scanner;

public class ShellMain {

    private static final String EUICC_SERVICE = "econtroller";
    private static final String EUICC_INTERFACE = "com.android.internal.telephony.euicc.IEuiccController";
    private static final String CALLING_PACKAGE = FakeContext.PACKAGE_NAME;

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
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

    private static void printDeviceImeis() {
        IInterface telephonyManager = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");

        if (telephonyManager == null) {
            Ln.e("ITelephony service unavailable");
            return;
        }

        System.out.println("\n📱 Device IMEI(s):");
        boolean found = false;
        for (int slot = 0; slot < 2; slot++) {
            String imei = invokeTelephonyStringMethod(telephonyManager, "getImeiForSlot", slot, CALLING_PACKAGE);
            if (imei != null && !imei.isEmpty()) {
                System.out.printf("  - Slot %d: %s%n", slot, imei);
                found = true;
            }
        }
        if (!found) {
            System.out.println("  (No IMEI found or device has restrictions)");
        }
    }

    private static void printActiveSubscription() {
        int activeSubId = getActiveSubscriptionId();
        if (activeSubId == -1) {
            System.out.println("\n📶 Active eSIM Profile: Not detected");
            return;
        }

        SubscriptionService subscriptionService = ServiceManager.getSubscriptionService();
        List<SubscriptionInfo> subscriptions = subscriptionService.getAvailableSubscriptionInfoList();
        if (subscriptions != null) {
            for (SubscriptionInfo sub : subscriptions) {
                if (sub.getSubscriptionId() == activeSubId) {
                    System.out.printf("\n📶 Active eSIM Profile: %s (ICCID: %s, ID: %d)%n",
                            sub.getDisplayName(), sub.getIccId(), sub.getSubscriptionId());
                    return;
                }
            }
        }
        System.out.println("\n📶 Active eSIM Profile: Unknown Subscription ID " + activeSubId);
    }

    private static void printTelephonyStates() {
        IInterface telephonyManager = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        if (telephonyManager == null) {
            Ln.e("ITelephony service unavailable");
            return;
        }

        int subId = getActiveSubscriptionId();

        System.out.println("\n📡 Telephony States:");
        printBoolMethod(telephonyManager, "isConcurrentVoiceAndDataAllowed", subId);
        printBoolMethod(telephonyManager, "isDataConnectivityPossible", subId);
        printBoolMethod(telephonyManager, "isDataEnabled", subId);
        printBoolMethod(telephonyManager, "isHearingAidCompatibilitySupported");
        printBoolMethod(telephonyManager, "isIdle", CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isIdleForSubscriber", subId, CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isImsRegistered", subId);
        printBoolMethod(telephonyManager, "isOffhook", CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isOffhookForSubscriber", subId, CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isRadioOn", CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isRadioOnForSubscriber", subId, CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isRinging", CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isRingingForSubscriber", subId, CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isTtyModeSupported");
        printBoolMethod(telephonyManager, "isUserDataEnabled", subId);
        printBoolMethod(telephonyManager, "isVideoCallingEnabled", CALLING_PACKAGE);
        printBoolMethod(telephonyManager, "isVideoTelephonyAvailable", subId);
        printBoolMethod(telephonyManager, "isVolteAvailable", subId);
        printBoolMethod(telephonyManager, "isWifiCallingAvailable", subId);
        printBoolMethod(telephonyManager, "isWorldPhone");
    }

    private static void printBoolMethod(IInterface service, String method, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++)
                paramTypes[i] = args[i] instanceof Integer ? int.class : String.class;
            Method m = service.getClass().getMethod(method, paramTypes);
            boolean result = (boolean) m.invoke(service, args);
            System.out.printf("  - %s: %s%n", method, result ? "✅ YES" : "❌ NO");
        } catch (Exception e) {
            Ln.e("Reflection error invoking " + method, e);
            System.out.printf("  - %s: ⚠️ ERROR%n", method);
        }
    }

    private static String invokeTelephonyStringMethod(IInterface service, String method, Object... args) {
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++)
                paramTypes[i] = args[i] instanceof Integer ? int.class : String.class;
            Method m = service.getClass().getMethod(method, paramTypes);
            return (String) m.invoke(service, args);
        } catch (Exception e) {
            Ln.e("Error invoking telephony method: " + method, e);
            return null;
        }
    }

    private static int getActiveSubscriptionId() {
        try {
            IInterface subscriptionService = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
            Method getDefaultSubIdMethod = subscriptionService.getClass().getMethod("getDefaultSubId");
            return (int) getDefaultSubIdMethod.invoke(subscriptionService);
        } catch (Exception e) {
            Ln.e("Could not determine active subscription ID", e);
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
                System.out.printf("%d. %s (ICCID: %s, ID: %d)%s%n",
                        index++,
                        sub.getDisplayName(),
                        sub.getIccId(),
                        sub.getSubscriptionId(),
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

            Ln.i("Activating existing eSIM profile (subscription ID: " + subscriptionId + ")...");
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

