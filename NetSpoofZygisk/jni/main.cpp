// NetSpoof Zygisk native module
// Hooks android.os.SystemProperties.native_get at JNI level so property reads
// from Java always return safe values (release-keys, green verified boot, etc.).
// Native-level system_property_get is already handled by post-fs-data.sh resetprop.

#include "zygisk.hpp"
#include <android/log.h>
#include <sys/system_properties.h>
#include <cstring>
#include <cstdlib>
#include <jni.h>

#define TAG "NSv5:Zygisk"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)

// ── Target packages ──────────────────────────────────────────────────────────
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
    for (int i = 0; TARGET_PKGS[i]; i++)
        if (strstr(name, TARGET_PKGS[i])) return true;
    return false;
}

// ── Property override table ───────────────────────────────────────────────────
struct PropOverride { const char *key; const char *value; };
static const PropOverride OVERRIDES[] = {
    {"ro.boot.verifiedbootstate",    "green"},
    {"ro.boot.veritymode",           "enforcing"},
    {"ro.boot.vbmeta.device_state",  "locked"},
    {"ro.boot.flash.locked",         "1"},
    {"ro.build.tags",                "release-keys"},
    {"ro.build.type",                "user"},
    {"ro.debuggable",                "0"},
    {"ro.secure",                    "1"},
    {"ro.warranty_bit",              "0"},
    {"ro.boot.warranty_bit",         "0"},
    {"sys.oem_unlock_allowed",       "0"},
    {"ro.oem_unlock_supported",      "0"},
    {"ro.crypto.state",              "encrypted"},
    {"ro.boot.vbmeta.hash_alg",      "sha256"},
    {"ro.boot.verifiedboothash",     ""},
    {nullptr, nullptr}
};

static const char *getOverride(const char *key) {
    if (!key) return nullptr;
    for (int i = 0; OVERRIDES[i].key; i++) {
        if (strcmp(key, OVERRIDES[i].key) == 0) return OVERRIDES[i].value;
        // Partial-match for variants (e.g. "verifiedbootstate" in a longer key)
        if (strstr(OVERRIDES[i].key, "verifiedbootstate") && strstr(key, "verifiedbootstate"))
            return "green";
    }
    return nullptr;
}

// ── JNI replacement for SystemProperties.native_get(String) ─────────────────
static jstring jni_native_get(JNIEnv *env, jclass, jstring keyObj) {
    if (!keyObj) return env->NewStringUTF("");
    const char *key = env->GetStringUTFChars(keyObj, nullptr);
    const char *ov  = getOverride(key);
    if (ov) {
        env->ReleaseStringUTFChars(keyObj, key);
        return env->NewStringUTF(ov);
    }
    char value[PROP_VALUE_MAX] = {};
    __system_property_get(key, value);
    env->ReleaseStringUTFChars(keyObj, key);
    return env->NewStringUTF(value);
}

// ── JNI replacement for SystemProperties.native_get(String, String) ─────────
static jstring jni_native_get_default(JNIEnv *env, jclass,
                                       jstring keyObj, jstring defObj) {
    if (!keyObj) return defObj;
    const char *key = env->GetStringUTFChars(keyObj, nullptr);
    const char *ov  = getOverride(key);
    if (ov) {
        env->ReleaseStringUTFChars(keyObj, key);
        return env->NewStringUTF(ov);
    }
    char value[PROP_VALUE_MAX] = {};
    int  len = __system_property_get(key, value);
    env->ReleaseStringUTFChars(keyObj, key);
    return (len > 0) ? env->NewStringUTF(value) : defObj;
}

// ── Zygisk module class ───────────────────────────────────────────────────────
class NetSpoofModule : public zygisk::ModuleBase {
    zygisk::Api *api_ = nullptr;
    JNIEnv      *env_ = nullptr;
    bool         active_ = false;

public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        api_ = api;
        env_ = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        const char *name = nullptr;
        if (args->nice_name)
            name = env_->GetStringUTFChars(args->nice_name, nullptr);
        active_ = isTarget(name);
        if (name) env_->ReleaseStringUTFChars(args->nice_name, name);

        if (!active_) {
            // Unload from non-target processes to avoid overhead
            api_->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!active_) return;
        LOGI("postAppSpecialize: hooking SystemProperties.native_get");

        // Hook android.os.SystemProperties native methods via Zygisk API.
        // This replaces the JNI bridge so any Java call to
        // SystemProperties.get(key) / get(key, def) returns our overrides.
        JNINativeMethod methods[] = {
            {
                "native_get",
                "(Ljava/lang/String;)Ljava/lang/String;",
                reinterpret_cast<void *>(jni_native_get)
            },
            {
                "native_get",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                reinterpret_cast<void *>(jni_native_get_default)
            },
        };

        bool ok = api_->hookJniNativeMethods(
            env_, "android/os/SystemProperties", methods, 2);
        LOGI("SystemProperties hook result: %s", ok ? "OK" : "failed/already hooked");

        // Also register via standard JNI as fallback
        jclass cls = env_->FindClass("android/os/SystemProperties");
        if (cls) {
            env_->RegisterNatives(cls, methods, 2);
            env_->DeleteLocalRef(cls);
        }
    }
};

REGISTER_ZYGISK_MODULE(NetSpoofModule)
