#!/usr/bin/env bash
# Console-only QEMU flow tests for the NEMESIS CLI (no zygote / no Manager UI).
exec python3 "$(cd "$(dirname "$0")" && pwd)/emu_nemesis_flow.py" "$@"
