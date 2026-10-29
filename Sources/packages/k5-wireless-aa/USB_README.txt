K5 Gen5W wireless Android Auto 0.5.0

Only for DL3.KOR.S5W_M.V039.001.260902 with matching pinned stock software.
NEMESIS and wireless projection features must already be enabled.
0.4.4's connection code succeeded on the vehicle; this simplified installation
lifecycle has host verification and still needs a vehicle test.

1. Restore any previous K5 AA trial first, using that trial's own restore tool.
   Do not uninstall Leafia or clear its wireless features. Do not delete backups.
2. Back up your USB files, then extract this normal ZIP onto the USB root.
3. Park, disconnect AA, temporarily turn phone Bluetooth off, and reboot the
   infotainment. Insert USB after the normal screen appears and confirm execution.
4. Wait for Operation success, remove USB within two minutes, then wait 15 seconds.
   Enable phone Wi-Fi/Bluetooth and connect Android Auto. No manual Wi-Fi password,
   keep decision, eight-minute connection trial or reinsertion is needed.
5. Operation success means the AP is prepared. The background setup then verifies
   USB removal, resets only an idle AA service and marks installation complete.
   Actual AA success requires the phone to connect and the AA screen to appear.

Failed setup restores the private original configuration and related settings.
A pending install interrupted by reboot is restored, not resumed blindly.
Backups and independent recovery tools remain under /data/nemesis/k5-wireless-aa.

Restore: disconnect AA, then Launch "K5 Wireless Android Auto" in Manager, or run:
sh /data/nemesis/k5-wireless-aa/restore.sh
Verify rolled_back before uninstalling the package. An uninstall message alone
is insufficient: NEMESIS 1.15 ignores failed pre-uninstall hooks.

Normal setup captures no detailed authentication or network logs. Optional
collection is separate in the source diagnostics directory. This release does
not solve the first automatic reconnect after a reset-button reboot; toggling
phone Bluetooth restored connection in the earlier vehicle test.
