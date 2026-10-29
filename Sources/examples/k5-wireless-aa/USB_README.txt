K5 Gen5W reversible wireless Android Auto trial 0.4.4

Only for DL3.KOR.S5W_M.V039.001.260902 with matching pinned stock software.
NEMESIS and wireless-projection feature support must already be present.
Reliable automatic AA reconnection after an infotainment reboot is not yet solved.

1. Park, temporarily turn phone Bluetooth off and reboot the infotainment unit.
   After the normal screen appears, insert this USB and approve execution.
2. AP name, WPA2 and credential equality must verify before Operation success.
   Remember the displayed Wi-Fi name; it may differ from the ordinary hotspot name.
   If Operation failed appears, stop and inspect the collected logs on a PC.
3. After Operation success, remove USB, wait 15 seconds, enable phone Wi-Fi and
   Bluetooth, and try wireless Android Auto. No manual Wi-Fi password is needed.
4. Within eight minutes of AP readiness, reinsert the SAME marked USB and approve
   execution. Select the phone observation and choose keep only if the actual AA
   screen opened. Otherwise choose failure/restore. The vehicle UI is Korean.
5. After choosing a result, leave USB connected until the final Operation success.
   Remove it, wait 15 seconds, then connect it to a PC to inspect the logs.

The private original configuration is preserved before changes. A recovery guard
restores all original AP fields, states/settings and the added compatibility APK
on failure, interruption, changed boot or no success confirmation within 8 minutes.
Rollback is independently verified before it is reported complete. A failed
verification preserves backups and is retried. No stock APK/ODEX is replaced.

On KEEP, the AP settings and compatibility APK remain. The recovery guard removes
itself AFTER USB removal, preserving a real connected AA session. That final
cleanup is logged on the vehicle, not in the already removed USB's earlier export:
/data/nemesis/k5-aa-apalign/post-usb-cleanup.txt
The private original backup remains in /data/nemesis/k5-aa-apalign.
aa/restore.sh is an explicit later restore tool; it is not executed automatically.

USB logs: K5-AA-FIX/run-*/
AP_AA_SSID_AND_CREDENTIAL_MATCH_VERIFIED means AP verification, not AA success.
The comparison does not capture Bluetooth wire credentials or GPM internal caches.
Credentials and derived keys are not exported; runtime logs may include local
network identifiers, so do not publish collected logs without sanitizing them.
