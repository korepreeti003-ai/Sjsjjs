package com.research.ambulator;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Shows custom security message as Toast inside target app on every resume */
public final class MsgOverlayHooks {

    public static void apply(ClassLoader cl, final String message) {
        if (message == null || message.trim().isEmpty()) return;
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        final Activity act = (Activity) p.thisObject;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                Toast t = Toast.makeText(act, message, Toast.LENGTH_LONG);
                                t.show();
                            } catch (Throwable ignored) {}
                        }, 600);
                    }
                });
            XposedBridge.log("[AmbulatorSec] Message overlay active: " + message);
        } catch (Throwable t) {
            XposedBridge.log("[AmbulatorSec] msg-hook: " + t.getMessage());
        }
    }
}
