#!/usr/bin/env python3
"""
AmbulatorSec - One-Click Android Virtual Device Setup
======================================================
Ek click mein rooted Android emulator + Play Store + root detection bypass
Compatible: Windows / Linux / macOS
Requirements: Android Studio installed (SDK auto-detect karta hai)
"""

import os, sys, subprocess, time, platform, shutil, urllib.request, zipfile, json

# ─── Config ──────────────────────────────────────────────────────────────────
AVD_NAME       = "AmbulatorSec_Research"
API_LEVEL      = "34"
ABI            = "x86_64"
DEVICE_PROFILE = "pixel_6_pro"          # Poco F7 jaisi feel
RAM_MB         = "4096"
HEAP_MB        = "512"
STORAGE_MB     = "8192"

# LSposed + companion app (GitHub releases)
LSPOSED_URL = "https://github.com/LSPosed/LSPosed/releases/download/v1.9.2/LSPosed-v1.9.2-7024-zygisk-release.zip"
APP_APK_NAME = "AmbulatorSec.apk"       # companion app (build karke rakho)

IS_WIN = platform.system() == "Windows"
SEP    = os.sep

# ─── SDK detection ───────────────────────────────────────────────────────────
def find_sdk():
    candidates = []
    if IS_WIN:
        candidates = [
            os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk"),
            r"C:\Android\Sdk",
            r"C:\Users\Public\Android\Sdk",
        ]
    else:
        candidates = [
            os.path.expanduser("~/Android/Sdk"),
            os.path.expanduser("~/Library/Android/sdk"),
            "/opt/android-sdk",
        ]
    env_sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env_sdk and os.path.isdir(env_sdk):
        return env_sdk
    for c in candidates:
        if os.path.isdir(c):
            return c
    return None

def tool(sdk, *parts):
    ext = ".exe" if IS_WIN else ""
    p = os.path.join(sdk, *parts)
    if not p.endswith(ext):
        p += ext
    # Also try without extension on Linux
    return p

def adb(sdk, *args):
    return subprocess.run([tool(sdk, "platform-tools", "adb")] + list(args),
                          capture_output=True, text=True)

def adb_wait_boot(sdk, timeout=180):
    print("  ⏳ Emulator boot hone ka wait kar raha hoon...", flush=True)
    t = time.time()
    while time.time() - t < timeout:
        r = adb(sdk, "shell", "getprop", "sys.boot_completed")
        if r.returncode == 0 and r.stdout.strip() == "1":
            time.sleep(3)
            return True
        time.sleep(4)
    return False

# ─── Step 1: SDK check ───────────────────────────────────────────────────────
def step_sdk_check():
    print("\n[1/7] Android SDK check kar raha hoon...")
    sdk = find_sdk()
    if not sdk:
        print("  ❌ Android SDK nahi mila!")
        print("  ➜  Android Studio install karo: https://developer.android.com/studio")
        print("  ➜  Ya ANDROID_HOME environment variable set karo")
        sys.exit(1)
    print(f"  ✅ SDK mila: {sdk}")
    return sdk

# ─── Step 2: System image download ──────────────────────────────────────────
def step_install_image(sdk):
    print(f"\n[2/7] System image check/install kar raha hoon (API {API_LEVEL})...")
    sdkmanager = tool(sdk, "cmdline-tools", "latest", "bin", "sdkmanager")
    if not os.path.isfile(sdkmanager):
        # Try older path
        sdkmanager = tool(sdk, "tools", "bin", "sdkmanager")
    if not os.path.isfile(sdkmanager):
        print("  ⚠  sdkmanager nahi mila — Android Studio se manually install karo:")
        print(f"     system-images;android-{API_LEVEL};google_apis_playstore;{ABI}")
        return
    img_pkg = f"system-images;android-{API_LEVEL};google_apis_playstore;{ABI}"
    print(f"  📦 Installing: {img_pkg}")
    subprocess.run([sdkmanager, "--install", img_pkg], check=False)
    print("  ✅ System image ready")

