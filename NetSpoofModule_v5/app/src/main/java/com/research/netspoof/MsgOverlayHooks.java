package com.research.netspoof;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import de.robv.android.xposed.*;

/** Shows custom security message as Toast inside target app on each screen open */
public final class MsgOverlayHooks {

    public static void apply(ClassLoader cl, final String message) {
        if (message == null || message.trim().isEmpty()) return;
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        final Activity act = (Activity) p.thisObject;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try { Toast.makeText(act, message, Toast.LENGTH_LONG).show(); }
                            catch (Throwable ignored) {}
                        }, 500);
                    }
                });
            XposedBridge.log("[NSv5:Msg] Overlay active: " + message);
        } catch (Throwable t) {
            XposedBridge.log("[NSv5:Msg] hook failed: " + t.getMessage());
        }
    }
}
