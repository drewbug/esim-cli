package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.os.IInterface;
import android.telephony.UiccCardInfo;

import java.lang.reflect.Method;
import java.util.List;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class TelephonyManager {

    private final IInterface manager;
    private Method getUiccCardsInfoMethod;

    static TelephonyManager create() {
        IInterface manager = ServiceManager.getService("phone", "com.android.internal.telephony.ITelephony");
        return new TelephonyManager(manager);
    }

    private TelephonyManager(IInterface manager) {
        this.manager = manager;
    }

    private Method getGetUiccCardsInfoMethod() throws NoSuchMethodException {
        if (getUiccCardsInfoMethod == null) {
            getUiccCardsInfoMethod = manager.getClass().getMethod("getUiccCardsInfo", String.class);
        }
        return getUiccCardsInfoMethod;
    }

    public List<UiccCardInfo> getUiccCardsInfo() {
        try {
            Method method = getGetUiccCardsInfoMethod();
            return (List<UiccCardInfo>) method.invoke(manager, FakeContext.PACKAGE_NAME);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }

}
