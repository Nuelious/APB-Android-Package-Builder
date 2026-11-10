#!/usr/bin/env python3
"""Create/verify a ZIP that retains Unix symlinks and executable mode bits."""
import os, stat, sys, zipfile
from pathlib import Path

def add_tree(zf, root: Path):
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root).as_posix()
        st = path.lstat()
        info = zipfile.ZipInfo(rel + ("/" if stat.S_ISDIR(st.st_mode) else ""))
        info.create_system = 3
        info.external_attr = (st.st_mode & 0xFFFF) << 16
        if stat.S_ISLNK(st.st_mode):
            zf.writestr(info, os.readlink(path).encode())
        elif stat.S_ISDIR(st.st_mode):
            zf.writestr(info, b"")
        elif stat.S_ISREG(st.st_mode):
            with path.open("rb") as src, zf.open(info, "w") as dst:
                while True:
                    chunk = src.read(1024 * 1024)
                    if not chunk: break
                    dst.write(chunk)
        else:
            raise RuntimeError(f"Unsupported special file: {path}")

def verify(root: Path, archive: Path):
    with zipfile.ZipFile(archive) as zf:
        for info in zf.infolist():
            name = info.filename.rstrip("/")
            target = root / name
            mode = (info.external_attr >> 16) & 0xFFFF
            kind = stat.S_IFMT(mode)
            if kind == stat.S_IFLNK:
                if not target.is_symlink() or os.readlink(target) != zf.read(info).decode():
                    raise RuntimeError(f"Symlink mismatch: {name}")
            elif kind == stat.S_IFDIR:
                if not target.is_dir(): raise RuntimeError(f"Missing directory: {name}")
            elif kind == stat.S_IFREG:
                if not target.is_file() or target.is_symlink():
                    raise RuntimeError(f"Missing regular file: {name}")
                if bool(mode & 0o111) != bool(target.stat().st_mode & 0o111):
                    raise RuntimeError(f"Executable mode mismatch: {name}")

def main():
    if len(sys.argv) == 4 and sys.argv[1] == "--verify":
        verify(Path(sys.argv[2]), Path(sys.argv[3])); return
    if len(sys.argv) != 3: raise SystemExit("usage: package_runtime_zip.py ROOT OUTPUT.zip")
    root, output = Path(sys.argv[1]), Path(sys.argv[2])
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as zf:
        add_tree(zf, root)
    with zipfile.ZipFile(output) as zf:
        bad = zf.testzip()
        if bad: raise RuntimeError(f"ZIP CRC failure: {bad}")
if __name__ == "__main__": main()
