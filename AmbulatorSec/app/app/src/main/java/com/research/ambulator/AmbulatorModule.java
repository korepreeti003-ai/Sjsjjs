package com.research.ambulator;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSposed/Xposed entry point — orchestrates all hooks */
public class AmbulatorModule implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        XSharedPreferences prefs = new XSharedPreferences("com.research.ambulator", Prefs.FILE);
        prefs.makeWorldReadable();

        if (!prefs.getBoolean(Prefs.KEY_ENABLED, true)) return;

        String target = prefs.getString(Prefs.KEY_TARGET_PKG, "");
        if (target.isEmpty() || !lp.packageName.equals(target)) return;

        XposedBridge.log("[AmbulatorSec] Injecting into: " + lp.packageName);
        prefs.reload();

        ClassLoader cl = lp.classLoader;

        // Root detection bypass
        if (prefs.getBoolean(Prefs.KEY_ROOT_BYPASS, true)) {
            RootBypassHooks.apply(cl);
        }

        // Phone number / IMEI spoof
        if (prefs.getBoolean(Prefs.KEY_PHONE_SPOOF, true)) {
            PhoneSpoofHooks.apply(cl, prefs);
        }

        // Custom message overlay
        if (prefs.getBoolean(Prefs.KEY_SHOW_MSG, false)) {
            String msg = prefs.getString(Prefs.KEY_CUSTOM_MSG, Prefs.DEF_CUSTOM_MSG);
            MsgOverlayHooks.apply(cl, msg);
        }
    }
}
