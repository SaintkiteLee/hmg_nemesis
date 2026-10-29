#!/system/bin/sh
export PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib
[ "$(id -u)" = 0 ] || exit 1
[ "$(getprop ro.build.product2)" = '@FIRMWARE@' ] || exit 1
MD5=$(command -v md5 2>/dev/null)
[ -n "$MD5" ] || MD5=$(command -v md5sum 2>/dev/null)
[ -n "$MD5" ] || exit 1
check(){ expected=$2; set -- $("$MD5" "$1" 2>/dev/null); [ "$1" = "$expected" ]; }
# Compare shared software builds, never individual vehicle identifiers.
check /system/framework/framework.odex '@FRAMEWORK_MD5@' || exit 1
check /system/framework/services.odex '@SERVICES_MD5@' || exit 1
check /system/app/PhoneProjection.odex '@PHONEPROJECTION_MD5@' || exit 1
check /system/app/HKMC_GPM.odex '@HKMC_GPM_MD5@' || exit 1
# Refuse a still-registered legacy recovery worker; finish its own restoration first.
for old in /data/nemesis/apps/k5.aa.*.guard; do
    [ ! -d "$old" ] || { echo 'Restore and remove the previous AA trial first'; exit 1; }
done
[ -n "$K5AA_USB_ROOT" ] || { echo 'Use the supplied USB installer'; exit 1; }
MEDIA=$(cat "$K5AA_USB_ROOT/packages/media-id" 2>/dev/null)
[ -n "$MEDIA" ] || exit 1
STATE=/data/nemesis/k5-wireless-aa
CLASSPATH="$PWD/lib/runtime.jar" /system/bin/app_process /system/bin com.saintkite.k5aa.Main snapshot "$STATE" '@BRIDGE_SHA256@' "$K5AA_USB_ROOT" "$MEDIA" || exit 1
# Recovery survives package removal; the original AP baseline is never removed.
cp lib/runtime.jar "$STATE/runtime.jar" || exit 1
cp bin/restore.sh "$STATE/restore.sh" || exit 1
chmod 600 "$STATE/runtime.jar" "$STATE/restore.sh"
