# Pre-made Packages

Each catalog folder is one USB stick: `NEMESIS.data` at the stick root and the install zip under `packages/`. The `.zip` next to that folder is the same stick. Unzip it onto the root of an exFAT or FAT32 drive, or copy the folder contents there. Use one NEMESIS package on the drive at a time.

The source for each package is under [`Sources/packages`](../Sources/packages). Rebuild an install zip with:

```bash
python3 Sources/tools/make_nemesis_package.py Sources/packages/wireless-projection -o wireless.projection.zip
```

## Catalog

| Name | Source | Description | Compatibility |
| ---- | ------ | ----------- | ------------- |
| Turn-by-Turn Guidance for Phone Projection | `Sources/packages/projection-tbt` | Cluster and HUD turn-by-turn guidance for Android Auto and Apple CarPlay | Kia EV6 firmware 260507 |
| Enable ADB | `Sources/packages/adb-tcp` | Clears the hide flag on ADB in Engineering Mode. After install, open Engineering Mode, Module Info, page 3, and tap a corner of the screen until the ADB menu appears | Universal. The package snapshots the live unit and reboots |
| Enable Wireless Features | `Sources/packages/wireless-projection` | Enables WiFi control and wireless phone projection. Apple CarPlay through this package is not verified | Head units with a WiFi module |
