#!/usr/bin/env python3
"""COPY di X11 menyalin isi layar xterm ke clipboard, seperti COPY di Molina Terminal.

Sebelumnya COPY hanya mengirim Ctrl+Shift+C (menyalin seleksi), sehingga tanpa seleksi tidak ada
yang tersalin. Sekarang di xterm, Ctrl+Shift+C menjalankan print-everything: seluruh layar dan
scrollback dikirim sebagai teks polos ke clipboard X lewat xclip, tanpa baris kosong di ujung.
Clipboard X tersinkron ke clipboard Android, jadi hasilnya bisa langsung di-paste di aplikasi lain.
Menyeleksi teks dengan pointer juga otomatis masuk ke clipboard (selectToClipboard).
Perubahan:
  1. MolinaShellProfile: __molina_x11_prepare membuat ~/.local/bin/molina-copy-screen dan blok
     aturan xterm bertanda "molina-xterm-copy" di ~/.Xresources. Blok lama dari patch_x11_keys.py
     (hanya copy-selection) diganti.
  2. PackageSpec: menambah xclip ke paket "Desktop X11".
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_copy.py [--apply]
"""
import argparse
import difflib
import os
import sys

PROFILE = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/MolinaShellProfile.java"
PACKAGE_SPEC = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/packages/PackageSpec.kt"

OLD_XRES_SH = r"""	if ! grep -q 'copy-selection' "$res"; then
		printf '%s\n' 'XTerm*VT100.translations: #override \n\' \
			'	Ctrl Shift <Key>C: copy-selection(CLIPBOARD) \n\' \
			'	Ctrl Shift <Key>V: insert-selection(CLIPBOARD)' >> "$res" || return 1
	fi"""

NEW_XRES_SH = r"""	local bin="$HOME/.local/bin"
	mkdir -p "$bin" || return 1
	if [ ! -f "$bin/molina-copy-screen" ]; then
		cat > "$bin/molina-copy-screen" <<-'MOLINA_EOF' || return 1
			#!/bin/sh
			# Menyalin teks dari stdin (isi layar xterm) ke clipboard X tanpa baris kosong di ujung.
			sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | xclip -selection clipboard
		MOLINA_EOF
		chmod +x "$bin/molina-copy-screen" || return 1
	fi
	if ! grep -q 'molina-xterm-copy' "$res"; then
		if grep -q 'copy-selection(CLIPBOARD)' "$res"; then
			sed -i '/^XTerm\*VT100\.translations: #override/,/insert-selection(CLIPBOARD)$/d' "$res" || return 1
		fi
		printf '%s\n' '! molina-xterm-copy' \
			'XTerm*VT100.selectToClipboard: true' \
			'XTerm*VT100.printAttributes: 0' \
			'XTerm*VT100.printerAutoClose: true' \
			"XTerm*VT100.printerCommand: $bin/molina-copy-screen" \
			'XTerm*VT100.translations: #override \n\' \
			'	Ctrl Shift <Key>C: print-everything() \n\' \
			'	Ctrl Shift <Key>V: insert-selection(CLIPBOARD)' >> "$res" || return 1
	fi"""


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


def java_lines(shell_text):
    out = ""
    for line in shell_text.split("\n"):
        body = line.replace("\\", "\\\\").replace('"', '\\"').replace("\t", "\\t")
        out += '            + "' + body + '\\n"\n'
    return out


def patch_profile():
    text, eol = load(PROFILE)
    if "molina-xterm-copy" in text:
        print("MolinaShellProfile.java: sudah")
        return None
    old = java_lines(OLD_XRES_SH)
    if text.count(old) == 1:
        return PROFILE, text, text.replace(old, java_lines(NEW_XRES_SH)), eol
    anchor = java_lines('\tif [ ! -f "$cfg/autostart" ]; then').rstrip("\n") + "\n"
    if text.count(anchor) == 1:
        return PROFILE, text, text.replace(anchor, java_lines(NEW_XRES_SH) + anchor), eol
    die("fungsi __molina_x11_prepare tidak ditemukan; terapkan tools/patch_x11_desktop.py lebih dulu")


def patch_spec():
    text, eol = load(PACKAGE_SPEC)
    if '"xclip"' in text:
        print("PackageSpec.kt: sudah")
        return None
    old = '"python3-xdg", "fonts-dejavu-core"'
    if text.count(old) != 1:
        die("daftar paket x11desktop tidak ditemukan; terapkan tools/patch_x11_desktop.py lebih dulu")
    return PACKAGE_SPEC, text, text.replace(old, '"python3-xdg", "xclip", "fonts-dejavu-core"'), eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir("features") or not os.path.isdir("engines"):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (patch_profile(), patch_spec()) if c]
    if not changes:
        print("Tidak ada perubahan (semua sudah diterapkan).")
        return
    for path, old, new, eol in changes:
        print("\n".join(difflib.unified_diff(old.splitlines(), new.splitlines(), path, path, lineterm="", n=1)))
        if args.apply:
            save(path, new, eol)
            print("DITULIS: " + path)
    if not args.apply:
        print("\nDry-run. Jalankan lagi dengan --apply untuk menulis.")


if __name__ == "__main__":
    main()
