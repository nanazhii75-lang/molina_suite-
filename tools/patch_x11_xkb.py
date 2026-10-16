#!/usr/bin/env python3
"""Perbaiki X server gagal start: $XKB_CONFIG_ROOT is not set.

X server lorie berjalan di host (app_process) dan hanya mencari data keyboard XKB di
$PREFIX/share/X11/xkb, yang tidak ada di bootstrap molina. Data itu ada di rootfs Debian
(paket xkb-data). Patch ini:
  1. X11ServerCommand: mengekspor XKB_CONFIG_ROOT ke rootfs Debian sebelum server dijalankan;
     bila tidak ditemukan, keluar dengan pesan yang jelas (tampil sebagai toast).
  2. PackageSpec: menambah xkb-data ke paket "Desktop X11".
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_xkb.py [--apply]
"""
import argparse
import difflib
import os
import sys

SERVER_CMD = "features/feature-x11/src/main/kotlin/com/molina/suite/feature/x11/X11ServerCommand.kt"
PACKAGE_SPEC = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/packages/PackageSpec.kt"


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def load(path):
    if not os.path.isfile(path):
        die("berkas tidak ada: " + path)
    with open(path, "rb") as f:
        raw = f.read().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n"), eol


def save(path, text, eol):
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


XKB_CONST = '''    private const val DEBIAN_ROOTFS = "/var/lib/proot-distro/installed-rootfs/debian"

    /**
     * X server tidak membawa data keyboard XKB dan bootstrap molina tidak memilikinya; data itu
     * ada di rootfs Debian (paket xkb-data). Bila XKB_CONFIG_ROOT belum diatur, diarahkan ke sana.
     * Tanpa data itu server berhenti dengan pesan yang menunjuk ke pemasangan Desktop X11.
     */
    private const val XKB_SETUP =
        "R=\\"\\${PREFIX:-/data/data/com.molina.suite/files/usr}$DEBIAN_ROOTFS\\"; " +
            "if [ -z \\"\\${XKB_CONFIG_ROOT-}\\" ]; then " +
            "for d in \\"\\$R/usr/share/xkeyboard-config-2\\" \\"\\$R/usr/share/X11/xkb\\"; do " +
            "if [ -f \\"\\$d/rules/evdev\\" ]; then export XKB_CONFIG_ROOT=\\"\\$d\\"; break; fi; done; fi; " +
            "if [ -z \\"\\${XKB_CONFIG_ROOT-}\\" ]; then " +
            "echo 'Data keyboard XKB belum ada. Pasang Desktop X11 di Setting, menu Paket.' >&2; exit 1; fi; "

'''


def patch_server(apply):
    text, eol = load(SERVER_CMD)
    if "XKB_SETUP" in text:
        print("X11ServerCommand: sudah")
        return None
    anchor = '    private const val NATIVE_LIB = "libXlorie.so"\n\n'
    if text.count(anchor) != 1:
        die("jangkar NATIVE_LIB tidak ditemukan di X11ServerCommand.kt")
    new = text.replace(anchor, anchor.rstrip("\n") + "\n\n" + XKB_CONST.rstrip("\n") + "\n\n")
    old_line = '            "exec $APP_PROCESS / $ENTRY_POINT $DISPLAY"\n'
    if new.count(old_line) != 1:
        die("baris exec app_process tidak ditemukan di X11ServerCommand.kt")
    new = new.replace(old_line, '            XKB_SETUP +\n' + old_line)
    return SERVER_CMD, text, new, eol


def patch_spec(apply):
    text, eol = load(PACKAGE_SPEC)
    if '"xkb-data"' in text:
        print("PackageSpec: sudah")
        return None
    old = '"dbus-x11", "x11-xserver-utils", "fonts-dejavu-core"'
    if text.count(old) != 1:
        die("daftar paket x11desktop tidak ditemukan di PackageSpec.kt")
    new = text.replace(old, '"dbus-x11", "x11-xserver-utils", "xkb-data", "fonts-dejavu-core"')
    return PACKAGE_SPEC, text, new, eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir("features") or not os.path.isdir("core"):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (patch_server(args.apply), patch_spec(args.apply)) if c]
    if not changes:
        print("Tidak ada perubahan (semua sudah diterapkan).")
        return
    for path, old, new, eol in changes:
        diff = difflib.unified_diff(old.splitlines(), new.splitlines(), path, path, lineterm="", n=1)
        print("\n".join(diff))
        if args.apply:
            save(path, new, eol)
            print("DITULIS: " + path)
    if not args.apply:
        print("\nDry-run. Jalankan lagi dengan --apply untuk menulis.")


if __name__ == "__main__":
    main()
