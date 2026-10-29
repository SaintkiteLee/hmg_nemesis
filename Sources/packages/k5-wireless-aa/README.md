# K5 Gen5W wireless Android Auto

This NEMESIS package aligns the actual AP SSID and credentials with the Connectivity fields read by stock Android Auto on `DL3.KOR.S5W_M.V039.001.260902`. NEMESIS and wireless projection features must already be installed (Leafia basic functionality was used in the original vehicle test).

The AP credential verification, root settings-provider access, private Parcelable backup, settings bridge and idle-only AA reset come from the vehicle-successful 0.4.4 implementation. Version **0.5.0 separates normal installation from diagnostics**. Its new installation lifecycle has host validation; it has not yet been run on the vehicle. Reliable first automatic reconnect after an infotainment reboot remains unresolved; toggling phone Bluetooth restored AA in the earlier vehicle test.

## Normal installation

Build or download the normal USB ZIP and follow [USB_README.txt](USB_README.txt). The wrapper verifies the payload, installs a standard NEMESIS package, and waits for AP readiness. Remove the USB after Operation success, wait 15 seconds, then connect from the phone. An idle AA service is reset only after that same USB has been absent for two seconds. Connected/starting and unknown AA states are protected.

There is no eight-minute connection trial, survey, keep decision or USB reinsertion. The setup worker exits after AP verification and AA preparation. `installed` means installation completed, not that a phone's AA screen was observed. It does not add a permanent boot retry or connection monitor. The registered background setup entry becomes a no-op once installed.

## Backup and restore

Before mutation the package saves the original AP Parcelable, SHA256 and original AP/client/settings/property states privately under `/data/nemesis/k5-wireless-aa`, with owner-only permissions. It also retains a recovery JAR and script there. Startup failure, credential drift, idle-AA restart failure, pending installation on a changed boot, or not removing USB within two minutes triggers verified restoration. A failed restore stays pending and is retried; the original baseline is not deleted or overwritten.

To restore, first disconnect Android Auto, then use NEMESIS Manager **Launch** on `K5 Wireless Android Auto`, or run as root:

```sh
sh /data/nemesis/k5-wireless-aa/restore.sh
```

Only `rolled_back` plus `verify-restored` success confirms completion. After successful restore the package can be uninstalled. NEMESIS 1.15 ignores a failing pre-uninstall hook, so restore explicitly before uninstalling; an uninstall message alone is not proof of restoration. Even if the package is removed, the private baseline and recovery tool stay on the vehicle. Phone pairing changes and all internal AA session state are outside the restore scope.

Existing 0.4.4 trials and unknown packages in the bridge namespace are **not overwritten or adopted**. Restore the prior trial with its own recovery tool and finish its guard cleanup before installing 0.5.0. This prevents losing the true pre-trial baseline or replacing a differently signed bridge. The 0.4.4 trial is preserved in [commit f8af07b](https://github.com/SaintkiteLee/hmg_nemesis/tree/f8af07ba0456cfcaae671391d18162d587ee9b51/Sources/examples/k5-wireless-aa).

## Build and test

Requires Python 3.10+, JDK 17, Android SDK platform 35, build-tools 34.0.0, and Bash for workflow tests. Configure `JAVA_HOME`, `ANDROID_SDK_ROOT`, and optionally `BASH`. Override `ANDROID_PLATFORM` or `ANDROID_BUILD_TOOLS` if needed.

```sh
python3 build.py
python3 package.py
python3 test.py
python3 test_adapter.py
python3 test_shell.py
```

Outputs are in ignored `dist/`. `build.py` compiles **only** `src/` and the two settings-bridge classes into the normal runtime; tests and diagnostic sources are never compiled into it. The normal USB contains five files, with no source, test fixtures or captures. The small APK uses API19-compatible DEX035 and v1 signing with an ignored local development key in `.private/`. No original OEM binaries are distributed. `profile.json` pins shared software build hashes, not individual vehicle identifiers.

## Optional diagnostics

Detailed logcat, hostapd events, station lists, DHCP/ARP and socket snapshots live in [diagnostics/](diagnostics/README.md). They require an explicit separate build and invocation and do not participate in normal installation. Tests remain as development tooling, including checks that diagnostic and test classes are absent from the normal binaries.

## Privacy and limits

No captured vehicle logs, VIN, actual SSID/MAC, phone names, USB serials, credentials, private backups, local home paths or private signing keys are shipped. Normal operation records only setup/restore events and state; it does not copy network captures onto USB. Optional diagnostics can contain local network identifiers and must be kept private or sanitized before sharing. Credential and derived-key comparison happens only in RAM.

The credential alignment verifies current fields used by the pinned GPM source against hostapd; it does not capture Bluetooth wire values or GPM caches. No other firmware or CarPlay compatibility is claimed. This example remains covered by the repository's AGPL-3.0 license. The Manager permission fix is a separate change.
