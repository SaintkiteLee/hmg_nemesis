#!/usr/bin/env python3
"""Talk to the Gen5W QEMU virtio-console root shell on TCP 4446."""
from __future__ import annotations

import argparse
import socket
import sys
import time
from pathlib import Path

SENTINEL = "__EOC__"


def connect(host: str, port: int, timeout: float) -> socket.socket:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2.0)
        try:
            s.connect((host, port))
            return s
        except OSError as e:
            last = e
            s.close()
            time.sleep(1)
    raise SystemExit(f"CONNECT-FAILED: {last}")


def _drain(s: socket.socket, seconds: float = 0.4) -> bytes:
    s.settimeout(0.2)
    buf = b""
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            chunk = s.recv(8192)
        except socket.timeout:
            continue
        if not chunk:
            break
        buf += chunk
    return buf


def run(cmd: str, timeout: float = 30.0, host: str = "127.0.0.1", port: int = 4446) -> str:
    s = connect(host, port, min(max(timeout, 15.0), 60.0))
    try:
        # overlay respawns sh 1s after the previous TCP client disconnects
        time.sleep(1.3)
        s.sendall(b"\nPS1=\nPS2=\nstty -echo cols 512 2>/dev/null\n")
        _drain(s, 0.6)
        payload = cmd.rstrip() + f"\necho {SENTINEL}\n"
        s.sendall(payload.encode("utf-8", errors="replace"))
        s.settimeout(1.0)
        buf = b""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                chunk = s.recv(8192)
            except socket.timeout:
                continue
            if not chunk:
                break
            buf += chunk
            text = buf.decode("utf-8", errors="replace")
            for line in text.splitlines():
                if line.strip() == SENTINEL:
                    return _clean(text)
        return _clean(buf.decode("utf-8", errors="replace"))
    finally:
        s.close()


def _clean(text: str) -> str:
    lines = []
    skip_ps = True
    for line in text.splitlines():
        raw = line.strip()
        if skip_ps and raw in ("PS1=", "PS2=", "stty -echo cols 512 2>/dev/null", ""):
            continue
        skip_ps = False
        if raw == SENTINEL:
            continue
        if raw == f"echo {SENTINEL}":
            continue
        if raw.endswith("# echo " + SENTINEL):
            continue
        lines.append(line)
    return "\n".join(lines).strip() + ("\n" if lines else "")


def put(local: Path, remote: str, timeout: float = 120.0) -> str:
    data = local.read_bytes()
    parent = str(Path(remote).parent)
    prelude = []
    if parent not in ("", ".", "/"):
        prelude.append(f"mkdir -p '{parent}'")
    prelude.append(f"rm -f '{remote}'")
    prelude.append(f": > '{remote}'")
    run("\n".join(prelude), timeout=min(timeout, 30.0))
    last = ""
    step = 8
    chunks = [data[i : i + 40] for i in range(0, len(data), 40)]
    for i in range(0, len(chunks), step):
        lines = []
        for chunk in chunks[i : i + step]:
            esc = "".join("\\x%02x" % b for b in chunk)
            lines.append(f"printf '{esc}' >> '{remote}'")
        last = run("\n".join(lines), timeout=timeout)
    return run(f"ls -l '{remote}'", timeout=min(timeout, 20.0)) or last


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", nargs="?", default=None, help="shell command to run")
    ap.add_argument("--put", nargs=2, metavar=("LOCAL", "REMOTE"))
    ap.add_argument("--timeout", type=float, default=30.0)
    args = ap.parse_args()
    if args.put:
        out = put(Path(args.put[0]), args.put[1], timeout=max(args.timeout, 120.0))
        sys.stdout.write(out)
        return
    if not args.cmd:
        raise SystemExit("usage: gexec.py 'command' | gexec.py --put LOCAL REMOTE")
    sys.stdout.write(run(args.cmd, timeout=args.timeout))


if __name__ == "__main__":
    main()
