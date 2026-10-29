#!/usr/bin/env python3
"""Exercise Manager's HKMC activity-finish compatibility without Android hardware."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def java_tool(name):
    home = os.environ.get("JAVA_HOME")
    if home:
        return str(Path(home) / "bin" / (name + (".exe" if os.name == "nt" else "")))
    found = shutil.which(name)
    if not found:
        raise RuntimeError("Set JAVA_HOME to a JDK")
    return found


def main():
    source = ROOT / "manager/src/com/nemesis/manager"
    with tempfile.TemporaryDirectory(prefix="hkmc-finish-test-") as temp:
        root = Path(temp)
        files = {
            "android/app/Activity.java": """package android.app; public class Activity {
              public static boolean failFinish,failMove;public static int finishes,moves;
              public void finish(){finishes++;if(failFinish)throw new SecurityException("GET_TASKS");}
              public boolean moveTaskToBack(boolean all){moves++;if(failMove)throw new SecurityException("GET_TASKS");return true;}
            }""",
            "android/util/Log.java": "package android.util;public class Log {public static int e(String t,String m,Throwable x){return 0;}}",
            "com/nemesis/manager/FinishTest.java": """package com.nemesis.manager;
              import android.app.Activity;
              public class FinishTest {public static void main(String[] args){
                HkmcActivity activity=new HkmcActivity();activity.finish();
                if(Activity.finishes!=1||Activity.moves!=0)throw new AssertionError();
                Activity.failFinish=true;activity.finish();
                if(Activity.moves!=1)throw new AssertionError();
                Activity.failMove=true;activity.finish();
                if(Activity.moves!=2)throw new AssertionError();
                System.out.println("PASS normal finish, GET_TASKS rejection and fallback rejection");
              }}""",
        }
        for name, text in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        command = [java_tool("javac"), "-encoding", "UTF-8", "-d", str(root / "classes")]
        command += [str(p) for p in root.rglob("*.java")] + [str(source / "HkmcActivity.java")]
        subprocess.run(command, check=True)
        subprocess.run([java_tool("java"), "-cp", str(root / "classes"), "com.nemesis.manager.FinishTest"], check=True)
    for name in ["MainActivity", "DialogActivity", "LogActivity", "OptionsActivity", "PackageActivity", "ServiceActivity"]:
        assert "extends HkmcActivity" in (source / (name + ".java")).read_text(encoding="utf-8")
    assert "android.permission.GET_TASKS" in (ROOT / "manager/AndroidManifest.xml").read_text(encoding="utf-8")
    print("PASS all Manager activities use the guard and GET_TASKS is declared")


if __name__ == "__main__":
    main()
