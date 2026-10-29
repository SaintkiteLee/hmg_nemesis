#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
ROOT=/data/nemesis/apps/k5.wireless.aa
export CLASSPATH="$ROOT/lib/runtime.jar"
exec /system/bin/app_process /system/bin --nice-name=k5-aa-setup com.saintkite.k5aa.Main run /data/nemesis/k5-wireless-aa "$ROOT/bridge.apk"
