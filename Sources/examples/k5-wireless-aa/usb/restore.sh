#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
STATE=/data/nemesis/k5-aa-apalign
# Stop the transaction worker first; its uninstall hook restores pending trials.
echo rollback > "$STATE/decision"
/system/xbin/nemesis service stop k5.aa.apalign.guard/monitor 2>/dev/null
CLASSPATH="$STATE/guard.jar" /system/bin/app_process /system/bin com.saintkite.k5aa.Main restore "$STATE"
rc=$?
sync
exit "$rc"
