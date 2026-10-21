# NEMESIS_QB_HOOK: qb_runtime.sh runs on quickboot_fs before Android is up.
if [ -x /system/xbin/nemesis ]; then
    trap '' HUP
    chown root:root /system/xbin/nemesis 2>/dev/null || true
    chmod 4755 /system/xbin/nemesis 2>/dev/null || true
    /system/xbin/nemesis watchdog
fi
