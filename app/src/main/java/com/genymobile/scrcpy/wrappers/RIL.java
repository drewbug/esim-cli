package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class RIL {

    private static final int HAL_SERVICE_NETWORK = 2;
    
    private static Class<?> rilClass;
    private static Object rilInstance;
    private static Method getRadioServiceProxyMethod;

    static {
        try {
            rilClass = Class.forName("com.android.internal.telephony.RIL");
            
            // Try to get RIL instance - this might be available through various paths
            try {
                // Try getting singleton instance if available
                Method getInstance = rilClass.getMethod("getInstance");
                rilInstance = getInstance.invoke(null);
            } catch (Exception e) {
                // If singleton not available, we'll need to access it differently
                // This might require getting it from Phone or other telephony components
                Ln.w("Could not get RIL singleton instance: " + e.getMessage());
            }
        } catch (ClassNotFoundException e) {
            Ln.e("RIL class not found", e);
        }
    }

    private RIL() {
        /* not instantiable */
    }

    private static Method getGetRadioServiceProxyMethod() throws NoSuchMethodException {
        if (getRadioServiceProxyMethod == null) {
            getRadioServiceProxyMethod = rilClass.getMethod("getRadioServiceProxy", int.class);
        }
        return getRadioServiceProxyMethod;
    }

    public static Object getRadioServiceProxy() {
        return getRadioServiceProxy(HAL_SERVICE_NETWORK);
    }

    public static Object getRadioServiceProxy(int service) {
        if (rilClass == null) {
            Ln.e("RIL class not available");
            return null;
        }

        try {
            Object instance = getRilInstance();
            if (instance == null) {
                Ln.e("RIL instance not available");
                return null;
            }

            Method method = getGetRadioServiceProxyMethod();
            return method.invoke(instance, service);
        } catch (Exception e) {
            Ln.e("Could not invoke getRadioServiceProxy with service " + service, e);
            return null;
        }
    }

    public static Object getRadioServiceProxyForNetwork() {
        return getRadioServiceProxy(HAL_SERVICE_NETWORK);
    }

    public static Object getRadioServiceProxyForVoice() {
        return getRadioServiceProxy(0); // HAL_SERVICE_VOICE
    }

    public static Object getRadioServiceProxyForData() {
        return getRadioServiceProxy(1); // HAL_SERVICE_DATA
    }

    public static Object getRadioServiceProxyForMessaging() {
        return getRadioServiceProxy(3); // HAL_SERVICE_MESSAGING
    }

    private static Object getRilInstance() {
        if (rilInstance != null) {
            return rilInstance;
        }

        // Try alternative methods to get RIL instance
        try {
            // Try getting from Phone class
            Class<?> phoneClass = Class.forName("com.android.internal.telephony.Phone");
            Method getDefaultPhone = phoneClass.getMethod("getDefaultPhone");
            Object phone = getDefaultPhone.invoke(null);
            
            if (phone != null) {
                Method getCi = phone.getClass().getMethod("getCi");
                rilInstance = getCi.invoke(phone);
                return rilInstance;
            }
        } catch (Exception e) {
            Ln.w("Could not get RIL instance from Phone: " + e.getMessage());
        }

        try {
            // Try getting from PhoneFactory
            Class<?> phoneFactoryClass = Class.forName("com.android.internal.telephony.PhoneFactory");
            Method getDefaultPhone = phoneFactoryClass.getMethod("getDefaultPhone");
            Object phone = getDefaultPhone.invoke(null);
            
            if (phone != null) {
                Method getCi = phone.getClass().getMethod("getCi");
                rilInstance = getCi.invoke(phone);
                return rilInstance;
            }
        } catch (Exception e) {
            Ln.w("Could not get RIL instance from PhoneFactory: " + e.getMessage());
        }

        return null;
    }

    public static boolean isRilAvailable() {
        return rilClass != null && getRilInstance() != null;
    }

    public static int getHalServiceNetwork() {
        return HAL_SERVICE_NETWORK;
    }
}