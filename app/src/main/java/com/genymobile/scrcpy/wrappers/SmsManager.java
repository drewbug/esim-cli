package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.Ln;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.os.IInterface;

import java.lang.reflect.Method;
import java.util.Arrays;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class SmsManager {

    private final IInterface manager;
    private Method sendTextMessageMethod;

    static SmsManager create() {
        IInterface manager = ServiceManager.getService("isms", "com.android.internal.telephony.ISms");
        return new SmsManager(manager);
    }

    private SmsManager(IInterface manager) {
        this.manager = manager;
    }

    private Method findSendTextMethod() {
        // Look for sendText methods in the interface
        Method[] methods = manager.getClass().getDeclaredMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (name.equals("sendTextForSubscriber") || name.equals("sendText")) {
                Ln.i("Found method: " + name + " with params: " + Arrays.toString(method.getParameterTypes()));
                return method;
            }
        }
        
        // If not found, print all available methods for debugging
        Ln.e("Could not find sendText method. Available methods:");
        for (Method method : methods) {
            if (method.getName().contains("send")) {
                Ln.e("  " + method.getName() + " - " + Arrays.toString(method.getParameterTypes()));
            }
        }
        return null;
    }

    public void sendTextMessage(int subId, String callingPackage, String destAddr, String text) {
        try {
            if (sendTextMessageMethod == null) {
                sendTextMessageMethod = findSendTextMethod();
                if (sendTextMessageMethod == null) {
                    throw new RuntimeException("Could not find send text method in ISms interface");
                }
            }
            
            Class<?>[] paramTypes = sendTextMessageMethod.getParameterTypes();
            Object[] args;
            
            // Build arguments based on method signature
            if (paramTypes.length == 10) {
                // subId, pkg, attributionTag, destAddr, scAddr, text, sentIntent, deliveryIntent, persist, messageId
                args = new Object[]{subId, callingPackage, null, destAddr, null, text, null, null, false, 0L};
            } else if (paramTypes.length == 9) {
                // subId, pkg, attributionTag, destAddr, scAddr, text, sentIntent, deliveryIntent, persist
                args = new Object[]{subId, callingPackage, null, destAddr, null, text, null, null, false};
            } else if (paramTypes.length == 8) {
                // subId, pkg, destAddr, scAddr, text, sentIntent, deliveryIntent, persist
                args = new Object[]{subId, callingPackage, destAddr, null, text, null, null, false};
            } else if (paramTypes.length == 7) {
                // pkg, destAddr, scAddr, text, sentIntent, deliveryIntent, persist
                args = new Object[]{callingPackage, destAddr, null, text, null, null, false};
            } else {
                throw new RuntimeException("Unexpected parameter count for send method: " + paramTypes.length);
            }
            
            sendTextMessageMethod.invoke(manager, args);
            Ln.i("SMS sent successfully to " + destAddr);
        } catch (Exception e) {
            Ln.e("Could not send SMS", e);
            throw new RuntimeException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    public void sendTextMessage(String destAddr, String text) {
        // Use default subscription
        sendTextMessage(-1, "radio.ab3j.esim", destAddr, text);
    }
}