# Reversible K5 Gen5W wireless Android Auto trial

This experimental NEMESIS USB example addresses an AP credential mismatch on a Korean-market 2020 Kia K5 DL3 running `DL3.KOR.S5W_M.V039.001.260902`.

The AP could be started in Connectivity mode, but its actual SSID matched the ordinary hotspot `SSID`, while the stock AA service read `ConnectivitySSID`. Enabling wireless feature flags alone did not make a phone join that AP. This example copies the existing configuration, aligns **only** `SSID/preSharedKey` with `ConnectivitySSID/ConnectivityPSK`, and verifies the actual AP's SSID, WPA2/CCMP and credentials before starting a reversible trial.

## Validation and limits

- Wireless association, DHCP, an established AA TCP connection on port 40515, wireless GPM status 6 and the actual AA screen were confirmed on the vehicle above. No other firmware is claimed compatible.
- The original private AP configuration and settings were restored and independently verified in earlier failed trials. Fault-injection tests cover startup failure, credential mismatch, unknown credentials, interruption, timeout and credential drift before keep.
- After an infotainment reset-button reboot, the first automatic and manual AA attempts failed; toggling phone Bluetooth restored wireless AA. **Reliable automatic reconnection after reboot remains unresolved.** This package does not add a permanent boot retry service.
- On keep, guard removal runs after the USB is removed. Its final log lives on the vehicle at `/data/nemesis/k5-aa-apalign/post-usb-cleanup.txt`; the preceding USB export cannot prove that removal completed. The final successful trial's post-removal guard cleanup has not yet been independently verified.
- The root compatibility package `com.daudio.app.deviceconnections` must be absent initially. It is retained on keep and removed on rollback. Existing packages in that namespace are never overwritten.
- NEMESIS must already be installed and wireless projection features enabled, for example by an appropriate existing wireless-feature package. Leafia basic functionality was used during the vehicle test. This example does not install Leafia, alter feature flags or replace stock APK/ODEX files.

The firmware gate and public software fingerprints in `profile.json` deliberately reject other images. They are shared software build identifiers, not a VIN, device serial number or individual-vehicle fingerprint.

## Build and test

Requirements: Python 3.9+, JDK 17, Android SDK platform 35, build-tools 34.0.0, and Bash for workflow tests. On Windows use Git Bash, not a disconnected WSL filesystem. Override `ANDROID_PLATFORM`, `ANDROID_BUILD_TOOLS` or `BASH` if needed.

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk
python3 build.py
python3 package.py
python3 test.py
python3 test_adapter.py
python3 test_shell.py
```

Run those commands from this directory. Outputs are under `dist/`, including `aa-ap-fix-0.4.4-usb.zip`, a source archive, manifests and test reports. `test_adapter.py` uses synthetic Android API doubles and synthetic credentials; it never connects to a vehicle.

The build creates an ignored development signing key under `.private/`. It uses API 19-compatible DEX035 and JAR v1/SHA1 signing for the small compatibility APK. Never commit that private key. A fresh build has its own signing identity and requires the same initially empty package namespace.

Private OEM firmware dumps are not needed to build and are not distributed. OEM API declarations and quickened getter mappings were checked privately against the pinned firmware; public tests do not pretend to repeat those checks without those inputs.

## USB use

Follow [USB_README.txt](USB_README.txt). Back up existing USB contents before extracting the built USB ZIP. Use one root `NEMESIS.data` workflow at a time; this example provides no host script that deletes or formats a drive.

The trial saves the original AP Parcelable and persisted settings privately on the vehicle, installs a temporary recovery guard before changing AP state, and starts an eight-minute confirmation window only after AP verification. Remove the USB and wait 15 seconds before trying AA: a stable USB removal permits an idle-only AA service restart so a stale USB-AA start mode is not reused. Connected/starting or unknown AA states are protected.

Reinsert the same marked USB before the timeout, then choose keep only if the actual AA screen opened. Failures, cancellation, a missing success confirmation, interruption or a changed boot while testing trigger rollback. A failed rollback is retried and never reported as verified. Keep still requires matching actual AP credentials; changes since initial readiness reject keep.

## Implementation notes

- `ApCredentials` works on an independent copy and leaves AA fields and the private original unchanged.
- `ApComparison` compares hostapd passphrases or derived WPA PSKs in RAM. Android's [SoftapController implementation](https://android.googlesource.com/platform/system/netd/+/40cdfe48dbf95d4cef7bbd47bd0c0e72f72bd390/SoftapController.cpp) uses PBKDF2-HMAC-SHA1 with SSID salt, 4096 iterations and 32 output bytes. Tests include an independent WPA vector and [RFC 6070](https://www.rfc-editor.org/info/rfc6070/) test data.
- The comparison is against current fields read by the pinned GPM source. It does not capture GPM's cached values or Bluetooth wire credentials.
- `ExternalSettings` uses the OEM external-provider path for root; ordinary app ContentResolver acquisition fails on this ROM.
- Hostapd query and event sockets are separate. Event polling checks `available()` before reading, avoiding KitKat's plain `IOException` on idle receive timeout. Query EOF does not tear down the event monitor.
- An idle persistent AA process is reset only after its command, all UIDs and PID start time are checked. A new live Binder is required after restart.

## Privacy and contribution scope

This directory contains application source, synthetic tests, build scripts and public firmware compatibility data. No captured vehicle logs, VIN, real SSID/MAC, phone name, USB serial, vehicle credentials, private AP backup, local account path or private signing key is included. Runtime diagnostics can contain local network identifiers: keep collected `K5-AA-FIX` data private and sanitize it before sharing.

Runtime code is the successful 0.4.4 trial implementation. Build tooling has been made portable without distributing OEM firmware. This example is covered by the repository's AGPL-3.0 license. The Manager activity-finish compatibility change is a separate, reviewable change.
