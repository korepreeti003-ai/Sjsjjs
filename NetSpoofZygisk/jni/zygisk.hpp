// Zygisk API v4 header (minimal subset needed for our module)
// Source: https://github.com/topjohnwu/zygisk-next/blob/master/include/zygisk.hpp
#pragma once

#include <jni.h>
#include <cstdint>
#include <functional>

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

#define REGISTER_ZYGISK_MODULE(clazz) \
    static zygisk::ModuleBase* _ns_createModule() { return new clazz(); } \
    extern "C" __attribute__((visibility("default"))) \
    void zygisk_module_entry(zygisk::Api *api, JNIEnv *env) { \
        auto *mod = _ns_createModule(); \
        mod->onLoad(api, env); \
    }

struct Api {
    void *impl;
    void *(*getModuleDir)(Api *);
    int  (*connectCompanion)(Api *);
    void (*setOption)(Api *, int);
    void (*hookJniNativeMethods)(Api *, JNIEnv *, const char *, struct JNINativeMethod *, int);
    void (*pltHookRegister)(Api *, const char *, const char *, void **, void *);
    void (*pltHookCommit)(Api *);
    void (*exemptFd)(Api *, int);

    inline void setOption(int opt) { setOption(this, opt); }
    inline void hookJniNativeMethods(JNIEnv *env, const char *cls, JNINativeMethod *methods, int cnt) {
        hookJniNativeMethods(this, env, cls, methods, cnt);
    }
    inline void pltHookRegister(const char *lib, const char *sym, void **fn, void *orig) {
        pltHookRegister(this, lib, sym, fn, orig);
    }
    inline void pltHookCommit() { pltHookCommit(this); }
    inline void exemptFd(int fd) { exemptFd(this, fd); }
};

// Option flags
enum Option : int {
    FORCE_DENYLIST_UNMOUNT = 0,
    DLCLOSE_MODULE_LIBRARY = 1,
};

struct AppSpecializeArgs {
    jint &uid;
    jint &gid;
    jintArray &gids;
    jint &runtime_flags;
    jint &mount_external;
    jstring &se_info;
    jstring &nice_name;
    jstring &instruction_set;
    jstring &app_data_dir;
    jboolean *const is_child_zygote;
    jboolean *const is_top_app;
    jobjectArray *const pkg_data_info_list;
    jobjectArray *const whitelisted_data_info_list;
    jboolean *const bind_mount_app_data_dirs;
    jboolean *const bind_mount_app_storage_dirs;
};

struct ServerSpecializeArgs {
    jint &uid;
    jint &gid;
    jintArray &gids;
    jint &runtime_flags;
    jint &permitted_capabilities;
    jint &effective_capabilities;
};

}  // namespace zygisk
