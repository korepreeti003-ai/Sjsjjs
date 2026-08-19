#!/sbin/sh
# NetSpoof Zygisk — customize.sh
# SKIPUNZIP=1 tells Magisk to skip auto-extraction; we extract manually below.
SKIPUNZIP=1

ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ui_print "  NetSpoof Zygisk v5.1"
ui_print "  Root / Bootloader Bypass"
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Require Magisk 24+ for Zygisk support
if [ "$MAGISK_VER_CODE" -lt 24000 ]; then
  abort "! Magisk 24+ required for Zygisk support"
fi

# Warn if Zygisk not enabled (system.prop overrides still work)
if [ "$ZYGISK_ENABLED" != "1" ]; then
  ui_print "! WARNING: Zygisk is not enabled in Magisk settings"
  ui_print "! Go to Magisk -> Settings -> Enable Zygisk"
  ui_print "! system.prop overrides will still apply at boot"
fi

# Extract module files
ui_print "- Extracting module files..."
unzip -o "$ZIPFILE" 'system.prop'       -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'service.sh'        -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'post-fs-data.sh'   -d "$MODPATH" >&2
unzip -o "$ZIPFILE" 'zygisk/*'          -d "$MODPATH" >&2

# Set permissions
set_perm "$MODPATH/service.sh"      root root 0755
set_perm "$MODPATH/post-fs-data.sh" root root 0755

ui_print "- System property overrides installed"
ui_print "- Zygisk native library installed"
ui_print ""
ui_print "  After reboot:"
ui_print "  1. Install NetSpoof APK (companion controller)"
ui_print "  2. Open NetSpoof APK -> select Paytm / PhonePe"
ui_print "  3. Enable NetSpoof in LSposed Manager"
ui_print ""
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