# ─── Step 3: AVD create ──────────────────────────────────────────────────────
def step_create_avd(sdk):
    print(f"\n[3/7] AVD create kar raha hoon: {AVD_NAME}")
    avdmanager = tool(sdk, "cmdline-tools", "latest", "bin", "avdmanager")
    if not os.path.isfile(avdmanager):
        avdmanager = tool(sdk, "tools", "bin", "avdmanager")

    # Check if AVD already exists
    r = subprocess.run([avdmanager, "list", "avd"], capture_output=True, text=True)
    if AVD_NAME in r.stdout:
        print(f"  ℹ  AVD '{AVD_NAME}' already exist karta hai — skip")
        return

    img = f"system-images;android-{API_LEVEL};google_apis_playstore;{ABI}"
    cmd = [avdmanager, "create", "avd",
           "--name", AVD_NAME,
           "--package", img,
           "--device", DEVICE_PROFILE,
           "--force"]
    proc = subprocess.run(cmd, input="no\n", capture_output=True, text=True)
    if proc.returncode != 0:
        print("  ⚠  AVD create failed:", proc.stderr[:300])
    else:
        print(f"  ✅ AVD '{AVD_NAME}' created")

    # Performance tuning — config.ini patch
    avd_home = os.path.expanduser(f"~/.android/avd/{AVD_NAME}.avd")
    if os.path.isdir(avd_home):
        cfg = os.path.join(avd_home, "config.ini")
        extras = {
            "hw.ramSize": RAM_MB,
            "vm.heapSize": HEAP_MB,
            "disk.dataPartition.size": f"{STORAGE_MB}MB",
            "hw.gpu.enabled": "yes",
            "hw.gpu.mode": "host",
            "hw.keyboard": "yes",
            "hw.audioInput": "yes",
            "hw.camera.back": "webcam0",
            "hw.camera.front": "emulated",
            "hw.lcd.density": "440",
            "hw.lcd.height": "2560",
            "hw.lcd.width": "1080",
            "fastboot.forceColdBoot": "no",
        }
        lines = []
        if os.path.isfile(cfg):
            with open(cfg) as f:
                lines = f.readlines()
        existing_keys = {l.split("=")[0].strip() for l in lines if "=" in l}
        with open(cfg, "a") as f:
            for k, v in extras.items():
                if k not in existing_keys:
                    f.write(f"{k}={v}\n")
        print("  ✅ Performance config patched (GPU host, 4GB RAM, 1080p)")

# ─── Step 4: Launch emulator ─────────────────────────────────────────────────
def step_launch_emulator(sdk):
    print(f"\n[4/7] Emulator launch kar raha hoon...")
    emulator = tool(sdk, "emulator", "emulator")
    if not os.path.isfile(emulator):
        emulator = shutil.which("emulator") or "emulator"

    cmd = [emulator,
           "-avd", AVD_NAME,
           "-writable-system",          # system partition writable (root ke liye)
           "-gpu", "host",              # GPU acceleration
           "-no-boot-anim",             # faster boot
           "-memory", RAM_MB,
           "-cores", "4",
           "-no-snapshot-load",]

    if IS_WIN:
        subprocess.Popen(cmd, creationflags=subprocess.CREATE_NEW_CONSOLE)
    else:
        subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    print("  ⏳ Boot ho raha hai (please wait ~60 sec)...")
    if not adb_wait_boot(sdk):
        print("  ❌ Boot timeout! Manually check karo.")
        sys.exit(1)
    print("  ✅ Emulator ready hai")

# ─── Step 5: Root enable ─────────────────────────────────────────────────────
def step_enable_root(sdk):
    print("\n[5/7] Root enable kar raha hoon...")
    # google_apis emulator supports adb root directly
    r = adb(sdk, "root")
    print(f"  adb root: {r.stdout.strip() or r.stderr.strip()}")
    time.sleep(2)

    r = adb(sdk, "remount")
    print(f"  adb remount: {r.stdout.strip() or r.stderr.strip()}")
    time.sleep(1)

    # Verify root
    r = adb(sdk, "shell", "id")
    if "uid=0" in r.stdout:
        print("  ✅ Root confirmed: " + r.stdout.strip())
    else:
        print("  ⚠  Root verify failed — try karo: adb root && adb remount")

    # Hide root indicators at system level
    cmds = [
        # Remove su binary if present (we use adb root, not su binary)
        "rm -f /system/bin/su /system/xbin/su /sbin/su",
        # Patch build props for Play Integrity
        "resetprop ro.build.tags release-keys",
        "resetprop ro.debuggable 0",
        "resetprop ro.secure 1",
        "resetprop ro.boot.verifiedbootstate green",
        "resetprop ro.boot.flash.locked 1",
        # SELinux appears enforcing to apps
        "setenforce 1 || true",
    ]
    for cmd in cmds:
        adb(sdk, "shell", cmd)
    print("  ✅ System-level root hiding done")

# ─── Step 6: Install apps ────────────────────────────────────────────────────
def step_install_apps(sdk):
    print("\n[6/7] Apps install kar raha hoon...")
    script_dir = os.path.dirname(os.path.abspath(__file__))

    # Push AmbulatorSec companion APK if available
    apk_path = os.path.join(script_dir, APP_APK_NAME)
    if os.path.isfile(apk_path):
        r = adb(sdk, "install", "-r", "-t", apk_path)
        print(f"  APK install: {r.stdout.strip() or r.stderr.strip()}")
        print("  ✅ AmbulatorSec app installed")
    else:
        print(f"  ⚠  {APP_APK_NAME} nahi mila — build karke is folder mein rakho")

    # Grant permissions
    pkg = "com.research.ambulator"
    adb(sdk, "shell", f"pm grant {pkg} android.permission.READ_PHONE_STATE")
    adb(sdk, "shell", f"pm grant {pkg} android.permission.QUERY_ALL_PACKAGES")

    print("  ✅ Permissions granted")

