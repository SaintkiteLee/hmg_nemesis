#!/usr/bin/env python3
"""Persistent virtio-console session for the Gen5W QEMU hvc (TCP 4446)."""
from __future__ import annotations

import socket
import time


class Guest:
    def __init__(self, host: str = "127.0.0.1", port: int = 4446) -> None:
        self.n = 0
        self.s = socket.create_connection((host, port), 10)
        self.s.settimeout(1.0)
        time.sleep(0.5)
        # Drop interactive mksh (prompt/echo/wrap) for a plain eval loop.
        self._send("\nexec sh -c 'while IFS= read -r l; do eval \"$l\"; done'\n")
        self._drain(1.0)

    def close(self) -> None:
        try:
            self.s.close()
        except OSError:
            pass

    def _send(self, text: str) -> None:
        self.s.sendall(text.encode("utf-8", errors="replace"))

    def _drain(self, seconds: float) -> bytes:
        buf = b""
        deadline = time.time() + seconds
        self.s.settimeout(0.2)
        while time.time() < deadline:
            try:
                chunk = self.s.recv(8192)
            except socket.timeout:
                continue
            if not chunk:
                break
            buf += chunk
        return buf

    def cmd(self, command: str, timeout: float = 30.0) -> str:
        self.n += 1
        tok = f"__EOC_{self.n}__"
        self._send(command.rstrip() + "\n" + f"echo {tok}\n")
        buf = b""
        deadline = time.time() + timeout
        self.s.settimeout(1.0)
        while time.time() < deadline:
            try:
                chunk = self.s.recv(8192)
            except socket.timeout:
                continue
            if not chunk:
                break
            buf += chunk
            text = buf.decode("utf-8", errors="replace")
            lines = [ln.replace("\r", "") for ln in text.splitlines()]
            if tok in [ln.strip() for ln in lines]:
                # leftover after the sentinel belongs to the next command
                self._drain(0.15)
                return self._clean(text, tok, command)
        return self._clean(buf.decode("utf-8", errors="replace"), tok, command)

    @staticmethod
    def _clean(text: str, tok: str, command: str) -> str:
        echoed = {ln.strip() for ln in command.splitlines() if ln.strip()}
        echoed.add(f"echo {tok}")
        lines = []
        for line in text.splitlines():
            raw = line.replace("\r", "").strip()
            if raw == tok or raw in echoed:
                continue
            if raw.startswith("root@") and "# " in raw:
                # leftover interactive prompt + echoed command
                after = raw.split("# ", 1)[-1].strip()
                if after == tok or after in echoed or after == f"echo {tok}":
                    continue
                raw = after
            lines.append(raw)
        return "\n".join(lines).strip() + ("\n" if lines else "")
