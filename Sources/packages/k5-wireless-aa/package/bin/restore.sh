#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
STATE=/data/nemesis/k5-wireless-aa
[ -f "$STATE/original.properties" ] || exit 0
/system/xbin/nemesis service stop k5.wireless.aa/setup 2>/dev/null
export CLASSPATH="$STATE/runtime.jar"
/system/bin/app_process /system/bin com.saintkite.k5aa.Main restore "$STATE" || exit 1
/system/bin/app_process /system/bin com.saintkite.k5aa.Main verify-restored "$STATE"
