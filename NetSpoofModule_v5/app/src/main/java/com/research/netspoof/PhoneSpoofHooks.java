package com.research.netspoof;

import de.robv.android.xposed.*;

/** Phone number, IMSI, IMEI, carrier, Android ID, MAC, network type spoof */
public final class PhoneSpoofHooks {

    public static void apply(ClassLoader cl, XSharedPreferences p) {
        String num  = p.getString(Prefs.KEY_NUMBER,     Prefs.DEF_NUMBER);
        String imsi = p.getString(Prefs.KEY_IMSI,       Prefs.DEF_IMSI);
        String op   = p.getString(Prefs.KEY_OPERATOR,   Prefs.DEF_OPERATOR);
        String sop  = p.getString(Prefs.KEY_SIM_OP,     Prefs.DEF_SIM_OP);
        String icc  = p.getString(Prefs.KEY_ICCID,      Prefs.DEF_ICCID);
        String ctry = p.getString(Prefs.KEY_COUNTRY,    Prefs.DEF_COUNTRY);
        String mcc  = p.getString(Prefs.KEY_MCC_MNC,    Prefs.DEF_MCC_MNC);
        String aid  = p.getString(Prefs.KEY_ANDROID_ID, Prefs.DEF_ANDROID_ID);
        String imei = p.getString(Prefs.KEY_IMEI,       Prefs.DEF_IMEI);

        hookTelephony(cl, num, imsi, op, sop, icc, ctry, mcc, imei);
        hookAndroidId(cl, aid);
        hookWifi(cl);
        XposedBridge.log("[NSv5:Phone] Spoof active — number=" + num + " operator=" + op);
    }

    private static void hookTelephony(ClassLoader cl,
            String num, String imsi, String op, String sop,
            String icc, String ctry, String mcc, String imei) {

        h(cl, "android.telephony.TelephonyManager", "getLine1Number",         num);
        h(cl, "android.telephony.TelephonyManager", "getSubscriberId",        imsi);
        h(cl, "android.telephony.TelephonyManager", "getNetworkOperatorName", op);
        h(cl, "android.telephony.TelephonyManager", "getSimOperatorName",     sop);
        h(cl, "android.telephony.TelephonyManager", "getSimSerialNumber",     icc);
        h(cl, "android.telephony.TelephonyManager", "getSimCountryIso",       ctry);
        h(cl, "android.telephony.TelephonyManager", "getNetworkCountryIso",   ctry);
        h(cl, "android.telephony.TelephonyManager", "getNetworkOperator",     mcc);
        h(cl, "android.telephony.TelephonyManager", "getSimOperator",         mcc);
        h(cl, "android.telephony.TelephonyManager", "getDeviceId",            imei);

        // Network type → LTE (13)
        XC_MethodHook lte = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(13); }
        };
        try { XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl, "getNetworkType",     lte); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl, "getDataNetworkType", lte); } catch (Throwable ignored) {}
    }

    private static void hookAndroidId(ClassLoader cl, final String fakeId) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings$Secure", cl,
                "getString",
                "android.content.ContentResolver", String.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if ("android_id".equals(p.args[1])) p.setResult(fakeId);
                    }
                });
        } catch (Throwable ignored) {}
    }

    private static void hookWifi(ClassLoader cl) {
        h(cl, "android.net.wifi.WifiInfo", "getMacAddress", "02:00:00:00:00:00");
        h(cl, "android.net.wifi.WifiInfo", "getBSSID",      "02:00:00:00:00:00");
    }

    private static void h(ClassLoader cl, String cls, String method, final String val) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(val); }
            });
        } catch (Throwable t) {
            XposedBridge.log("[NSv5:Phone] skip [" + method + "]: " + t.getMessage());
        }
    }
}