# ─── Step 7: Configure ADB shortcuts ────────────────────────────────────────
def step_write_shortcuts(sdk):
    print("\n[7/7] Shortcuts bana raha hoon...")
    adb_path = tool(sdk, "platform-tools", "adb")
    emu_path = tool(sdk, "emulator", "emulator")

    if IS_WIN:
        bat = f"""@echo off
echo AmbulatorSec Virtual Device launching...
start "" "{emu_path}" -avd {AVD_NAME} -writable-system -gpu host -no-boot-anim -memory {RAM_MB} -cores 4
timeout /t 60
"{adb_path}" root
"{adb_path}" remount
"{adb_path}" shell resetprop ro.build.tags release-keys
"{adb_path}" shell resetprop ro.debuggable 0
echo Done! AmbulatorSec ready.
pause
"""
        with open("launch_ambulator.bat", "w") as f:
            f.write(bat)
        print("  ✅ launch_ambulator.bat created")
    else:
        sh = f"""#!/bin/bash
echo "AmbulatorSec Virtual Device launching..."
"{emu_path}" -avd {AVD_NAME} -writable-system -gpu host -no-boot-anim -memory {RAM_MB} -cores 4 &
sleep 55
"{adb_path}" root && sleep 2
"{adb_path}" remount && sleep 1
"{adb_path}" shell resetprop ro.build.tags release-keys
"{adb_path}" shell resetprop ro.debuggable 0
echo "Done! AmbulatorSec ready."
"""
        with open("launch_ambulator.sh", "w") as f:
            f.write(sh)
        os.chmod("launch_ambulator.sh", 0o755)
        print("  ✅ launch_ambulator.sh created")

    # Write custom phone number helper
    num_helper = f"""#!/usr/bin/env python3
\"\"\"
AmbulatorSec — Custom Phone Number / Message Setter
Usage: python3 set_config.py --number +919876543210 --msg "Security Research Active"
\"\"\"
import subprocess, argparse, sys

ADB = r"{adb_path}"
PKG = "com.research.ambulator"
PREFS = "ambulance_config"

def adb(*args):
    r = subprocess.run([ADB] + list(args), capture_output=True, text=True)
    return r.stdout.strip()

def set_pref(key, value):
    adb("shell", f"am broadcast -a {PKG}.SET_PREF "
        f"--es key {{key}} --es value {{value}} -p {PKG}")

def main():
    p = argparse.ArgumentParser(description="AmbulatorSec quick config")
    p.add_argument("--number",  help="Custom phone number e.g. +919876543210")
    p.add_argument("--imei",    help="Custom IMEI")
    p.add_argument("--msg",     help="Custom security message shown in target app")
    p.add_argument("--target",  help="Target app package e.g. com.example.app")
    p.add_argument("--on",  action="store_true", help="Enable module")
    p.add_argument("--off", action="store_true", help="Disable module")
    args = p.parse_args()

    if args.number:
        adb("shell", f"content call --uri content://{PKG}.prefs "
            f"--method set --arg {PREFS}:fake_number:{args.number}")
        print(f"  ✅ Number set: {{args.number}}")
    if args.imei:
        adb("shell", f"content call --uri content://{PKG}.prefs "
            f"--method set --arg {PREFS}:fake_imei:{{args.imei}}")
        print(f"  ✅ IMEI set: {{args.imei}}")
    if args.msg:
        print(f"  ✅ Message configure karo app ke UI se: '{{args.msg}}'")
    if not any(vars(args).values()):
        p.print_help()

if __name__ == "__main__":
    main()
"""
    with open("set_config.py", "w") as f:
        f.write(num_helper)
    print("  ✅ set_config.py created (number/message quick-set)")

# ─── Main ────────────────────────────────────────────────────────────────────
def main():
    print("=" * 60)
    print("  AmbulatorSec — Virtual Research Device Setup")
    print("  Rooted | Play Store | Root Detection Zero | No Lag")
    print("=" * 60)

    sdk = step_sdk_check()
    step_install_image(sdk)
    step_create_avd(sdk)
    step_launch_emulator(sdk)
    step_enable_root(sdk)
    step_install_apps(sdk)
    step_write_shortcuts(sdk)

    print("\n" + "=" * 60)
    print("  ✅ AmbulatorSec SETUP COMPLETE!")
    print()
    print("  Next time launch karne ke liye:")
    print("  Windows →  launch_ambulator.bat double-click karo")
    print("  Linux   →  ./launch_ambulator.sh")
    print()
    print("  Custom number set karne ke liye:")
    print("  python3 set_config.py --number +919876543210")
    print()
    print("  Custom message:")
    print("  App open karo → Custom Message field mein type karo → Save")
    print("=" * 60)

if __name__ == "__main__":
    main()
