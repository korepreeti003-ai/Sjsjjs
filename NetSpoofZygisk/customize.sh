#!/sbin/sh
# NetSpoof Zygisk — customize.sh (runs during Magisk flash)

print_modname() {
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  ui_print "  NetSpoof Zygisk v5.1"
  ui_print "  Root / Bootloader Bypass"
  ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Check Magisk version (needs Zygisk support = 24+)
if [ "$MAGISK_VER_CODE" -lt 24000 ]; then
  abort "! Magisk 24+ required for Zygisk support"
fi

# Zygisk must be enabled
if [ "$ZYGISK_ENABLED" != "1" ]; then
  ui_print "! WARNING: Zygisk is not enabled in Magisk settings"
  ui_print "! Go to Magisk → Settings → Enable Zygisk"
  ui_print "! The system.prop overrides still work without Zygisk"
fi

ui_print "- Installing system property overrides..."
ui_print "- Installing Zygisk native library..."
ui_print ""
ui_print "  After reboot:"
ui_print "  1. Install NetSpoof APK (companion controller)"
ui_print "  2. Open NetSpoof APK → select Paytm / PhonePe"
ui_print "  3. Also enable NetSpoof in LSposed for Java-level hooks"
ui_print ""
ui_print "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
