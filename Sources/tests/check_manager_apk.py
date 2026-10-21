#!/usr/bin/env python3
"""Host checks for Manager APK before flashing. No guest / no GUI."""
from __future__ import annotations

import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "manager" / "NEMESIS.apk"
SRC = ROOT / "manager" / "src" / "com" / "nemesis" / "manager"
AAPT = "aapt"


def fail(msg: str) -> None:
    print("FAIL:", msg, file=sys.stderr)
    sys.exit(1)


def dex_blob() -> bytes:
    with zipfile.ZipFile(APK) as z:
        return z.read("classes.dex")


def main() -> None:
    if not APK.is_file():
        fail(f"missing {APK}")
    with zipfile.ZipFile(APK) as z:
        names = z.namelist()
    nine = [n for n in names if n.endswith(".9.png")]
    if nine:
        fail("APK still contains 9-patches (KitKat inflate NPE): " + ", ".join(nine))
    if "classes.dex" not in names:
        fail("APK missing classes.dex")
    dex = dex_blob()
    if dex[:8] != b"dex\n035\x00":
        fail(f"DEX version is {dex[:8]!r}, KitKat needs 035")
    latin = dex.decode("latin1", errors="ignore")
    java_names = sorted(p.stem for p in SRC.glob("*.java"))
    if "App" in java_names:
        fail("custom Application class App.java must not be shipped")
    for stem in java_names:
        needle = f"Lcom/nemesis/manager/{stem};"
        if needle not in latin:
            fail(f"classes.dex missing {needle} (Make/d8 skipped a class)")
    dump = subprocess.check_output(
        [AAPT, "dump", "xmltree", str(APK), "AndroidManifest.xml"],
        text=True,
        errors="replace",
    )
    if "(type 0x10)0x5" not in dump:
        fail("Manager versionCode is not 5:\n" + dump[:800])
    if '=".App"' in dump or 'name=".App"' in dump:
        fail("AndroidManifest must not name a custom Application class")
    if "BootReceiver" not in dump:
        fail("AndroidManifest missing BootReceiver")
    tree = subprocess.check_output(
        [AAPT, "dump", "xmltree", str(APK), "res/layout/activity_main.xml"],
        text=True,
        errors="replace",
    )
    if "btn_usb" in tree:
        fail("activity_main still has btn_usb (old layout)")
    if "list" not in tree:
        fail("activity_main missing ListView id list")
    strings = subprocess.check_output(
        [AAPT, "dump", "resources", str(APK)],
        text=True,
        errors="replace",
    )
    for needle in ("close", "packages", "services", "logs"):
        if needle not in strings:
            fail(f"missing string {needle}")
    list_xml = (ROOT / "manager" / "res" / "layout" / "activity_list.xml").read_text()
    if "co_bt_bottom" in list_xml:
        fail("activity_list still uses stretched co_bt_bottom")
    if "co_bt_title" not in list_xml:
        fail("activity_list must use co_bt_title (same as Close)")
    log_xml = (ROOT / "manager" / "res" / "layout" / "activity_log.xml").read_text()
    if "@string/close" in log_xml:
        fail("activity_log has a second Close; Close belongs on the title bar only")
    print("OK: Manager APK DEX 035, all Java classes present, no Application, versionCode 5")


if __name__ == "__main__":
    main()
