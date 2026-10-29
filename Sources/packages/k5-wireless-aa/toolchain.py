"""Portable local Android build settings; no machine-specific paths are stored."""
from pathlib import Path
import os
import shutil

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "dist"
JDK = Path(os.environ.get("JAVA_HOME", Path(shutil.which("javac") or "javac").resolve().parent.parent))
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", Path.home() / "Android" / "Sdk")))
BT = SDK / "build-tools" / os.environ.get("ANDROID_BUILD_TOOLS", "34.0.0")
ANDROID = SDK / "platforms" / os.environ.get("ANDROID_PLATFORM", "android-35") / "android.jar"


def java_tool(name):
    filename = name + (".exe" if os.name == "nt" else "")
    candidate = JDK / "bin" / filename
    if candidate.is_file():
        return candidate
    found = shutil.which(name)
    if not found:
        raise RuntimeError("JDK 17 is required; set JAVA_HOME or add JDK tools to PATH")
    return Path(found)


def bash_tool():
    candidate = os.environ.get("BASH")
    if not candidate and os.name == "nt":
        fallback = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        if fallback.is_file():
            candidate = str(fallback)
    candidate = candidate or shutil.which("bash")
    if not candidate:
        raise RuntimeError("Bash is required for USB workflow tests; set BASH")
    return Path(candidate)


def posix(path):
    path = Path(path).resolve()
    return "/" + path.drive[0].lower() + path.as_posix()[2:] if os.name == "nt" else path.as_posix()
