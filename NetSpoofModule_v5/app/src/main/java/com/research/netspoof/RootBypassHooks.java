package com.research.netspoof;

import java.io.*;
import java.util.*;
import de.robv.android.xposed.*;

/**
 * Comprehensive root detection bypass:
 * File system, PackageManager, Runtime.exec, SystemProperties,
 * Build fields, SELinux, /proc/mounts, /proc/net/unix
 */
public final class RootBypassHooks {

    private static final Set<String> ROOT_PATHS = new HashSet<>(Arrays.asList(
        "/su", "/system/bin/su", "/sbin/su", "/system/xbin/su",
        "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su",
        "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
        "/system/app/KingoUser.apk",
        "/system/etc/init.d/99SuperSUDaemon",
        "/system/bin/.ext/.su", "/system/usr/we-need-root",
        "/.magisk", "/sbin/.magisk.unblock",
        "/sbin/.core/mirror", "/sbin/.core/img",
        "/data/adb/magisk", "/data/adb/magisk.img",
        "/cache/magisk.log", "/data/cache/magisk.log"
    ));

    private static final Set<String> ROOT_PKGS = new HashSet<>(Arrays.asList(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.zachspong.temprootremovejb",
        "com.yellowes.su",
        "com.kingouser.com",
        "io.github.vvb2060.magisk",
        "com.fox2code.mmm",
        "io.github.sukisu.manager",
        "org.lsposed.manager",
        "io.github.lsposed.manager",
        "com.rovo98.magiskhide",
        "com.dimonvideo.luckypatcher"
    ));

    private static final String[] SU_CMDS = {
        "su", "/system/bin/su", "/sbin/su", "/system/xbin/su",
        "which su", "id", "busybox"
    };

    private static final String[] MOUNT_DENY = {
        "magisk", ".core", "mirror", "tmpfs /sbin"
    };

    public static void apply(ClassLoader cl) {
        hookFile();
        hookPackageManager(cl);
        hookRuntime();
        hookSystemProperties(cl);
        hookBuildFields(cl);
        hookSeLinux(cl);
        hookProcReads();
        XposedBridge.log("[NSv5:Root] All root bypass hooks active");
    }

    // ── 1. File.exists / canExecute / canRead ──────────────────────────────
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

    // ── 2. PackageManager ─────────────────────────────────────────────────
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
                "getPackageInfo", String.class, int.class,  h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", cl,
                "getPackageInfo", String.class, long.class, h); } catch (Throwable ignored) {}
    }

    // ── 3. Runtime.exec ───────────────────────────────────────────────────
    private static void hookRuntime() {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                String cmd = null;
                if (p.args[0] instanceof String)
                    cmd = (String) p.args[0];
                else if (p.args[0] instanceof String[]) {
                    String[] a = (String[]) p.args[0];
                    if (a.length > 0) cmd = a[0];
                }
                if (cmd != null) {
                    String t = cmd.trim();
                    for (String s : SU_CMDS)
                        if (t.equals(s) || t.startsWith("su ")) {
                            p.setResult(Runtime.getRuntime().exec(new String[]{"echo", ""}));
                            return;
                        }
                }
            }
        };
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class,   h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec", String[].class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec",
                String.class,   String[].class, File.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Runtime.class, "exec",
                String[].class, String[].class, File.class, h); } catch (Throwable ignored) {}
    }

    // ── 4. SystemProperties ───────────────────────────────────────────────
    private static void hookSystemProperties(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                String k = (String) p.args[0];
                if (k == null) return;
                switch (k) {
                    case "ro.build.tags":             p.setResult("release-keys"); break;
                    case "ro.debuggable":             p.setResult("0");            break;
                    case "ro.secure":                 p.setResult("1");            break;
                    case "ro.build.selinux":          p.setResult("1");            break;
                    case "ro.boot.verifiedbootstate": p.setResult("green");        break;
                    case "ro.boot.flash.locked":      p.setResult("1");            break;
                    case "ro.build.type":             p.setResult("user");         break;
                }
            }
        };
        try { XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl, "get", String.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl, "get", String.class, String.class, h); } catch (Throwable ignored) {}
    }

    // ── 5. Build fields ───────────────────────────────────────────────────
    private static void hookBuildFields(ClassLoader cl) {
        try {
            Class<?> b = XposedHelpers.findClass("android.os.Build", cl);
            XposedHelpers.setStaticObjectField(b, "TAGS", "release-keys");
            XposedHelpers.setStaticObjectField(b, "TYPE", "user");
        } catch (Throwable ignored) {}
    }

    // ── 6. SELinux — report enforcing ─────────────────────────────────────
    private static void hookSeLinux(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { p.setResult(true); }
        };
        try { XposedHelpers.findAndHookMethod("android.os.SELinux", cl, "isSELinuxEnabled",  h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod("android.os.SELinux", cl, "isSELinuxEnforced", h); } catch (Throwable ignored) {}
    }

    // ── 7. /proc/mounts + /proc/net/unix ──────────────────────────────────
    private static void hookProcReads() {
        try {
            XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        String line = (String) p.getResult();
                        while (line != null && hasMountDeny(line)) {
                            line = (String) XposedBridge.invokeOriginalMethod(
                                    p.method, p.thisObject, p.args);
                        }
                        p.setResult(line);
                    }
                });
        } catch (Throwable ignored) {}
    }

    private static boolean hasMountDeny(String line) {
        String l = line.toLowerCase();
        for (String d : MOUNT_DENY) if (l.contains(d)) return true;
        return false;
    }
}
