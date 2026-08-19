package com.research.netspoof;

import de.robv.android.xposed.*;
import java.lang.reflect.*;

/**
 * Targeted bypass for Paytm / NPCI UPI / PhonePe specific security classes.
 *
 * Based on analysis of Paytm APK decompilation:
 *
 * Paytm root detection flow:
 *  1. org.npci.upi.security.pinactivitycomponent.Oooo0.OooO0o0()  — combined root check
 *     ├─ OooO00o()  — Build.TAGS "test-keys" check
 *     ├─ OooO0O0()  — su binary file existence check (11 paths)
 *     └─ OooO0OO()  — Runtime.exec("which su") check
 *  2. W8.AbstractC2383i.x()  — Crashlytics root check (Build.TAGS, Superuser.apk, /system/xbin/su)
 *  3. Z8.E (OsData) — isRooted field sent to Crashlytics / Firebase
 *  4. C7696r0 (HawkEye) — sendHawkEyeErrorEvent / sendHawkVerdictLog to backend
 *  5. d8.AbstractC6389c — Build.TAGS "test-keys"/"dev-keys" signature check
 */
public final class FinancialAppBypassHooks {

    private static final XC_MethodHook RETURN_FALSE = new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(false); }
    };

    private static final XC_MethodHook RETURN_NULL = new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(null); }
    };

    private static final XC_MethodHook RETURN_EMPTY_STR = new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(""); }
    };

    private static final XC_MethodHook NO_OP = new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) { p.setResult(null); }
    };

    public static void apply(ClassLoader cl) {
        hookNpciUpiOooo0(cl);
        hookFirebaseCrashlyticsRootCheck(cl);
        hookPaytmOsData(cl);
        hookPaytmHawkEye(cl);
        hookBuildTagsCheck(cl);
        hookGenericRootMethods(cl);
        XposedBridge.log("[NSv5:Fin] Financial app (Paytm/PhonePe/NPCI) bypass hooks active");
    }

    // ── 1. NPCI UPI Oooo0 class — direct method hooks ────────────────────────
    // org.npci.upi.security.pinactivitycomponent.Oooo0
    //   OooO0o0()  → combined check (TAGS || su-files || which-su) → false
    //   OooO00o()  → Build.TAGS test-keys check → false
    //   OooO0O0()  → file existence check → false
    //   OooO0OO()  → Runtime.exec which su → false
    private static void hookNpciUpiOooo0(ClassLoader cl) {
        String cls = "org.npci.upi.security.pinactivitycomponent.Oooo0";
        // Main combined root check
        try { XposedHelpers.findAndHookMethod(cls, cl, "OooO0o0", RETURN_FALSE); } catch (Throwable ignored) {}
        // Build.TAGS "test-keys" check (no args, static boolean)
        try { XposedHelpers.findAndHookMethod(cls, cl, "OooO00o", RETURN_FALSE); } catch (Throwable ignored) {}
        // File-based su binary check
        try { XposedHelpers.findAndHookMethod(cls, cl, "OooO0O0", RETURN_FALSE); } catch (Throwable ignored) {}
        // Runtime.exec "which su" check
        try { XposedHelpers.findAndHookMethod(cls, cl, "OooO0OO", RETURN_FALSE); } catch (Throwable ignored) {}

        // Also hook the instance method that creates the JSON response with isDeviceRooted
        // The method OooO00o(boolean z7) returns JSONObject — we intercept to always set isDeviceRooted=false
        try {
            XposedHelpers.findAndHookMethod(cls, cl, "OooO00o", boolean.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        // Force z7 (isRooted) to false before the method runs
                        // We hook OooO0o0() above, but this catches the result builder too
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        // Replace the returned JSONObject to set isDeviceRooted=false
                        Object result = p.getResult();
                        if (result != null) {
                            try {
                                // org.json.JSONObject.put("isDeviceRooted","false")
                                result.getClass().getMethod("put", String.class, Object.class)
                                    .invoke(result, "isDeviceRooted", "false");
                            } catch (Throwable ignored) {}
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── 2. Firebase/Crashlytics AbstractC2383i root check ────────────────────
    // W8.AbstractC2383i (obfuscated Firebase Crashlytics util)
    //   x() — root check used to construct OsData.isRooted
    //   v() — Debug.isDebuggerConnected() check
    //   w() — emulator check (used in x())
    private static void hookFirebaseCrashlyticsRootCheck(ClassLoader cl) {
        String cls = "W8.AbstractC2383i";
        // x() = main root detection used by OsData
        try { XposedHelpers.findAndHookMethod(cls, cl, "x", RETURN_FALSE); } catch (Throwable ignored) {}
        // v() = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        try { XposedHelpers.findAndHookMethod(cls, cl, "v", RETURN_FALSE); } catch (Throwable ignored) {}
        // w() = emulator check — return false so x() won't think it's an emulator
        try { XposedHelpers.findAndHookMethod(cls, cl, "w", RETURN_FALSE); } catch (Throwable ignored) {}

        // Also try unobfuscated name in case different Paytm version
        for (String name : new String[]{"isRooted","checkRoot","isDeviceRooted","isRootedDevice"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, name, RETURN_FALSE); } catch (Throwable ignored) {}
        }
    }

    // ── 3. OsData (Z8.E / Z8.G.c) — isRooted getter ─────────────────────────
    // Z8.E extends Z8.G.c; b() returns the isRooted boolean field f15528c
    private static void hookPaytmOsData(ClassLoader cl) {
        // Hook b() getter on Z8.E which returns isRooted
        for (String cls : new String[]{"Z8.E", "Z8.G$c", "Z8.G.c"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, "b", RETURN_FALSE); } catch (Throwable ignored) {}
        }

        // Hook the factory method G.c.a(String, String, boolean) — intercept to force isRooted=false
        try {
            XposedHelpers.findAndHookMethod("Z8.G$c", cl, "a",
                String.class, String.class, boolean.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        // Force the boolean (isRooted) argument to false
                        p.args[2] = false;
                    }
                });
        } catch (Throwable ignored) {}

        // Also hook W8.C2390p (the Crashlytics session handler) OsData builder at line 346
        // public final Z8.G.c getOsData() { return G.c.a(Build.VERSION.RELEASE, CODENAME, AbstractC2383i.x()); }
        // We can't hook at line level, but hookFirebaseCrashlyticsRootCheck.x() covers it
    }

    // ── 4. Paytm HawkEye — suppress security event reporting ─────────────────
    // net.one97.paytm.utils.C7696r0 implements com.paytm.integrity.IntegrityHawkeye
    // Suppress sendHawkEyeErrorEvent and sendHawkVerdictLog to prevent backend alerts
    private static void hookPaytmHawkEye(ClassLoader cl) {
        // Hook the interface implementation class
        for (String cls : new String[]{
                "net.one97.paytm.utils.C7696r0",
                "net.one97.paytm.utils.r0"}) {
            try {
                XposedHelpers.findAndHookMethod(cls, cl, "sendHawkEyeErrorEvent",
                    String.class, String.class, String.class, String.class, String.class,
                    int.class, int.class, String.class, NO_OP);
            } catch (Throwable ignored) {}
            try {
                XposedHelpers.findAndHookMethod(cls, cl, "sendHawkVerdictLog",
                    String.class, String.class, String.class, String.class, String.class,
                    String.class, String.class, String.class,
                    int.class, int.class, long.class, long.class, String.class, long.class, NO_OP);
            } catch (Throwable ignored) {}
            // Also hook static helper methods c() and d() that do the actual logging
            try { XposedHelpers.findAndHookMethod(cls, cl, "c",
                    String.class, String.class, String.class, String.class, String.class,
                    int.class, int.class, String.class, NO_OP); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "d",
                    String.class, String.class, String.class, String.class, String.class,
                    String.class, String.class, String.class,
                    int.class, int.class, long.class, long.class, long.class, String.class, NO_OP); } catch (Throwable ignored) {}
        }

        // Also hook the IntegrityHawkeye interface directly
        try {
            XposedHelpers.findAndHookMethod("com.paytm.integrity.IntegrityHawkeye", cl,
                "sendHawkEyeErrorEvent",
                String.class, String.class, String.class, String.class, String.class,
                int.class, int.class, String.class, NO_OP);
        } catch (Throwable ignored) {}
    }

    // ── 5. Build.TAGS signature check in d8.AbstractC6389c ───────────────────
    // Paytm Play Integrity flow checks Build.TAGS for "test-keys"/"dev-keys"
    private static void hookBuildTagsCheck(ClassLoader cl) {
        // Force Build.TAGS to "release-keys" in case RootBypassHooks didn't get there first
        try {
            Class<?> buildCls = XposedHelpers.findClass("android.os.Build", cl);
            Object tags = XposedHelpers.getStaticObjectField(buildCls, "TAGS");
            if (tags instanceof String) {
                String tagsStr = (String) tags;
                if (tagsStr.contains("test-keys") || tagsStr.contains("dev-keys")) {
                    XposedHelpers.setStaticObjectField(buildCls, "TAGS", "release-keys");
                }
            }
        } catch (Throwable ignored) {}

        // Hook d8.AbstractC6389c which does the signature+tags check for Play Integrity
        for (String cls : new String[]{"d8.AbstractC6389c", "d8.C6389c"}) {
            // Hook any boolean method that checks tags
            try {
                Class<?> c = XposedHelpers.findClass(cls, cl);
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getReturnType() == boolean.class && m.getParameterCount() == 0) {
                        XposedBridge.hookMethod(m, RETURN_FALSE);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    // ── 6. Generic root utility methods (common patterns across banking apps) ──
    private static void hookGenericRootMethods(ClassLoader cl) {
        // Common method names used across various banking SDKs
        String[] classes = {
            "com.paytmbank.payments.bank.utils.RootCheckUtil",
            "net.one97.paytm.security.RootDetector",
            "com.phonepe.security.RootDetectionUtil",
            "com.phonepe.sdk.security.RootDetector",
            "com.mobikwik.security.RootChecker",
        };
        String[] methods = {
            "isDeviceRooted", "isRooted", "checkRooted", "isRootedDevice",
            "isDeviceCompromised", "checkRoot", "isRootPresent",
            "rootDetected", "detectRoot", "hasRootAccess"
        };
        for (String cls : classes) {
            for (String method : methods) {
                try {
                    XposedHelpers.findAndHookMethod(cls, cl, method, RETURN_FALSE);
                } catch (Throwable ignored) {}
            }
        }
    }
}
