#!/system/bin/sh
# NetSpoof — post-fs-data.sh
# Runs early in boot (before any app). Applies resetprop overrides immediately.

MODDIR="${0%/*}"

resetprop_if_needed() {
  local key="$1" val="$2"
  local cur
  cur="$(resetprop "$key" 2>/dev/null)"
  [ "$cur" != "$val" ] && resetprop "$key" "$val"
}

# ── Verified boot ────────────────────────────────────────────
resetprop_if_needed ro.boot.verifiedbootstate green
resetprop_if_needed ro.boot.veritymode        enforcing
resetprop_if_needed ro.boot.vbmeta.device_state locked
resetprop_if_needed ro.boot.flash.locked       1

# ── Build tags ───────────────────────────────────────────────
resetprop_if_needed ro.build.tags  release-keys
resetprop_if_needed ro.build.type  user
resetprop_if_needed ro.debuggable  0
resetprop_if_needed ro.secure      1

# ── Warranty / OEM ───────────────────────────────────────────
resetprop_if_needed ro.warranty_bit          0
resetprop_if_needed ro.boot.warranty_bit     0
resetprop_if_needed sys.oem_unlock_allowed   0
resetprop_if_needed ro.oem_unlock_supported  0

# ── SELinux ──────────────────────────────────────────────────
resetprop_if_needed ro.build.selinux 1
