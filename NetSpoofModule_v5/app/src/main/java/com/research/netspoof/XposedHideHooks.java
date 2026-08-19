package com.research.netspoof;

import java.io.*;
import java.util.*;
import de.robv.android.xposed.*;

/**
 * System-level Xposed / LSposed / Zygisk self-concealment.
 *
 * Detection methods neutralised:
 *  1. ClassLoader.loadClass — XposedBridge class not found
 *  2. Class.forName          — same
 *  3. Thread.getStackTrace   — Xposed frames stripped
 *  4. Throwable.getStackTrace— Xposed frames stripped
 *  5. /proc/self/maps        — lsplant / zygisk / magisk entries hidden
 *  6. /proc/net/unix         — Magisk/Zygisk socket entries hidden
 *  7. Reflection on XposedBridge — NameNotFoundException raised
 *  8. System.getenv("JAVA_TOOL_OPTIONS") — stripped (Xposed injects this)
 */
public final class XposedHideHooks {

    // Fully-qualified Xposed / LSposed / EdXposed class names to hide
    private static final Set<String> XPOSED_CLASSES = new HashSet<>(Arrays.asList(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.XC_MethodHook",
        "de.robv.android.xposed.XC_MethodReplacement",
        "de.robv.android.xposed.IXposedHookLoadPackage",
        "de.robv.android.xposed.callbacks.XC_LoadPackage",
        "io.github.lsposed.lspd.core.Main",
        "org.lsposed.lspd.core.Main",
        "me.weishu.exposed.ExposedKernel",
        "com.elderdrivers.riru.edxp.core.Main"
    ));

    // Substrings that should NOT appear in stack frames or /proc/maps lines
    private static final String[] FRAME_DENY = {
        "de.robv.android.xposed",
        "io.github.lsposed",
        "org.lsposed",
        "me.weishu.exposed",
        "zygisk",
        "magisk",
        "lsplant",
        "edxp",
        "riru"
    };

    // Additional deny list specifically for /proc/maps and /proc/net/unix
    private static final String[] MAP_DENY = {
        "xposed", "lspd", "lsplant", "zygisk", "magisk",
        "/dev/socket/zygote_secondary",
        "@/dev/socket/zygote"
    };

    public static void apply(ClassLoader cl) {
        hookClassLoader(cl);
        hookForName(cl);
        hookStackTrace();
        hookProcMaps();
        hookEnv();
        XposedBridge.log("[NSv5:XposedHide] Self-concealment hooks active");
    }

    // ── 1. ClassLoader.loadClass → throw for Xposed classes ──────────────
    private static void hookClassLoader(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (XPOSED_CLASSES.contains((String) p.args[0]))
                    p.setThrowable(new ClassNotFoundException((String) p.args[0]));
            }
        };
        try { XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                String.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                String.class, boolean.class, h); } catch (Throwable ignored) {}
    }

    // ── 2. Class.forName → throw for Xposed classes ───────────────────────
    private static void hookForName(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String name = (String) p.args[0];
                if (name != null && XPOSED_CLASSES.contains(name))
                    p.setThrowable(new ClassNotFoundException(name));
            }
        };
        try { XposedHelpers.findAndHookMethod(Class.class, "forName",
                String.class, h); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Class.class, "forName",
                String.class, boolean.class, ClassLoader.class, h); } catch (Throwable ignored) {}
    }

    // ── 3 & 4. Strip Xposed frames from stack traces ──────────────────────
    private static void hookStackTrace() {
        XC_MethodHook strip = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                StackTraceElement[] trace = (StackTraceElement[]) p.getResult();
                if (trace == null || trace.length == 0) return;
                List<StackTraceElement> clean = new ArrayList<>(trace.length);
                for (StackTraceElement e : trace) {
                    if (!frameDenied(e.getClassName())) clean.add(e);
                }
                if (clean.size() != trace.length)
                    p.setResult(clean.toArray(new StackTraceElement[0]));
            }
        };
        try { XposedHelpers.findAndHookMethod(Thread.class,    "getStackTrace", strip); } catch (Throwable ignored) {}
        try { XposedHelpers.findAndHookMethod(Throwable.class, "getStackTrace", strip); } catch (Throwable ignored) {}

        // Also hook Throwable.getOurStackTrace (internal, used on some ROMs)
        try { XposedHelpers.findAndHookMethod(Throwable.class, "getOurStackTrace", strip); } catch (Throwable ignored) {}
    }

    // ── 5 & 6. /proc/self/maps + /proc/net/unix line-by-line filtering ────
    private static void hookProcMaps() {
        try {
            XposedHelpers.findAndHookMethod(BufferedReader.class, "readLine",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        String line = (String) p.getResult();
                        while (line != null && mapLineDenied(line)) {
                            line = (String) XposedBridge.invokeOriginalMethod(
                                    p.method, p.thisObject, p.args);
                        }
                        p.setResult(line);
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── 7. System.getenv — strip JAVA_TOOL_OPTIONS injected by Xposed ─────
    private static void hookEnv() {
        try {
            XposedHelpers.findAndHookMethod(System.class, "getenv", String.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        String key = (String) p.args[0];
                        if ("JAVA_TOOL_OPTIONS".equals(key) || "_JAVA_OPTIONS".equals(key))
                            p.setResult(null);
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private static boolean frameDenied(String className) {
        if (className == null) return false;
        String lower = className.toLowerCase();
        for (String d : FRAME_DENY) if (lower.contains(d)) return true;
        return false;
    }

    private static boolean mapLineDenied(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase();
        for (String d : MAP_DENY) if (lower.contains(d)) return true;
        return false;
    }
}
