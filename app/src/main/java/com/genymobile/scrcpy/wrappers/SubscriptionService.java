package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.os.IInterface;
import android.telephony.SubscriptionInfo;
import android.telephony.UiccCardInfo;

import java.lang.reflect.Method;
import java.util.List;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class SubscriptionService {

    private final IInterface service;
    private Method getAvailableSubscriptionInfoListMethod;


    static SubscriptionService create() {
        IInterface service = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
        return new SubscriptionService(service);
    }

    private SubscriptionService(IInterface service) {
        this.service = service;
    }

    private Method getGetAvailableSubscriptionInfoListMethod() throws NoSuchMethodException {
        if (getAvailableSubscriptionInfoListMethod == null) {
            getAvailableSubscriptionInfoListMethod = service.getClass().getMethod("getAvailableSubscriptionInfoList", String.class, String.class);
        }
        return getAvailableSubscriptionInfoListMethod;
    }

    public List<SubscriptionInfo> getAvailableSubscriptionInfoList() {
        try {
            Method method = getGetAvailableSubscriptionInfoListMethod();
            return (List<SubscriptionInfo>) method.invoke(service, FakeContext.PACKAGE_NAME, null);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }

}
