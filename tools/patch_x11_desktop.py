#!/usr/bin/env python3
"""Desktop X11 siap pakai: autostart, menu Openbox, font xterm Xft, dan PyXDG.

Penyebab layar kosong dan pesan galat saat molina-x11 desktop:
  - openbox-xdg-autostart butuh PyXDG (paket python3-xdg),
  - debian-menu.xml tidak ada (paket menu tidak terpasang),
  - font core X "fixed" tidak terlihat server host; xterm harus memakai font Xft,
  - tidak ada autostart, jadi hanya latar hitam.
Patch ini:
  1. MolinaShellProfile: fungsi __molina_x11_prepare di blok molina-x11, dipanggil sebelum
     desktop dijalankan. Membuat ~/.Xresources, ~/.config/openbox/autostart dan menu.xml
     hanya bila belum ada (berkas milik pengguna tidak pernah ditimpa).
  2. PackageSpec: menambah python3-xdg ke paket "Desktop X11".
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_desktop.py [--apply]
"""
import argparse
import difflib
import os
import sys

PROFILE = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/MolinaShellProfile.java"
PACKAGE_SPEC = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/packages/PackageSpec.kt"

PREPARE_SH = r"""__molina_x11_prepare() {
	local cfg="$HOME/.config/openbox" res="$HOME/.Xresources"
	mkdir -p "$cfg" || return 1
	if [ ! -f "$res" ]; then
		printf '%s\n' 'XTerm*faceName: DejaVu Sans Mono' 'XTerm*faceSize: 11' 'XTerm*saveLines: 4000' > "$res" || return 1
	fi
	if [ ! -f "$cfg/autostart" ]; then
		printf '%s\n' 'xrdb -merge "$HOME/.Xresources"' 'pcmanfm --desktop &' 'xterm &' > "$cfg/autostart" || return 1
	fi
	if [ ! -f "$cfg/menu.xml" ]; then
		printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?>' \
			'<openbox_menu xmlns="http://openbox.org/3.4/menu">' \
			'<menu id="root-menu" label="Molina">' \
			'<item label="Terminal"><action name="Execute"><command>xterm</command></action></item>' \
			'<item label="Berkas"><action name="Execute"><command>pcmanfm</command></action></item>' \
			'<separator />' \
			'<item label="Mulai ulang Openbox"><action name="Restart" /></item>' \
			'<item label="Keluar desktop"><action name="Exit" /></item>' \
			'</menu>' '</openbox_menu>' > "$cfg/menu.xml" || return 1
	fi
}"""


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


def java_line(shell_line):
    body = shell_line.replace("\\", "\\\\").replace('"', '\\"').replace("\t", "\\t")
    return '            + "' + body + '\\n"\n'


def patch_profile():
    text, eol = load(PROFILE)
    if "__molina_x11_prepare" in text:
        print("MolinaShellProfile: sudah")
        return None
    anchor = '            + "molina-x11() {\\n"\n'
    if text.count(anchor) != 1:
        die("jangkar molina-x11() tidak ditemukan di MolinaShellProfile.java")
    block = "".join(java_line(l) for l in PREPARE_SH.split("\n"))
    new = text.replace(anchor, block + anchor)
    launch = '            + "\\t\\t\\t( __molina_x11_env && exec nohup dbus-launch --exit-with-session openbox-session ) >\\"$log\\" 2>&1 &\\n"\n'
    if new.count(launch) != 1:
        die("baris peluncuran openbox-session tidak ditemukan di MolinaShellProfile.java")
    call = java_line("\t\t\t__molina_x11_prepare || return 1")
    new = new.replace(launch, call + launch)
    return PROFILE, text, new, eol


def patch_spec():
    text, eol = load(PACKAGE_SPEC)
    if '"python3-xdg"' in text:
        print("PackageSpec: sudah")
        return None
    old = '"fonts-dejavu-core"),\n            optional = true'
    if text.count(old) != 1:
        die("daftar paket x11desktop tidak ditemukan di PackageSpec.kt")
    new = text.replace(old, '"python3-xdg", "fonts-dejavu-core"),\n            optional = true')
    return PACKAGE_SPEC, text, new, eol


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
        diff = difflib.unified_diff(old.splitlines(), new.splitlines(), path, path, lineterm="", n=1)
        print("\n".join(diff))
        if args.apply:
            save(path, new, eol)
            print("DITULIS: " + path)
    if not args.apply:
        print("\nDry-run. Jalankan lagi dengan --apply untuk menulis.")


if __name__ == "__main__":
    main()
