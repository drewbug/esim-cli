package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.os.IInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class TelephonyManager {

    private final IInterface manager;
    private Method getUiccSlotsInfoMethod;
    private static Class<?> uiccSlotInfoClass;

    static TelephonyManager create() {
        IInterface manager = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        return new TelephonyManager(manager);
    }

    private TelephonyManager(IInterface manager) {
        this.manager = manager;
    }

    private static Class<?> getUiccSlotInfoClass() throws ClassNotFoundException {
        if (uiccSlotInfoClass == null) {
            uiccSlotInfoClass = Class.forName("android.telephony.UiccSlotInfo");
        }
        return uiccSlotInfoClass;
    }

    private Method getGetUiccSlotsInfoMethod() throws NoSuchMethodException {
        if (getUiccSlotsInfoMethod == null) {
            getUiccSlotsInfoMethod = manager.getClass().getMethod("getUiccSlotsInfo");
        }
        return getUiccSlotsInfoMethod;
    }

    public Object[] getUiccSlotsInfo() {
        try {
            Method method = getGetUiccSlotsInfoMethod();
            return (Object[]) method.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getUiccSlotsInfo method", e);
            return null;
        }
    }

    public String getLine1NumberForSubscriber(int subId, String callingPackage) {
        try {
            Method method = manager.getClass().getMethod("getLine1NumberForDisplay", 
                int.class, String.class, String.class);
            return (String) method.invoke(manager, subId, callingPackage, null);
        } catch (ReflectiveOperationException e) {
            try {
                Method fallbackMethod = manager.getClass().getMethod("getLine1NumberForSubscriber", 
                    int.class, String.class);
                return (String) fallbackMethod.invoke(manager, subId, callingPackage);
            } catch (ReflectiveOperationException e2) {
                Ln.e("Could not get phone number for subscription " + subId, e2);
                return null;
            }
        }
    }
}

