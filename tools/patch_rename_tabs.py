#!/usr/bin/env python3
"""Ganti tab Kodi/WebDash menjadi mpv/Code sesuai blueprint v2.

Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_rename_tabs.py [--apply]
"""
import argparse
import difflib
import os
import sys


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def load(path):
    with open(path, "rb") as f:
        raw = f.read().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n"), eol


def save(path, text, eol):
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


CORE = "core/core-common/src/main/kotlin/com/molina/suite/core/common/"
APP = "app/src/main/"

EDITS = [
    (CORE + "EngineId.kt", "    KODI,\n", "    MPV,\n"),
    (CORE + "EngineId.kt", "    WEBDASH,\n", "    CODE,\n"),
    (CORE + "DaemonId.kt", "    KODI\n", "    MPV\n"),
    (CORE + "DaemonStatusBoard.kt", "(terminal, WebDAV, Kodi)", "(terminal, WebDAV, mpv)"),
    (APP + "kotlin/com/molina/suite/MainActivity.kt",
     "bindDaemon(DaemonId.KODI, binding.dotKodi)",
     "bindDaemon(DaemonId.MPV, binding.dotMpv)"),
    (APP + "kotlin/com/molina/suite/MainActivity.kt",
     "R.id.nav_kodi -> EngineId.KODI", "R.id.nav_mpv -> EngineId.MPV"),
    (APP + "kotlin/com/molina/suite/MainActivity.kt",
     "R.id.nav_webdash -> EngineId.WEBDASH", "R.id.nav_code -> EngineId.CODE"),
    (APP + "kotlin/com/molina/suite/ui/StatusPresentation.kt",
     "EngineId.KODI -> R.string.nav_kodi", "EngineId.MPV -> R.string.nav_mpv"),
    (APP + "kotlin/com/molina/suite/ui/StatusPresentation.kt",
     "EngineId.WEBDASH -> R.string.nav_webdash", "EngineId.CODE -> R.string.nav_code"),
    (APP + "res/layout/activity_main.xml",
     'android:id="@+id/dotKodi"', 'android:id="@+id/dotMpv"'),
    (APP + "res/layout/activity_main.xml",
     '@string/daemon_kodi', '@string/daemon_mpv'),
    (APP + "res/menu/bottom_nav.xml",
     'android:id="@+id/nav_kodi"', 'android:id="@+id/nav_mpv"'),
    (APP + "res/menu/bottom_nav.xml",
     '@drawable/ic_nav_kodi', '@drawable/ic_nav_mpv'),
    (APP + "res/menu/bottom_nav.xml",
     '@string/nav_kodi', '@string/nav_mpv'),
    (APP + "res/menu/bottom_nav.xml",
     'android:id="@+id/nav_webdash"', 'android:id="@+id/nav_code"'),
    (APP + "res/menu/bottom_nav.xml",
     '@drawable/ic_nav_webdash', '@drawable/ic_nav_code'),
    (APP + "res/menu/bottom_nav.xml",
     '@string/nav_webdash', '@string/nav_code'),
    (APP + "res/values/strings.xml",
     '<string name="nav_kodi">Kodi</string>', '<string name="nav_mpv">mpv</string>'),
    (APP + "res/values/strings.xml",
     '<string name="nav_webdash">WebDash</string>', '<string name="nav_code">Code</string>'),
    (APP + "res/values/strings.xml",
     '<string name="daemon_kodi">Kodi</string>', '<string name="daemon_mpv">mpv</string>'),
]

VECTOR_HEAD = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:width="24dp"\n'
    '    android:height="24dp"\n'
    '    android:viewportWidth="24"\n'
    '    android:viewportHeight="24">\n'
)

IC_MPV = VECTOR_HEAD + (
    '    <path\n'
    '        android:fillColor="#FFFFFFFF"\n'
    '        android:pathData="M8,5v14l11,-7z" />\n'
    '</vector>\n'
)

IC_CODE = VECTOR_HEAD + (
    '    <path\n'
    '        android:fillColor="#FFFFFFFF"\n'
    '        android:pathData="M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z" />\n'
    '</vector>\n'
)

NEW_FILES = {
    APP + "res/drawable/ic_nav_mpv.xml": IC_MPV,
    APP + "res/drawable/ic_nav_code.xml": IC_CODE,
}

REMOVE = [
    APP + "res/drawable/ic_nav_kodi.xml",
    APP + "res/drawable/ic_nav_webdash.xml",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    # Kelompokkan suntingan per berkas, terapkan berurutan.
    order = []
    for path, _, _ in EDITS:
        if path not in order:
            order.append(path)

    writes = []   # (path, teks, eol)
    removes = []
    for path in order:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        new = text
        changed = False
        for p, old, rep in EDITS:
            if p != path:
                continue
            n = new.count(old)
            if n == 1:
                new = new.replace(old, rep, 1)
                changed = True
            elif n == 0 and new.count(rep) >= 1:
                continue
            else:
                die("%s: pola harus tepat 1 kali, ditemukan %d: %r" % (path, n, old))
        if not changed:
            print("SUDAH: " + path)
            continue
        diff = difflib.unified_diff(
            text.splitlines(), new.splitlines(),
            "sebelum/" + path, "sesudah/" + path, lineterm="", n=1,
        )
        print("\n".join(diff))
        writes.append((path, new, eol))

    for path, content in NEW_FILES.items():
        if os.path.exists(path):
            existing, _ = load(path)
            if existing == content:
                print("SUDAH: " + path)
                continue
            die(path + " sudah ada dengan isi berbeda; periksa manual")
        print("BARU: %s (%d baris)" % (path, content.count("\n")))
        writes.append((path, content, "\n"))

    for path in REMOVE:
        if os.path.exists(path):
            print("HAPUS: " + path)
            removes.append(path)
        else:
            print("SUDAH: " + path + " tidak ada")

    if not writes and not removes:
        print("Tidak ada perubahan (sudah diterapkan).")
        return

    if not args.apply:
        print("DRY-RUN: jalankan lagi dengan --apply")
        return

    for path, text, eol in writes:
        save(path, text, eol)
    for path in removes:
        os.remove(path)
    print("DITERAPKAN: %d ditulis, %d dihapus" % (len(writes), len(removes)))


if __name__ == "__main__":
    main()
