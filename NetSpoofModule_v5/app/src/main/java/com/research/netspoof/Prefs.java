package com.research.netspoof;

public final class Prefs {
    public static final String FILE = "netspoof_config";

    // Master
    public static final String KEY_ENABLED      = "enabled";
    public static final String KEY_TARGET_PKG   = "target_pkg";
    public static final String KEY_TARGET_NAME  = "target_name";

    // Feature toggles
    public static final String KEY_ROOT_BYPASS  = "root_bypass";   // root detection hide
    public static final String KEY_XPOSED_HIDE  = "xposed_hide";   // Xposed/Zygisk/LSposed hide
    public static final String KEY_PHONE_SPOOF  = "phone_spoof";   // phone identity spoof
    public static final String KEY_SHOW_MSG     = "show_msg";      // custom overlay message

    // Phone spoof values
    public static final String KEY_NUMBER       = "fake_number";
    public static final String KEY_IMSI         = "fake_imsi";
    public static final String KEY_OPERATOR     = "fake_operator";
    public static final String KEY_SIM_OP       = "fake_sim_op";
    public static final String KEY_ICCID        = "fake_iccid";
    public static final String KEY_COUNTRY      = "fake_country";
    public static final String KEY_MCC_MNC      = "fake_mcc_mnc";
    public static final String KEY_ANDROID_ID   = "fake_android_id";
    public static final String KEY_IMEI         = "fake_imei";

    // Custom overlay message
    public static final String KEY_CUSTOM_MSG   = "custom_msg";

    // Defaults — India Airtel
    public static final String DEF_NUMBER       = "+919876543210";
    public static final String DEF_IMSI         = "404101234567890";
    public static final String DEF_OPERATOR     = "Airtel";
    public static final String DEF_SIM_OP       = "Airtel";
    public static final String DEF_ICCID        = "89910123456789012345";
    public static final String DEF_COUNTRY      = "in";
    public static final String DEF_MCC_MNC      = "40410";
    public static final String DEF_ANDROID_ID   = "a1b2c3d4e5f6a7b8";
    public static final String DEF_IMEI         = "352000001234567";
    public static final String DEF_CUSTOM_MSG   = "🔒 Security Analysis Active";

    private Prefs() {}
}
