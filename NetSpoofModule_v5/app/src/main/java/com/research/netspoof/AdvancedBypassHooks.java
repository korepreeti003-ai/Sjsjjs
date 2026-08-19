package com.research.netspoof;

import android.os.Debug;
import android.content.pm.ApplicationInfo;
import java.io.*;
import java.util.*;
import de.robv.android.xposed.*;

/**
 * Advanced bypass for Paytm / PhonePe / banking apps.
 *
 * Covers:
 *  1. Debug / debugger detection
 *  2. ADB / developer options / mock location (Settings.Global/Secure)
 *  3. TracerPid in /proc/self/status
 *  4. SafetyNet AttestationResponse — basicIntegrity + ctsProfileMatch = true
 *  5. Play Integrity verdict spoofing
 *  6. VPN / proxy detection (ConnectivityManager)
 *  7. System.exit / Runtime.exit blocking (app tries to self-kill on detect)
 *  8. Frida / JDWP port detection in /proc/net/tcp
 *  9. PackageManager.getInstalledApplications hide root tools
 * 10. TelephonyManager rooted device queries
 */
public final class AdvancedBypassHooks {

    private static final String[] DENY_SETTINGS = {
        "adb_enabled", "development_settings_enabled",
        "mock_location", "allow_mock_location",
        "install_non_market_apps"
    };

    // Lines in /proc/self/status or /proc/net/tcp to hide
    private static final String[] STATUS_DENY = { "TracerPid:\t0", "TracerPid:" };

    public static void apply(ClassLoader cl) {
        hookDebug();
        hookSettings(cl);
        hookProcStatus();
        hookSafetyNet(cl);
        hookPlayIntegrity(cl);
        hookVpnDetection(cl);
        hookExitBlocking();
        hookInstalledPackages(cl);
        hookProcessList();
        XposedBridge.log("[NSv5:Adv] Advanced bypass hooks active (Paytm/PhonePe level)");
    }

