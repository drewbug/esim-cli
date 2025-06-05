package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.IInterface;
import android.os.Looper;
import android.os.ResultReceiver;

import com.android.internal.telephony.IIntegerConsumer;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    public String getPhoneNumberFromSubscriptionManager(int subId) {
        try {
            IInterface subManager = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
            if (subManager == null) return null;
            
            // Try newer getPhoneNumber method (API 33+)
            try {
                Method method = subManager.getClass().getMethod("getPhoneNumber", int.class, int.class, String.class, String.class);
                return (String) method.invoke(subManager, subId, 1, FakeContext.get().getOpPackageName(), null);
            } catch (NoSuchMethodException e) {
                // Try older getPhoneNumber method
                try {
                    Method method = subManager.getClass().getMethod("getPhoneNumber", int.class);
                    return (String) method.invoke(subManager, subId);
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
        } catch (Exception e) {
            Ln.e("Could not get phone number from SubscriptionManager for subscription " + subId, e);
            return null;
        }
    }

    public String getSubscriptionProperty(int subId, String property) {
        try {
            IInterface subManager = ServiceManager.getService("isub", "com.android.internal.telephony.ISub");
            if (subManager == null) return null;
            
            Method method = subManager.getClass().getMethod("getSubscriptionProperty", int.class, String.class, String.class);
            return (String) method.invoke(subManager, subId, property, FakeContext.get().getOpPackageName());
        } catch (Exception e) {
            Ln.e("Could not get subscription property " + property + " for subscription " + subId, e);
            return null;
        }
    }

    public Bundle invokeSatelliteMethodWithResultReceiver(String methodName) {
        try {
            Method method = manager.getClass().getMethod(methodName, ResultReceiver.class);
            
            // Create a dedicated thread and looper for handling the result
            final CountDownLatch latch = new CountDownLatch(1);
            final Bundle[] resultBundle = new Bundle[1];
            final Looper[] looper = new Looper[1];
            
            Thread handlerThread = new Thread(() -> {
                Looper.prepare();
                looper[0] = Looper.myLooper();
                
                // Create a ResultReceiver to capture the response
                Handler handler = new Handler(looper[0]);
                ResultReceiver receiver = new ResultReceiver(handler) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        resultBundle[0] = resultData;
                        latch.countDown();
                        looper[0].quit();
                    }
                };
                
                try {
                    // Invoke the method
                    method.invoke(manager, receiver);
                } catch (Exception e) {
                    Ln.e("Error invoking " + methodName, e);
                    latch.countDown();
                    looper[0].quit();
                }
                
                Looper.loop();
            });
            
            handlerThread.start();
            
            // Wait for the result with a timeout
            if (latch.await(5, TimeUnit.SECONDS)) {
                return resultBundle[0];
            } else {
                Ln.e("Timeout waiting for " + methodName);
                if (looper[0] != null) {
                    looper[0].quit();
                }
                return null;
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device
            return null;
        } catch (Exception e) {
            Ln.e("Could not invoke " + methodName, e);
            return null;
        }
    }

    public Bundle requestSatelliteCapabilities() {
        return invokeSatelliteMethodWithResultReceiver("requestSatelliteCapabilities");
    }

    public Boolean requestIsSatelliteEnabled() {
        Bundle result = invokeSatelliteMethodWithResultReceiver("requestIsSatelliteEnabled");
        if (result != null && result.containsKey("satellite_enabled")) {
            return result.getBoolean("satellite_enabled");
        }
        return null;
    }

    public Boolean requestIsDemoModeEnabled() {
        Bundle result = invokeSatelliteMethodWithResultReceiver("requestIsDemoModeEnabled");
        if (result != null && result.containsKey("demo_mode_enabled")) {
            return result.getBoolean("demo_mode_enabled");
        }
        return null;
    }

    public Boolean requestIsSatelliteSupported() {
        Bundle result = invokeSatelliteMethodWithResultReceiver("requestIsSatelliteSupported");
        if (result != null && result.containsKey("satellite_supported")) {
            return result.getBoolean("satellite_supported");
        }
        return null;
    }

    public Boolean requestIsSatelliteProvisioned() {
        Bundle result = invokeSatelliteMethodWithResultReceiver("requestIsSatelliteProvisioned");
        if (result != null && result.containsKey("satellite_provisioned")) {
            return result.getBoolean("satellite_provisioned");
        }
        return null;
    }

    public Integer requestSelectedNbIotSatelliteSubscriptionId() {
        Bundle result = invokeSatelliteMethodWithResultReceiver("requestSelectedNbIotSatelliteSubscriptionId");
        if (result != null && result.containsKey("selected_nb_iot_satellite_subscription_id")) {
            return result.getInt("selected_nb_iot_satellite_subscription_id");
        }
        return null;
    }

    public int requestSatelliteEnabled(boolean enableSatellite, boolean enableDemoMode, boolean isEmergency) {
        try {
            Method method = manager.getClass().getMethod("requestSatelliteEnabled", 
                boolean.class, boolean.class, boolean.class, IIntegerConsumer.class);
            
            // Create a latch to wait for the result
            final CountDownLatch latch = new CountDownLatch(1);
            final int[] resultCode = new int[1];
            
            // Create a concrete implementation of IIntegerConsumer.Stub
            IIntegerConsumer consumer = new IIntegerConsumer.Stub() {
                @Override
                public void accept(int result) {
                    resultCode[0] = result;
                    latch.countDown();
                }
            };
            
            // Invoke the method
            method.invoke(manager, enableSatellite, enableDemoMode, isEmergency, consumer);
            
            // Wait for the result with a timeout
            if (latch.await(5, TimeUnit.SECONDS)) {
                return resultCode[0];
            } else {
                Ln.e("Timeout waiting for satellite enable result");
                return -1;
            }
        } catch (NoSuchMethodException e) {
            Ln.e("requestSatelliteEnabled method not found", e);
            return -1;
        } catch (Exception e) {
            Ln.e("Could not request satellite enabled", e);
            return -1;
        }
    }
    
    public int[] getSatelliteDisallowedReasons() {
        try {
            Method method = manager.getClass().getMethod("getSatelliteDisallowedReasons");
            return (int[]) method.invoke(manager);
        } catch (NoSuchMethodException e) {
            Ln.e("getSatelliteDisallowedReasons method not found", e);
            return null;
        } catch (Exception e) {
            Ln.e("Could not get satellite disallowed reasons", e);
            return null;
        }
    }
    
    public Boolean requestIsCommunicationAllowedForCurrentLocation(int subId) {
        try {
            Method method = manager.getClass().getMethod("requestIsCommunicationAllowedForCurrentLocation", 
                int.class, ResultReceiver.class);
            
            // Create a dedicated thread and looper for handling the result
            final CountDownLatch latch = new CountDownLatch(1);
            final Bundle[] resultBundle = new Bundle[1];
            final Looper[] looper = new Looper[1];
            
            Thread handlerThread = new Thread(() -> {
                Looper.prepare();
                looper[0] = Looper.myLooper();
                
                // Create a ResultReceiver to capture the response
                Handler handler = new Handler(looper[0]);
                ResultReceiver receiver = new ResultReceiver(handler) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        resultBundle[0] = resultData;
                        latch.countDown();
                        looper[0].quit();
                    }
                };
                
                try {
                    // Invoke the method
                    method.invoke(manager, subId, receiver);
                } catch (Exception e) {
                    Ln.e("Error invoking requestIsCommunicationAllowedForCurrentLocation", e);
                    latch.countDown();
                    looper[0].quit();
                }
                
                Looper.loop();
            });
            
            handlerThread.start();
            
            // Wait for the result with a timeout
            if (latch.await(5, TimeUnit.SECONDS)) {
                if (resultBundle[0] != null && resultBundle[0].containsKey("communication_allowed")) {
                    return resultBundle[0].getBoolean("communication_allowed");
                }
                return null;
            } else {
                Ln.e("Timeout waiting for requestIsCommunicationAllowedForCurrentLocation");
                if (looper[0] != null) {
                    looper[0].quit();
                }
                return null;
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device
            return null;
        } catch (Exception e) {
            Ln.e("Could not invoke requestIsCommunicationAllowedForCurrentLocation", e);
            return null;
        }
    }
    
    public Bundle invokeCommunicationAllowedMethodWithResultReceiver(String methodName, int subId) {
        try {
            Method method = manager.getClass().getMethod(methodName, int.class, ResultReceiver.class);
            
            // Create a dedicated thread and looper for handling the result
            final CountDownLatch latch = new CountDownLatch(1);
            final Bundle[] resultBundle = new Bundle[1];
            final Looper[] looper = new Looper[1];
            
            Thread handlerThread = new Thread(() -> {
                Looper.prepare();
                looper[0] = Looper.myLooper();
                
                // Create a ResultReceiver to capture the response
                Handler handler = new Handler(looper[0]);
                ResultReceiver receiver = new ResultReceiver(handler) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        resultBundle[0] = resultData;
                        latch.countDown();
                        looper[0].quit();
                    }
                };
                
                try {
                    // Invoke the method
                    method.invoke(manager, subId, receiver);
                } catch (Exception e) {
                    Ln.e("Error invoking " + methodName, e);
                    latch.countDown();
                    looper[0].quit();
                }
                
                Looper.loop();
            });
            
            handlerThread.start();
            
            // Wait for the result with a timeout
            if (latch.await(5, TimeUnit.SECONDS)) {
                return resultBundle[0];
            } else {
                Ln.e("Timeout waiting for " + methodName);
                if (looper[0] != null) {
                    looper[0].quit();
                }
                return null;
            }
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this device
            return null;
        } catch (Exception e) {
            Ln.e("Could not invoke " + methodName, e);
            return null;
        }
    }
}

