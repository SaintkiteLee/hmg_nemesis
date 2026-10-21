#!/system/bin/sh
# Starts NEMESIS watchdog (which starts svcmgr). Stock firmware has no this file.
# Watchdog detaches; this oneshot must return so init does not kill the tree.
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
trap '' HUP
if [ -x /system/xbin/nemesis ]; then
    chown root:root /system/xbin/nemesis 2>/dev/null || true
    chmod 4755 /system/xbin/nemesis 2>/dev/null || true
    /system/xbin/nemesis watchdog
fi
exit 0
