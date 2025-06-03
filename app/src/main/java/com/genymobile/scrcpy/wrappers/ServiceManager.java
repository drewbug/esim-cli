package com.genymobile.scrcpy.wrappers;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.IInterface;

import java.lang.reflect.Method;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ServiceManager {

    private static final Method GET_SERVICE_METHOD;

    static {
        try {
            GET_SERVICE_METHOD = Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static TelephonyManager telephonyManager;
    private static SubscriptionService subscriptionService;
    private static ConnectivityManager connectivityManager;

    private ServiceManager() {
        /* not instantiable */
    }

    public static IInterface getService(String service, String type) {
        try {
            IBinder binder = (IBinder) GET_SERVICE_METHOD.invoke(null, service);
            Method asInterfaceMethod = Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class);
            return (IInterface) asInterfaceMethod.invoke(null, binder);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static TelephonyManager getTelephonyManager() {
        if (telephonyManager == null) {
            telephonyManager = TelephonyManager.create();
        }
        return telephonyManager;
    }

    public static SubscriptionService getSubscriptionService() {
        if (subscriptionService == null) {
            subscriptionService = SubscriptionService.create();
        }
        return subscriptionService;
    }

    public static ConnectivityManager getConnectivityManager() {
        if (connectivityManager == null) {
            connectivityManager = ConnectivityManager.create();
        }
        return connectivityManager;
    }
}