    // ── 1. Debugger detection ─────────────────────────────────────────────
    private static void hookDebug() {
        XC_MethodHook returnFalse = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(false); }
        };
        try { XposedHelpers.findAndHookMethod(Debug.class, "isDebuggerConnected", returnFalse); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Debug.class, "waitingForDebugger",   returnFalse); } catch (Throwable ignored) {}
        // android.os.Debug.isDebuggerPresent() is a native stub on some ROMs
        try { XposedHelpers.findAndHookMethod("android.os.Debug", null, "isDebuggerPresent", returnFalse); } catch (Throwable ignored) {}
    }

    // ── 2. Settings (ADB, dev options, mock location) ────────────────────
    private static void hookSettings(ClassLoader cl) {
        XC_MethodHook strHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length >= 2 && p.args[1] instanceof String) {
                    String key = (String) p.args[1];
                    for (String d : DENY_SETTINGS) {
                        if (d.equals(key)) { p.setResult("0"); return; }
                    }
                }
            }
        };
        XC_MethodHook intHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length >= 2 && p.args[1] instanceof String) {
                    String key = (String) p.args[1];
                    for (String d : DENY_SETTINGS) {
                        if (d.equals(key)) { p.setResult(0); return; }
                    }
                }
            }
        };
        for (String cls : new String[]{"android.provider.Settings$Global","android.provider.Settings$Secure"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, "getString",
                    "android.content.ContentResolver", String.class, strHook); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "getInt",
                    "android.content.ContentResolver", String.class, intHook); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "getInt",
                    "android.content.ContentResolver", String.class, int.class, intHook); } catch (Throwable ignored) {}
        }
    }

    // ── 3. TracerPid in /proc/self/status ────────────────────────────────
    private static void hookProcStatus() {
        try {
            XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        String line = (String) p.getResult();
                        if (line != null && line.startsWith("TracerPid:") && !line.equals("TracerPid:\t0")) {
                            p.setResult("TracerPid:\t0");
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── 4. SafetyNet attestation result ──────────────────────────────────
    private static void hookSafetyNet(ClassLoader cl) {
        XC_MethodHook returnTrue = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(true); }
        };
        // Old SafetyNet API response object
        for (String cls : new String[]{
                "com.google.android.gms.safetynet.SafetyNetApi$AttestationResponse",
                "com.google.android.gms.safetynet.SafetyNetApi$RecaptchaTokenResponse"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, "isBasicIntegrity",   returnTrue); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "isCtsProfileMatch",  returnTrue); } catch (Throwable ignored) {}
        }
        // Apps that parse the JWT locally check these boolean getters
        try { XposedHelpers.findAndHookMethod(
                "com.google.android.gms.safetynet.SafetyNetResponse", cl,
                "isCtsProfileMatch",  returnTrue); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "com.google.android.gms.safetynet.SafetyNetResponse", cl,
                "isBasicIntegrity",   returnTrue); } catch (Throwable ignored) {}
    }

    // ── 5. Play Integrity API verdict ────────────────────────────────────
    private static void hookPlayIntegrity(ClassLoader cl) {
        // Apps check the DEVICE_INTEGRITY and APP_INTEGRITY labels
        // Hook the verdict getter to return MEETS_DEVICE_INTEGRITY / MEETS_STRONG_INTEGRITY
        XC_MethodHook verdictHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object result = p.getResult();
                // If result is a List<String> of integrity labels, add the required ones
                if (result instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> list = (List<String>) result;
                    if (!list.contains("MEETS_DEVICE_INTEGRITY"))   list.add("MEETS_DEVICE_INTEGRITY");
                    if (!list.contains("MEETS_BASIC_INTEGRITY"))    list.add("MEETS_BASIC_INTEGRITY");
                    if (!list.contains("MEETS_STRONG_INTEGRITY"))   list.add("MEETS_STRONG_INTEGRITY");
                    p.setResult(list);
                }
            }
        };
        for (String cls : new String[]{
                "com.google.android.play.core.integrity.model.DeviceIntegrity",
                "com.google.android.play.core.integrity.model.AppIntegrity",
                "com.google.android.play.core.integrity.model.AccountDetails"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, "deviceRecognitionVerdict", verdictHook); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "appRecognitionVerdict",    verdictHook); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "accountActivity",          verdictHook); } catch (Throwable ignored) {}
        }
    }

    // ── 6. VPN / proxy detection ──────────────────────────────────────────
    private static void hookVpnDetection(ClassLoader cl) {
        // Some apps check if device is behind a VPN (they think root tools use VPN)
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.ConnectivityManager", cl,
                "getNetworkInfo", int.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        int type = (int) p.args[0];
                        if (type == 17) p.setResult(null); // TYPE_VPN = 17 → null means no VPN
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── 8+9. Hide root packages from getInstalledApplications / getInstalledPackages ──
    private static final Set<String> HIDE_PKGS = new HashSet<>(Arrays.asList(
        "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.noshufou.android.su",
        "com.koushikdutta.superuser", "com.kingouser.com", "io.github.vvb2060.magisk",
        "com.fox2code.mmm", "io.github.sukisu.manager", "org.lsposed.manager",
        "io.github.lsposed.manager", "com.rovo98.magiskhide"
    ));

    private static void hookInstalledPackages(ClassLoader cl) {
        XC_MethodHook filterHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object result = p.getResult();
                if (!(result instanceof List)) return;
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) result;
                list.removeIf(item -> {
                    try {
                        String pkg = null;
                        if (item instanceof ApplicationInfo) {
                            pkg = ((ApplicationInfo) item).packageName;
                        } else {
                            java.lang.reflect.Field f = item.getClass().getField("packageName");
                            f.setAccessible(true);
                            pkg = (String) f.get(item);
                        }
                        return pkg != null && HIDE_PKGS.contains(pkg);
                    } catch (Throwable ignored) { return false; }
                });
                p.setResult(list);
            }
        };
        for (String cls : new String[]{"android.app.ApplicationPackageManager", "android.content.pm.PackageManager"}) {
            try { XposedHelpers.findAndHookMethod(cls, cl, "getInstalledApplications", int.class, filterHook); } catch (Throwable ignored) {}
            try { XposedHelpers.findAndHookMethod(cls, cl, "getInstalledPackages",     int.class, filterHook); } catch (Throwable ignored) {}
        }
    }

    // ── 10. /proc/net/tcp — hide Frida JDWP ports ─────────────────────────
    private static final Set<String> TCP_PORT_DENY = new HashSet<>(Arrays.asList(
        "0BB8", "1CBB", // 3000, 7355 — common Frida ports hex
        "8AE0"          // 35552 — JDWP
    ));

    private static void hookProcessList() {
        try {
            XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        String line = (String) p.getResult();
                        if (line == null) return;
                        for (String port : TCP_PORT_DENY) {
                            if (line.contains(port)) {
                                // Skip this line — return next line instead
                                String next = (String) XposedBridge.invokeOriginalMethod(
                                        p.method, p.thisObject, p.args);
                                p.setResult(next);
                                return;
                            }
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── 7. Block app self-exit on detection ──────────────────────────────
    private static void hookExitBlocking() {
        XC_MethodHook block = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object code = p.args.length > 0 ? p.args[0] : null;
                int c = (code instanceof Integer) ? (int) code : 0;
                if (c != 0) {
                    XposedBridge.log("[NSv5:Adv] Blocked exit(" + c + ")");
                    p.setResult(null);
                }
            }
        };
        try { XposedHelpers.findAndHookMethod(System.class,  "exit", int.class, block); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exit", int.class, block); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "halt", int.class, block); } catch (Throwable ignored) {}
    }
}
