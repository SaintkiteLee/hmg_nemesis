# Pre-made Packages

Download the `.zip` for one catalog entry and extract it onto the root of an exFAT or FAT32 drive. Use one NEMESIS package on the drive at a time.

The source for each package is under [`Sources/packages`](../Sources/packages). Rebuild an install zip with:

```bash
python3 Sources/tools/make_nemesis_package.py Sources/packages/wireless-projection -o wireless.projection.zip
```

## Community Contributions

If you have a package which you want to be listed here under **Community Catalog**, open a request via **Issues**. Thank you.

## NEMESIS Catalog

| Name | Source | Description | Compatibility |
| ---- | ------ | ----------- | ------------- |
| Turn-by-Turn Guidance for Phone Projection | [Sources/packages/projection-tbt](../Sources/packages/projection-tbt) | Cluster and HUD turn-by-turn guidance for Android Auto and Apple CarPlay | Kia EV6 with firmware 260507. |
| Enable ADB | [Sources/packages/adb-tcp](../Sources/packages/adb-tcp) | Clears the hide flag on ADB in Engineering Mode. After install, open Engineering Mode, Module Info, page 3, and tap a corner of the screen until the ADB menu appears | All models. |
| Enable Wireless Features | [Sources/packages/wireless-projection](../Sources/packages/wireless-projection) | Enables WiFi control and wireless phone projection. Apple CarPlay through this package is not verified | Head units with a WiFi module. |

## Community Catalog

| Name | Repository | Description | Compatibility |
| ---- | ---------- | ----------- | ------------- |
| HMG Leafia Plus | [GitHub](https://github.com/VysseCodes/HMG-Leafia-Plus) | Wireless Apple CarPlay and Android Auto, and turn-by-turn guidance on the cluster and HUD. | Enable Wireless Features and Turn-by-Turn Guidance for Phone Projection packages must be uninstalled first. Full functionality - Compatible with US 260507 firmware Kia EV6 and Hyundai Ioniq 5 2022/2023, KR 260902 firmware Hyundai Ioniq 5 and Kia K5 Hybrid 2021/2022 (awaiting real-car confirmation). Basic functionality - And any Gen5W head unit with a Wi-Fi module |
| Auto-dismissal of safety popup on vehicle startup | [GitHub](https://github.com/armandodollia/nemesis-package-builds) | Automatically dismisses the startup confirmation/warning prompt that normally requires a manual tap before entering the infotainment interface. | All Gen5W models. Tested on a 2022 Kia EV6. |
| LaunchPad | [GitHub](https://github.com/paytondevs/gen5w-preconditioning) | A car launcher dashboard with manual battery preconditioning feature. | All Gen5W models for the launcher, Manual preconditioning supported only on US Hyundai Ioniq 5. |
