#!/system/bin/sh
# NetSpoof — service.sh
# Runs after system is booted (late_start). Handles:
#   1. Re-apply resetprop in case anything changed them
#   2. Auto-add NetSpoof APK to LSposed scope (silently, no crash)
#   3. Configure Magisk DenyList for Paytm/PhonePe

MODDIR="${0%/*}"
NETSPOOF_PKG="com.research.netspoof"
TARGET_PKGS="net.one97.paytm com.phonepe.app com.paytmbank in.org.npci.upiapp"

# ── 1. Re-apply verified boot props (in case Play Services reset them) ──
resetprop ro.boot.verifiedbootstate green
resetprop ro.boot.veritymode        enforcing
resetprop ro.boot.vbmeta.device_state locked
resetprop ro.boot.flash.locked       1
resetprop ro.build.tags              release-keys
resetprop ro.build.type              user
resetprop ro.debuggable              0
resetprop ro.secure                  1
resetprop ro.warranty_bit            0
resetprop ro.boot.warranty_bit       0
resetprop sys.oem_unlock_allowed     0

# ── 2. Auto-add to LSposed scope via SQLite ─────────────────────────────
# LSposed stores scope in /data/adb/lspd/config/modules.db
# We add the module (com.research.netspoof) scoped to each target package.
add_lsposed_scope() {
  local db="/data/adb/lspd/config/modules.db"
  [ -f "$db" ] || return

  for pkg in $TARGET_PKGS; do
    # Check if scope entry already exists
    local exists
    exists=$(sqlite3 "$db" \
      "SELECT COUNT(*) FROM scope WHERE module_pkg_name='$NETSPOOF_PKG' AND app_pkg_name='$pkg';" \
      2>/dev/null)
    if [ "$exists" = "0" ] || [ -z "$exists" ]; then
      sqlite3 "$db" \
        "INSERT OR IGNORE INTO scope (module_pkg_name, app_pkg_name, user_id) VALUES ('$NETSPOOF_PKG','$pkg',0);" \
        2>/dev/null
    fi
  done

  # Also ensure module is enabled in modules table
  sqlite3 "$db" \
    "INSERT OR IGNORE INTO modules (module_pkg_name, enabled) VALUES ('$NETSPOOF_PKG', 1);" \
    2>/dev/null
  sqlite3 "$db" \
    "UPDATE modules SET enabled=1 WHERE module_pkg_name='$NETSPOOF_PKG';" \
    2>/dev/null
}

# Try both Magisk/KernelSU LSposed paths
add_lsposed_scope
# Alternative path for newer LSposed builds
LSPOSED_DB2="/data/adb/modules/zygisk_lsposed/config/modules.db"
[ -f "$LSPOSED_DB2" ] && db="$LSPOSED_DB2" && add_lsposed_scope

# ── 3. Configure Magisk DenyList for target apps ────────────────────────
# Ensures Paytm/PhonePe are in DenyList so Magisk hides root from them
if command -v magisk >/dev/null 2>&1; then
  magisk --sqlite "SELECT 1;" >/dev/null 2>&1 && {
    for pkg in $TARGET_PKGS; do
      magisk --denylist add "$pkg" 2>/dev/null || true
    done
  }
fi

# ── 4. Fix SELinux context on NetSpoof data dirs ─────────────────────────
chcon -R u:object_r:system_data_file:s0 \
  "/data/data/$NETSPOOF_PKG" 2>/dev/null || true
