# Pre-made Packages

The package trees are under [`Sources/packages`](../Sources/packages). This folder keeps the USB stick descriptor for each catalog entry. The zip named in `NEMESIS.data` is not stored here. Build it, then copy it to `packages/` on the stick next to `NEMESIS.data`.

```bash
python3 Sources/tools/make_nemesis_package.py Sources/packages/wireless-projection -o wireless.projection.zip
```

Use one NEMESIS package on the USB drive at a time. Copy `NEMESIS.data` to the stick root and the zip to the relative `path` inside that descriptor (`packages/<id>.zip`).

## Catalog

| Name | Source | Description | Compatibility |
| ---- | ------ | ----------- | ------------- |
| Turn-by-Turn Guidance for Phone Projection | `Sources/packages/projection-tbt` | Cluster and HUD turn-by-turn guidance for Android Auto and Apple CarPlay | Kia EV6 firmware 260507 |
| Enable ADB | `Sources/packages/adb-tcp` | Clears the hide flag on ADB in Engineering Mode. After install, open Engineering Mode, Module Info, page 3, and tap a corner of the screen until the ADB menu appears | Universal. The package snapshots the live unit and reboots |
| Enable Wireless Features | `Sources/packages/wireless-projection` | Enables WiFi control and wireless phone projection. Apple CarPlay through this package is not verified | Head units with a WiFi module |
