package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.Ln;
import android.annotation.SuppressLint;
import android.os.IInterface;

import java.lang.reflect.Method;
import java.util.List;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ConnectivityManager {
    private final IInterface manager;
    private Method getAllNetworksMethod;
    private Method getNetworkCapabilitiesMethod;
    private static Class<?> networkClass;
    private static Class<?> networkCapabilitiesClass;
    private Method getTransportInfoMethod;
    private Method hasTransportMethod;
    private Method hasCapabilityMethod;
    private Method getLinkPropertiesMethod;
    private static Class<?> linkPropertiesClass;
    private Method getInterfaceNameMethod;
    private Method getLinkAddressesMethod;
    private Method getDnsServersMethod;
    private Method getRoutesMethod;
    private Method getMtuMethod;

    static ConnectivityManager create() {
        IInterface manager = ServiceManager.getService("connectivity", "android.net.IConnectivityManager");
        return new ConnectivityManager(manager);
    }

    private ConnectivityManager(IInterface manager) {
        this.manager = manager;
    }

    private static Class<?> getNetworkClass() throws ClassNotFoundException {
        if (networkClass == null) {
            networkClass = Class.forName("android.net.Network");
        }
        return networkClass;
    }

    private static Class<?> getNetworkCapabilitiesClass() throws ClassNotFoundException {
        if (networkCapabilitiesClass == null) {
            networkCapabilitiesClass = Class.forName("android.net.NetworkCapabilities");
        }
        return networkCapabilitiesClass;
    }

    private static Class<?> getLinkPropertiesClass() throws ClassNotFoundException {
        if (linkPropertiesClass == null) {
            linkPropertiesClass = Class.forName("android.net.LinkProperties");
        }
        return linkPropertiesClass;
    }

    private Method getGetAllNetworksMethod() throws NoSuchMethodException {
        if (getAllNetworksMethod == null) {
            getAllNetworksMethod = manager.getClass().getMethod("getAllNetworks");
        }
        return getAllNetworksMethod;
    }

    private Method getGetNetworkCapabilitiesMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getNetworkCapabilitiesMethod == null) {
            getNetworkCapabilitiesMethod = manager.getClass().getMethod("getNetworkCapabilities", getNetworkClass(), String.class, String.class);
        }
        return getNetworkCapabilitiesMethod;
    }

    private Method getGetTransportInfoMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getTransportInfoMethod == null) {
            getTransportInfoMethod = getNetworkCapabilitiesClass().getMethod("getTransportInfo");
        }
        return getTransportInfoMethod;
    }

    private Method getHasTransportMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (hasTransportMethod == null) {
            hasTransportMethod = getNetworkCapabilitiesClass().getMethod("hasTransport", int.class);
        }
        return hasTransportMethod;
    }

    private Method getHasCapabilityMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (hasCapabilityMethod == null) {
            hasCapabilityMethod = getNetworkCapabilitiesClass().getMethod("hasCapability", int.class);
        }
        return hasCapabilityMethod;
    }

    private Method getGetLinkPropertiesMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getLinkPropertiesMethod == null) {
            getLinkPropertiesMethod = manager.getClass().getMethod("getLinkProperties", getNetworkClass());
        }
        return getLinkPropertiesMethod;
    }

    private Method getGetInterfaceNameMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getInterfaceNameMethod == null) {
            getInterfaceNameMethod = getLinkPropertiesClass().getMethod("getInterfaceName");
        }
        return getInterfaceNameMethod;
    }

    private Method getGetLinkAddressesMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getLinkAddressesMethod == null) {
            getLinkAddressesMethod = getLinkPropertiesClass().getMethod("getLinkAddresses");
        }
        return getLinkAddressesMethod;
    }

    private Method getGetDnsServersMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getDnsServersMethod == null) {
            getDnsServersMethod = getLinkPropertiesClass().getMethod("getDnsServers");
        }
        return getDnsServersMethod;
    }

    private Method getGetRoutesMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getRoutesMethod == null) {
            getRoutesMethod = getLinkPropertiesClass().getMethod("getRoutes");
        }
        return getRoutesMethod;
    }

    private Method getGetMtuMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (getMtuMethod == null) {
            getMtuMethod = getLinkPropertiesClass().getMethod("getMtu");
        }
        return getMtuMethod;
    }

    public Object[] getAllNetworks() {
        try {
            Method method = getGetAllNetworksMethod();
            return (Object[]) method.invoke(manager);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getAllNetworks method", e);
            return new Object[0];
        }
    }

    public Object getNetworkCapabilities(Object network) {
        try {
            Method method = getGetNetworkCapabilitiesMethod();
            return method.invoke(manager, network, FakeContext.PACKAGE_NAME, null);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getNetworkCapabilities method", e);
            return null;
        }
    }

    public Object getTransportInfo(Object networkCapabilities) {
        try {
            if (networkCapabilities == null) {
                return null;
            }
            Method method = getGetTransportInfoMethod();
            return method.invoke(networkCapabilities);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getTransportInfo method", e);
            return null;
        }
    }

    public boolean hasTransport(Object networkCapabilities, int transportType) {
        try {
            if (networkCapabilities == null) {
                return false;
            }
            Method method = getHasTransportMethod();
            return (Boolean) method.invoke(networkCapabilities, transportType);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke hasTransport method", e);
            return false;
        }
    }

    public boolean hasCapability(Object networkCapabilities, int capability) {
        try {
            if (networkCapabilities == null) {
                return false;
            }
            Method method = getHasCapabilityMethod();
            return (Boolean) method.invoke(networkCapabilities, capability);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke hasCapability method", e);
            return false;
        }
    }

    public Object getLinkProperties(Object network) {
        try {
            Method method = getGetLinkPropertiesMethod();
            return method.invoke(manager, network);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getLinkProperties method", e);
            return null;
        }
    }

    public String getInterfaceName(Object linkProperties) {
        try {
            if (linkProperties == null) {
                return null;
            }
            Method method = getGetInterfaceNameMethod();
            return (String) method.invoke(linkProperties);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getInterfaceName method", e);
            return null;
        }
    }

    public List<?> getLinkAddresses(Object linkProperties) {
        try {
            if (linkProperties == null) {
                return null;
            }
            Method method = getGetLinkAddressesMethod();
            return (List<?>) method.invoke(linkProperties);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getLinkAddresses method", e);
            return null;
        }
    }

    public List<?> getDnsServers(Object linkProperties) {
        try {
            if (linkProperties == null) {
                return null;
            }
            Method method = getGetDnsServersMethod();
            return (List<?>) method.invoke(linkProperties);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getDnsServers method", e);
            return null;
        }
    }

    public List<?> getRoutes(Object linkProperties) {
        try {
            if (linkProperties == null) {
                return null;
            }
            Method method = getGetRoutesMethod();
            return (List<?>) method.invoke(linkProperties);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getRoutes method", e);
            return null;
        }
    }

    public int getMtu(Object linkProperties) {
        try {
            if (linkProperties == null) {
                return 0;
            }
            Method method = getGetMtuMethod();
            return (Integer) method.invoke(linkProperties);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke getMtu method", e);
            return 0;
        }
    }

    // Transport type constants from NetworkCapabilities
    public static final int TRANSPORT_CELLULAR = 0;
    public static final int TRANSPORT_WIFI = 1;
    public static final int TRANSPORT_BLUETOOTH = 2;
    public static final int TRANSPORT_ETHERNET = 3;
    public static final int TRANSPORT_VPN = 4;
    public static final int TRANSPORT_WIFI_AWARE = 5;
    public static final int TRANSPORT_LOWPAN = 6;
    public static final int TRANSPORT_TEST = 7;
    public static final int TRANSPORT_USB = 8;
    public static final int TRANSPORT_THREAD = 9;
    public static final int TRANSPORT_SATELLITE = 10;

    // Capability constants from NetworkCapabilities
    public static final int NET_CAPABILITY_MMS = 0;
    public static final int NET_CAPABILITY_SUPL = 1;
    public static final int NET_CAPABILITY_DUN = 2;
    public static final int NET_CAPABILITY_FOTA = 3;
    public static final int NET_CAPABILITY_IMS = 4;
    public static final int NET_CAPABILITY_CBS = 5;
    public static final int NET_CAPABILITY_WIFI_P2P = 6;
    public static final int NET_CAPABILITY_IA = 7;
    public static final int NET_CAPABILITY_RCS = 8;
    public static final int NET_CAPABILITY_XCAP = 9;
    public static final int NET_CAPABILITY_EIMS = 10;
    public static final int NET_CAPABILITY_NOT_METERED = 11;
    public static final int NET_CAPABILITY_INTERNET = 12;
    public static final int NET_CAPABILITY_NOT_RESTRICTED = 13;
    public static final int NET_CAPABILITY_TRUSTED = 14;
    public static final int NET_CAPABILITY_NOT_VPN = 15;
    public static final int NET_CAPABILITY_VALIDATED = 16;
    public static final int NET_CAPABILITY_CAPTIVE_PORTAL = 17;
    public static final int NET_CAPABILITY_NOT_ROAMING = 18;
    public static final int NET_CAPABILITY_FOREGROUND = 19;
    public static final int NET_CAPABILITY_NOT_CONGESTED = 20;
    public static final int NET_CAPABILITY_NOT_SUSPENDED = 21;
    public static final int NET_CAPABILITY_OEM_PAID = 22;
    public static final int NET_CAPABILITY_MCX = 23;
    public static final int NET_CAPABILITY_PARTIAL_CONNECTIVITY = 24;
    public static final int NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25;
    public static final int NET_CAPABILITY_OEM_PRIVATE = 26;
    public static final int NET_CAPABILITY_VEHICLE_INTERNAL = 27;
    public static final int NET_CAPABILITY_NOT_VCN_MANAGED = 28;
    public static final int NET_CAPABILITY_ENTERPRISE = 29;
    public static final int NET_CAPABILITY_VSIM = 30;
    public static final int NET_CAPABILITY_BIP = 31;
    public static final int NET_CAPABILITY_HEAD_UNIT = 32;
    public static final int NET_CAPABILITY_MMTEL = 33;
    public static final int NET_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35;
    public static final int NET_CAPABILITY_LOCAL_NETWORK = 36;

    public static String getTransportName(int transport) {
        switch (transport) {
            case TRANSPORT_CELLULAR:
                return "CELLULAR";
            case TRANSPORT_WIFI:
                return "WIFI";
            case TRANSPORT_BLUETOOTH:
                return "BLUETOOTH";
            case TRANSPORT_ETHERNET:
                return "ETHERNET";
            case TRANSPORT_VPN:
                return "VPN";
            case TRANSPORT_WIFI_AWARE:
                return "WIFI_AWARE";
            case TRANSPORT_LOWPAN:
                return "LOWPAN";
            case TRANSPORT_TEST:
                return "TEST";
            case TRANSPORT_USB:
                return "USB";
            case TRANSPORT_THREAD:
                return "THREAD";
            case TRANSPORT_SATELLITE:
                return "SATELLITE";
            default:
                return "UNKNOWN(" + transport + ")";
        }
    }

    public static String getCapabilityName(int capability) {
        switch (capability) {
            case NET_CAPABILITY_MMS:
                return "MMS";
            case NET_CAPABILITY_SUPL:
                return "SUPL";
            case NET_CAPABILITY_DUN:
                return "DUN";
            case NET_CAPABILITY_FOTA:
                return "FOTA";
            case NET_CAPABILITY_IMS:
                return "IMS";
            case NET_CAPABILITY_CBS:
                return "CBS";
            case NET_CAPABILITY_WIFI_P2P:
                return "WIFI_P2P";
            case NET_CAPABILITY_IA:
                return "IA";
            case NET_CAPABILITY_RCS:
                return "RCS";
            case NET_CAPABILITY_XCAP:
                return "XCAP";
            case NET_CAPABILITY_EIMS:
                return "EIMS";
            case NET_CAPABILITY_NOT_METERED:
                return "NOT_METERED";
            case NET_CAPABILITY_INTERNET:
                return "INTERNET";
            case NET_CAPABILITY_NOT_RESTRICTED:
                return "NOT_RESTRICTED";
            case NET_CAPABILITY_TRUSTED:
                return "TRUSTED";
            case NET_CAPABILITY_NOT_VPN:
                return "NOT_VPN";
            case NET_CAPABILITY_VALIDATED:
                return "VALIDATED";
            case NET_CAPABILITY_CAPTIVE_PORTAL:
                return "CAPTIVE_PORTAL";
            case NET_CAPABILITY_NOT_ROAMING:
                return "NOT_ROAMING";
            case NET_CAPABILITY_FOREGROUND:
                return "FOREGROUND";
            case NET_CAPABILITY_NOT_CONGESTED:
                return "NOT_CONGESTED";
            case NET_CAPABILITY_NOT_SUSPENDED:
                return "NOT_SUSPENDED";
            case NET_CAPABILITY_OEM_PAID:
                return "OEM_PAID";
            case NET_CAPABILITY_MCX:
                return "MCX";
            case NET_CAPABILITY_PARTIAL_CONNECTIVITY:
                return "PARTIAL_CONNECTIVITY";
            case NET_CAPABILITY_TEMPORARILY_NOT_METERED:
                return "TEMPORARILY_NOT_METERED";
            case NET_CAPABILITY_OEM_PRIVATE:
                return "OEM_PRIVATE";
            case NET_CAPABILITY_VEHICLE_INTERNAL:
                return "VEHICLE_INTERNAL";
            case NET_CAPABILITY_NOT_VCN_MANAGED:
                return "NOT_VCN_MANAGED";
            case NET_CAPABILITY_ENTERPRISE:
                return "ENTERPRISE";
            case NET_CAPABILITY_VSIM:
                return "VSIM";
            case NET_CAPABILITY_BIP:
                return "BIP";
            case NET_CAPABILITY_HEAD_UNIT:
                return "HEAD_UNIT";
            case NET_CAPABILITY_MMTEL:
                return "MMTEL";
            case NET_CAPABILITY_PRIORITIZE_LATENCY:
                return "PRIORITIZE_LATENCY";
            case NET_CAPABILITY_PRIORITIZE_BANDWIDTH:
                return "PRIORITIZE_BANDWIDTH";
            case NET_CAPABILITY_LOCAL_NETWORK:
                return "LOCAL_NETWORK";
            default:
                return "UNKNOWN(" + capability + ")";
        }
    }
}