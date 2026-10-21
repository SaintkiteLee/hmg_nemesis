#!/usr/bin/env python3
"""Build a NEMESIS install zip from a package directory containing NEMESIS.data."""
from __future__ import annotations

import argparse
import zipfile
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("pkg_dir", type=Path)
    ap.add_argument("-o", "--output", type=Path, default=None)
    args = ap.parse_args()
    src = args.pkg_dir.resolve()
    meta = src / "NEMESIS.data"
    if not meta.is_file():
        raise SystemExit(f"missing {meta}")
    out = args.output or (src.parent / f"{src.name}.zip")
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for p in sorted(src.rglob("*")):
            if p.is_file():
                z.write(p, p.relative_to(src).as_posix())
    print(f"Wrote {out} ({out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
