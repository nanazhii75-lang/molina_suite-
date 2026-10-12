#!/usr/bin/env python3
"""Judul notifikasi service terminal: "Termux" menjadi "Molina Terminal".

Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_notif_title.py [--apply]
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
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


SERVICE = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/TermuxService.java"
STRINGS = "engines/termux/app/src/main/res/values/strings.xml"
CHANGES = "engines/termux/MOLINA-CHANGES.md"

OLD_CALL = "TermuxConstants.TERMUX_APP_NAME, notificationText, null,"
NEW_CALL = "getString(R.string.notification_title), notificationText, null,"

STR_ANCHOR = '<string name="notification_action_exit">Exit</string>'
STR_NEW = '<string name="notification_title">Molina Terminal</string>'

NOTE_MARK = "notification_title"
NOTE = (
    "\n## Notifikasi service\n"
    "- `app/.../TermuxService.java` dan `app/src/main/res/values/strings.xml`: judul notifikasi "
    "foreground service memakai string `notification_title` (\"Molina Terminal\"), bukan "
    "`TermuxConstants.TERMUX_APP_NAME`.\n"
)


def edit_service(text):
    n = text.count(OLD_CALL)
    if n == 1:
        return text.replace(OLD_CALL, NEW_CALL, 1)
    if n == 0 and text.count(NEW_CALL) == 1:
        return text
    die("%s: pola harus tepat 1 kali, ditemukan %d" % (SERVICE, n))


def edit_strings(text):
    if 'name="notification_title"' in text:
        return text
    n = text.count(STR_ANCHOR)
    if n != 1:
        die("%s: jangkar harus tepat 1 kali, ditemukan %d" % (STRINGS, n))
    return text.replace(STR_ANCHOR, STR_NEW + "\n    " + STR_ANCHOR, 1)


def edit_changes(text):
    if NOTE_MARK in text:
        return text
    return text.rstrip("\n") + "\n" + NOTE


JOBS = [(SERVICE, edit_service), (STRINGS, edit_strings), (CHANGES, edit_changes)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    writes = []
    for path, fn in JOBS:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        new = fn(text)
        if new == text:
            print("SUDAH: " + path)
            continue
        diff = difflib.unified_diff(
            text.splitlines(), new.splitlines(),
            "sebelum/" + path, "sesudah/" + path, lineterm="", n=1,
        )
        print("\n".join(diff))
        writes.append((path, new, eol))

    if not writes:
        print("Tidak ada perubahan (sudah diterapkan).")
        return
    if not args.apply:
        print("DRY-RUN: jalankan lagi dengan --apply")
        return
    for path, text, eol in writes:
        save(path, text, eol)
    print("DITERAPKAN: %d berkas" % len(writes))


if __name__ == "__main__":
    main()
