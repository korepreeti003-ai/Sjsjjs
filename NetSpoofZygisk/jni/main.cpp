// NetSpoof Zygisk native module
// Hooks at native level: system_property_get, __system_property_get,
// and JNI methods used by android.os.SystemProperties / KeyStore attestation.
// Runs before the target app's Java code starts.

#include "zygisk.hpp"
#include <android/log.h>
#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include <jni.h>
#include <string>

#define TAG "NSv5:Zygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

// ── Target packages we hook ─────────────────────────────────────────────────
static const char *TARGET_PKGS[] = {
    "net.one97.paytm",
    "com.phonepe.app",
    "com.paytmbank",
    "in.org.npci.upiapp",
    "com.mobikwik_new",
    "com.freecharge.android",
    nullptr
};

static bool isTarget(const char *name) {
    if (!name) return false;
    for (int i = 0; TARGET_PKGS[i]; i++) {
        if (strstr(name, TARGET_PKGS[i])) return true;
    }
    return false;
}

// ── Property override table ─────────────────────────────────────────────────
struct PropOverride { const char *key; const char *value; };
static const PropOverride PROP_OVERRIDES[] = {
    {"ro.boot.verifiedbootstate",   "green"},
    {"ro.boot.veritymode",          "enforcing"},
    {"ro.boot.vbmeta.device_state", "locked"},
    {"ro.boot.vbmeta.avb_version",  "1.0"},
    {"ro.boot.flash.locked",        "1"},
    {"ro.build.tags",               "release-keys"},
    {"ro.build.type",               "user"},
    {"ro.debuggable",               "0"},
    {"ro.secure",                   "1"},
    {"ro.adb.secure",               "1"},
    {"ro.build.selinux",            "1"},
    {"ro.warranty_bit",             "0"},
    {"ro.boot.warranty_bit",        "0"},
    {"sys.oem_unlock_allowed",      "0"},
    {"ro.oem_unlock_supported",     "0"},
    {"ro.boot.oem_unlock_supported","0"},
    {"ro.crypto.state",             "encrypted"},
    {"ro.crypto.type",              "file"},
    {"ro.boot.vbmeta.hash_alg",     "sha256"},
    {"ro.boot.verifiedboothash",    ""},
    {nullptr, nullptr}
};

static const char *getOverride(const char *key) {
    if (!key) return nullptr;
    for (int i = 0; PROP_OVERRIDES[i].key; i++) {
        if (strcmp(key, PROP_OVERRIDES[i].key) == 0)
            return PROP_OVERRIDES[i].value;
        // Partial match for keys with variable suffixes
        if (strstr(key, "verifiedbootstate") && strstr(PROP_OVERRIDES[i].key, "verifiedbootstate"))
            return PROP_OVERRIDES[i].value;
    }
    return nullptr;
}

// ── PLT hooks — intercept system_property_get / __system_property_get ───────
typedef int (*prop_get_fn)(const char *name, char *value, const char *default_value);
typedef int (*prop_get_fn2)(const char *name, char *value);

static prop_get_fn  orig_system_property_get  = nullptr;
static prop_get_fn2 orig__system_property_get = nullptr;

static int hook_system_property_get(const char *name, char *value, const char *default_value) {
    const char *ov = getOverride(name);
    if (ov) {
        strncpy(value, ov, PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        return (int)strlen(value);
    }
    return orig_system_property_get(name, value, default_value);
}

static int hook__system_property_get(const char *name, char *value) {
    const char *ov = getOverride(name);
    if (ov) {
        strncpy(value, ov, PROP_VALUE_MAX - 1);
        value[PROP_VALUE_MAX - 1] = '\0';
        return (int)strlen(value);
    }
    return orig__system_property_get(name, value);
}

// ── JNI hook — android/os/SystemProperties.native_get ───────────────────────
// Signature: (Ljava/lang/String;)Ljava/lang/String;
static jstring hooked_SystemProperties_native_get(JNIEnv *env, jclass, jstring keyObj) {
    const char *key = env->GetStringUTFChars(keyObj, nullptr);
    const char *ov = getOverride(key);
    if (ov) {
        env->ReleaseStringUTFChars(keyObj, key);
        return env->NewStringUTF(ov);
    }
    // Fall through to real implementation via property_get
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get(key, value);
    env->ReleaseStringUTFChars(keyObj, key);
    return env->NewStringUTF(value);
}

// Signature: (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
static jstring hooked_SystemProperties_native_get_default(JNIEnv *env, jclass,
                                                           jstring keyObj, jstring defObj) {
    const char *key = env->GetStringUTFChars(keyObj, nullptr);
    const char *ov = getOverride(key);
    if (ov) {
        env->ReleaseStringUTFChars(keyObj, key);
        return env->NewStringUTF(ov);
    }
    char value[PROP_VALUE_MAX] = {0};
    int len = __system_property_get(key, value);
    env->ReleaseStringUTFChars(keyObj, key);
    if (len > 0) return env->NewStringUTF(value);
    return defObj;  // return default
}

// ── Zygisk Module ────────────────────────────────────────────────────────────
class NetSpoofModule : public zygisk::ModuleBase {
    zygisk::Api *api_ = nullptr;
    JNIEnv *env_ = nullptr;
    bool    active_ = false;

public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        api_ = api;
        env_ = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        // Read package name from nice_name before app specialization
        const char *name = nullptr;
        if (args->nice_name) {
            name = env_->GetStringUTFChars(args->nice_name, nullptr);
        }
        active_ = isTarget(name);
        if (name) env_->ReleaseStringUTFChars(args->nice_name, name);

        if (!active_) {
            // Unload ourselves for non-target apps to save memory
            api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        LOGI("preAppSpecialize: target detected, applying PLT hooks");

        // Register PLT hooks for native property functions
        api_->pltHookRegister("libc.so", "system_property_get",
                              (void **)&orig_system_property_get,
                              (void *)hook_system_property_get);
        api_->pltHookRegister("libc.so", "__system_property_get",
                              (void **)&orig__system_property_get,
                              (void *)hook__system_property_get);
        // Also hook libandroid_runtime.so which calls property_get directly
        api_->pltHookRegister("libandroid_runtime.so", "system_property_get",
                              (void **)&orig_system_property_get,
                              (void *)hook_system_property_get);
        api_->pltHookCommit();
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!active_) return;
        LOGI("postAppSpecialize: hooking JNI SystemProperties.native_get");

        // Hook android.os.SystemProperties native methods
        JNINativeMethod methods[] = {
            {"native_get",
             "(Ljava/lang/String;)Ljava/lang/String;",
             (void *)hooked_SystemProperties_native_get},
            {"native_get",
             "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
             (void *)hooked_SystemProperties_native_get_default},
        };
        api_->hookJniNativeMethods(env_,
                                   "android/os/SystemProperties",
                                   methods, 2);
        LOGI("postAppSpecialize: all native hooks applied");
    }
};

REGISTER_ZYGISK_MODULE(NetSpoofModule)
