// Zygisk API — simplified header matching Magisk's Zygisk ABI
// Api uses pure virtual methods (correct vtable design); no function-pointer members.
#pragma once

#include <jni.h>

namespace zygisk {

struct Api;
struct AppSpecializeArgs;
struct ServerSpecializeArgs;

class ModuleBase {
public:
    virtual void onLoad(Api *api, JNIEnv *env) {}
    virtual void preAppSpecialize(AppSpecializeArgs *args) {}
    virtual void postAppSpecialize(const AppSpecializeArgs *args) {}
    virtual void preServerSpecialize(ServerSpecializeArgs *args) {}
    virtual void postServerSpecialize(const ServerSpecializeArgs *args) {}
    virtual ~ModuleBase() = default;
};

enum Option : int {
    FORCE_DENYLIST_UNMOUNT = 0,
    DLCLOSE_MODULE_LIBRARY = 1,
};

// Pure-virtual Api — Magisk provides the concrete implementation at runtime.
struct Api {
    virtual void *getModuleDir() = 0;
    virtual void setOption(Option opt) = 0;
    virtual bool exemptFd(int fd) = 0;
    // Re-registers native methods on a Java class (wraps JNIEnv::RegisterNatives).
    virtual bool hookJniNativeMethods(JNIEnv *env, const char *className,
                                      JNINativeMethod *methods, int numMethods) = 0;
    // PLT hook helpers (regex matches loaded-library paths).
    virtual bool pltHookRegister(const char *regex, const char *symbol,
                                 void *newFunc, void **oldFunc) = 0;
    virtual bool pltHookExclude(const char *regex, const char *symbol) = 0;
    virtual bool pltHookCommit() = 0;
};

struct AppSpecializeArgs {
    jint        &uid;
    jint        &gid;
    jintArray   &gids;
    jint        &runtime_flags;
    jint        &mount_external;
    jstring     &se_info;
    jstring     &nice_name;
    jstring     &instruction_set;
    jstring     &app_data_dir;
    jboolean *const is_child_zygote;
    jboolean *const is_top_app;
    jobjectArray *const pkg_data_info_list;
    jobjectArray *const whitelisted_data_info_list;
    jboolean *const bind_mount_app_data_dirs;
    jboolean *const bind_mount_app_storage_dirs;
};

struct ServerSpecializeArgs {
    jint      &uid;
    jint      &gid;
    jintArray &gids;
    jint      &runtime_flags;
    jint      &permitted_capabilities;
    jint      &effective_capabilities;
};

}  // namespace zygisk

// Module entry-point macro — Magisk dlopen()s the .so and calls zygisk_module_entry.
#define REGISTER_ZYGISK_MODULE(clazz)                             \
    extern "C" __attribute__((visibility("default")))             \
    void zygisk_module_entry(zygisk::Api *api, JNIEnv *env) {    \
        auto *m = new clazz();                                    \
        m->onLoad(api, env);                                      \
    }
