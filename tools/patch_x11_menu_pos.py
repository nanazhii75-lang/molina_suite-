#!/usr/bin/env python3
"""Ikon menu X11: default di kanan bawah (mudah dijangkau jempol) dan bisa digeser.

  - Seret ikon ke mana saja di layar monitor; posisinya disimpan (sebagai pecahan lebar/tinggi
    layar, jadi tetap wajar setelah rotasi) dan dipakai lagi pada pembukaan berikutnya.
  - Ketukan biasa tetap membuka/menutup panel.
  - Panel muncul di sisi ikon yang paling lapang: di atas ikon bila ikon di separuh bawah, di
    bawah ikon bila di separuh atas; rata kanan atau kiri mengikuti ikon dan selalu di dalam layar.
  - Saat keyboard muncul, ikon dan panel naik supaya tidak tertutup.
Kelas X11MenuPlacement.java dibuat lebih dulu lewat heredoc terpisah (penempatan dan geser
dipisah dari X11MenuController); skrip ini memasangnya ke layout, controller, dan MainActivity.
Butuh patch_x11_menu.py dan patch_x11_display.py lebih dulu.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_menu_pos.py [--apply]
"""
import argparse
import difflib
import os
import sys

LORIE = "engines/termux-x11/lorie/src/main"
PLACEMENT_JAVA = LORIE + "/java/com/molina/suite/x11/X11MenuPlacement.java"
CTRL_JAVA = LORIE + "/java/com/molina/suite/x11/X11MenuController.java"
MAIN_JAVA = LORIE + "/java/com/molina/suite/x11/MainActivity.java"
LAYOUT = LORIE + "/res/layout/main_activity.xml"

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
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


def swap(text, old, new, label):
    if text.count(old) != 1:
        die("jangkar '%s' ditemukan %d kali (harus 1)" % (label, text.count(old)))
    return text.replace(old, new)


def patch_layout():
    text, eol = load(LAYOUT)
    old = ('        android:id="@+id/x11_menu_button"\n'
           '        android:layout_width="44dp"\n'
           '        android:layout_height="44dp"\n'
           '        android:layout_gravity="top|end"\n')
    if old not in text:
        if 'android:id="@+id/x11_menu_button"' in text:
            print("main_activity.xml: sudah")
            return None
        die("ikon menu belum ada; terapkan tools/patch_x11_menu.py lebih dulu")
    new = old.replace("top|end", "bottom|end")
    return LAYOUT, text, swap(text, old, new, "gravity ikon menu"), eol


def patch_controller():
    text, eol = load(CTRL_JAVA)
    if "X11MenuPlacement" in text:
        print("X11MenuController.java: sudah")
        return None
    if "X11DisplayOptions" not in text:
        die("terapkan tools/patch_x11_display.py lebih dulu")
    t = text
    t = swap(t, "    private X11DisplayOptions displayOptions;\n",
             "    private X11DisplayOptions displayOptions;\n    private X11MenuPlacement placement;\n", "field displayOptions")
    t = swap(t, "        displayOptions = new X11DisplayOptions(activity);\n",
             "        displayOptions = new X11DisplayOptions(activity);\n"
             "        placement = new X11MenuPlacement(activity, menuButton, panel);\n"
             "        placement.attach();\n", "buat displayOptions")
    t = swap(t, "            panel.setVisibility(View.VISIBLE);\n",
             "            panel.setVisibility(View.VISIBLE);\n            placement.update();\n", "tampilkan panel")
    t = swap(t, "    boolean isOpen() {\n        return open;\n    }\n",
             "    boolean isOpen() {\n        return open;\n    }\n\n"
             "    /** Tinggi keyboard berubah: ikon dan panel naik supaya tidak tertutup. */\n"
             "    void onImeHeightChanged(int height) {\n"
             "        if (placement != null)\n"
             "            placement.setImeHeight(height);\n"
             "    }\n", "isOpen")
    return CTRL_JAVA, text, t, eol


def patch_main():
    text, eol = load(MAIN_JAVA)
    if "onImeHeightChanged" in text:
        print("MainActivity.java: sudah")
        return None
    old = "        imeHeight = height;\n        refreshContentInsets();\n    }\n"
    new = ("        imeHeight = height;\n"
           "        if (x11Menu != null)\n"
           "            x11Menu.onImeHeightChanged(height);\n"
           "        refreshContentInsets();\n    }\n")
    return MAIN_JAVA, text, swap(text, old, new, "setImeHeight"), eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir(LORIE):
        die("jalankan dari root repo molina-suite")
    if not os.path.isfile(PLACEMENT_JAVA):
        die("berkas " + PLACEMENT_JAVA + " belum dibuat; jalankan blok heredoc pertama lebih dulu")
    changes = [c for c in (patch_layout(), patch_controller(), patch_main()) if c]
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
