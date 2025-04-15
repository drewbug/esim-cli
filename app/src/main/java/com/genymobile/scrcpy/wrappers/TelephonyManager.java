package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.os.IInterface;
import android.telephony.UiccSlotInfo;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class TelephonyManager {

    private final IInterface manager;
    private Method getUiccSlotsInfoMethod;

    static TelephonyManager create() {
        IInterface manager = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        return new TelephonyManager(manager);
    }

    private TelephonyManager(IInterface manager) {
        this.manager = manager;
    }

    private Method getGetUiccSlotsInfoMethod() throws NoSuchMethodException {
        if (getUiccSlotsInfoMethod == null) {
            getUiccSlotsInfoMethod = manager.getClass().getMethod("getUiccSlotsInfo");
        }
        return getUiccSlotsInfoMethod;
    }

    public UiccSlotInfo[] getUiccSlotsInfo() {
        try {
            Method method = getGetUiccSlotsInfoMethod();
            return (UiccSlotInfo[]) method.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getUiccSlotsInfo method", e);
            return null;
        }
    }
}

