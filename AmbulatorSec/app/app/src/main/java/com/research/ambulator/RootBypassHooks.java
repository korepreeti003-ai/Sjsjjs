package com.research.ambulator;

import java.io.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Comprehensive root detection bypass — File, PM, Runtime, Build, SELinux, /proc */
public final class RootBypassHooks {

    private static final Set<String> ROOT_PATHS = new HashSet<>(Arrays.asList(
        "/su", "/system/bin/su", "/sbin/su", "/system/xbin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
        "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
        "/system/etc/init.d/99SuperSUDaemon", "/system/bin/.ext/.su",
        "/.magisk", "/sbin/.magisk.unblock", "/sbin/.core/mirror",
        "/data/adb/magisk", "/data/adb/magisk.img", "/cache/magisk.log"
    ));

    private static final Set<String> ROOT_PKGS = new HashSet<>(Arrays.asList(
        "com.topjohnwu.magisk", "eu.chainfire.supersu",
        "com.noshufou.android.su", "com.koushikdutta.superuser",
        "com.yellowes.su", "com.kingouser.com",
        "io.github.vvb2060.magisk", "com.fox2code.mmm",
        "io.github.sukisu.manager", "org.lsposed.manager",
        "io.github.lsposed.manager", "com.rovo98.magiskhide"
    ));

    private static final String[] MOUNT_DENY = {"magisk", ".core", "mirror", "tmpfs /sbin"};

    public static void apply(ClassLoader cl) {
        hookFile();
        hookPackageManager(cl);
        hookRuntime();
        hookSystemProperties(cl);
        hookBuildFields(cl);
        hookSeLinux(cl);
        hookProcMounts();
        XposedBridge.log("[AmbulatorSec] Root bypass active — all hooks injected");
    }

    // 1. File.exists / canExecute / canRead
    private static void hookFile() {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                File f = (File) p.thisObject;
                if (f != null && ROOT_PATHS.contains(f.getAbsolutePath()))
                    p.setResult(false);
            }
        };
        try { XposedHelpers.findAndHookMethod(File.class, "exists",     h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(File.class, "canExecute", h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(File.class, "canRead",    h); } catch (Throwable ignored) {}
    }

    // 2. PackageManager — root apps hide karo
    private static void hookPackageManager(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (ROOT_PKGS.contains((String) p.args[0]))
                    p.setThrowable(new android.content.pm.PackageManager
                            .NameNotFoundException((String) p.args[0]));
            }
        };
        try { XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", cl,
                "getPackageInfo", String.class, int.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", cl,
                "getPackageInfo", String.class, long.class, h); } catch (Throwable ignored) {}
    }

    // 3. Runtime.exec — su commands block karo
    private static void hookRuntime() {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                String cmd = null;
                if (p.args[0] instanceof String)   cmd = (String)   p.args[0];
                else if (p.args[0] instanceof String[]) {
                    String[] a = (String[]) p.args[0];
                    if (a.length > 0) cmd = a[0];
                }
                if (cmd != null) {
                    String t = cmd.trim();
                    if (t.equals("su") || t.equals("/system/bin/su") ||
                        t.equals("/sbin/su") || t.startsWith("su ") ||
                        t.equals("which su") || t.equals("id")) {
                        p.setResult(Runtime.getRuntime().exec(new String[]{"echo", ""}));
                    }
                }
            }
        };
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class,   h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec", String[].class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec",
                String.class, String[].class, File.class, h); }  catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec",
                String[].class, String[].class, File.class, h); } catch (Throwable ignored) {}
    }

    // 4. SystemProperties patch
    private static void hookSystemProperties(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                String k = (String) p.args[0];
                if ("ro.build.tags".equals(k))            p.setResult("release-keys");
                else if ("ro.debuggable".equals(k))       p.setResult("0");
                else if ("ro.secure".equals(k))           p.setResult("1");
                else if ("ro.boot.verifiedbootstate".equals(k)) p.setResult("green");
                else if ("ro.boot.flash.locked".equals(k)) p.setResult("1");
            }
        };
        try { XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl, "get", String.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl, "get", String.class, String.class, h); } catch (Throwable ignored) {}
    }

    // 5. Build fields
    private static void hookBuildFields(ClassLoader cl) {
        try {
            Class<?> b = XposedHelpers.findClass("android.os.Build", cl);
            XposedHelpers.setStaticObjectField(b, "TAGS", "release-keys");
            XposedHelpers.setStaticObjectField(b, "TYPE", "user");
        } catch (Throwable ignored) {}
    }

    // 6. SELinux — enforcing dikhao chahe permissive ho
    private static void hookSeLinux(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(true); }
        };
        try { XposedHelpers.findAndHookMethod("android.os.SELinux", cl, "isSELinuxEnabled",  h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod("android.os.SELinux", cl, "isSELinuxEnforced", h); } catch (Throwable ignored) {}
    }

    // 7. /proc/mounts se magisk lines chhupao
    private static void hookProcMounts() {
        try {
            XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        String line = (String) p.getResult();
                        if (line == null) return;
                        for (String d : MOUNT_DENY) {
                            if (line.contains(d)) {
                                p.setResult(XposedBridge.invokeOriginalMethod(
                                        p.method, p.thisObject, p.args));
                                return;
                            }
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }
}
