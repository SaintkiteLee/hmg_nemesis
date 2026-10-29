# Optional K5 AA diagnostics

This directory contains the detailed collectors removed from normal installation. They are not in the normal USB or normal runtime JAR. They do not call AP setters, change saved settings, install apps or restart AA. They read Wi-Fi/GPM status and subscribe to hostapd events for 60 seconds, then exit.

```sh
python3 build.py --diagnostics
```

Copy `dist/diagnostics.jar` separately when collecting a specific problem. Run as root with an explicit private output directory:

```sh
CLASSPATH=/path/to/diagnostics.jar app_process /system/bin com.saintkite.k5aa.Diagnostics /data/local/tmp/k5-aa-diagnostics
```

The output includes detailed network identifiers (MAC, IP and possibly phone-derived names). Credential-bearing lines are filtered, but this does not make the capture safe to publish. Inspect and sanitize it first. No previous vehicle captures are included. The historical reversible 0.4.4 trial is available in commit f8af07b; it is not a normal release artifact.
