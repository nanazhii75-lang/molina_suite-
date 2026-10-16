#!/usr/bin/env python3
"""Extra keys X11 disamakan dengan Molina Terminal.

Susunan baru (dua baris, tujuh kolom, sama dengan terminal):
  ESC  ☰  -(|)  HOME  UP  CLEAR  COPY
  TAB  CTRL  ALT  LEFT  DOWN  RIGHT  PASTE
Perilaku di X11:
  - COPY  : mengirim Ctrl+Shift+C ke aplikasi yang fokus.
  - CLEAR : mengirim Ctrl+L.
  - PASTE : sudah ada (isi clipboard Android diketik ke X11); labelnya kini teks "PASTE".
  - ☰     : membuka Pengaturan X11 (tidak ada drawer di X11).
KEYBOARD (tampil/sembunyi keyboard) pindah ke baris ikon di panel menu.
xterm: aturan Ctrl+Shift+C/V ke CLIPBOARD ditambahkan ke ~/.Xresources oleh molina-x11 desktop
(dilewati bila patch_x11_desktop.py belum diterapkan).
Pengaturan "Extra keys config" yang pernah disimpan pengguna tidak ditimpa; untuk beralih ke
susunan baru gunakan tombol reset di dialog Extra keys config.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_keys.py [--apply]
"""
import argparse
import difflib
import os
import re
import sys

LORIE = "engines/termux-x11/lorie/src/main"
KEYS_JAVA = LORIE + "/java/com/molina/suite/x11/utils/TermuxX11ExtraKeys.java"
CONSTANTS_JAVA = LORIE + "/java/com/molina/suite/x11/extrakeys/ExtraKeysConstants.java"
CTRL_JAVA = LORIE + "/java/com/molina/suite/x11/X11MenuController.java"
LAYOUT = LORIE + "/res/layout/main_activity.xml"
STRINGS = LORIE + "/res/values/strings.xml"
PROFILE = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/MolinaShellProfile.java"

DEFAULT_KEYS = ("    public static final String DEFAULT_IVALUE_EXTRA_KEYS = "
                "\"[['ESC','DRAWER',{key: '-', popup: '|'},'HOME','UP','CLEAR','COPY'], "
                "['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PASTE']]\"; // Double row\n")

COMBO_BRANCHES = r"""        else if ("COPY".equals(key))
            sendKeyCombo(KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT);
        else if ("CLEAR".equals(key))
            sendKeyCombo(KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_CTRL_LEFT);
"""

COMBO_HELPER = r"""    /**
     * Mengirim satu kombinasi tombol ke X11: modifier ditekan berurutan, tombol utama ditekan dan
     * dilepas, lalu modifier dilepas berkebalikan. Modifier yang menempel di extra keys ikut dilepas.
     */
    private void sendKeyCombo(int key, int... modifiers) {
        for (int modifier : modifiers)
            mActivity.getLorieView().sendKeyEvent(0, modifier, true);
        mActivity.getLorieView().sendKeyEvent(0, key, true);
        mActivity.getLorieView().sendKeyEvent(0, key, false);
        for (int i = modifiers.length - 1; i >= 0; i--)
            mActivity.getLorieView().sendKeyEvent(0, modifiers[i], false);
        unsetSpecialKeys();
    }

"""

KEYBOARD_BUTTON = r"""            <ImageButton
                android:id="@+id/x11_menu_keyboard"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_keyboard_text"
                android:padding="10dp"
                android:src="@drawable/ic_extra_key_keyboard" />

"""

XRESOURCES_SH = r"""	if ! grep -q 'copy-selection' "$res"; then
		printf '%s\n' 'XTerm*VT100.translations: #override \n\' \
			'	Ctrl Shift <Key>C: copy-selection(CLIPBOARD) \n\' \
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


def once(text, old, label):
    if text.count(old) != 1:
        die("jangkar '%s' ditemukan %d kali (harus 1)" % (label, text.count(old)))


def java_line(shell_line):
    body = shell_line.replace("\\", "\\\\").replace('"', '\\"').replace("\t", "\\t")
    return '            + "' + body + '\\n"\n'


def patch_keys():
    text, eol = load(KEYS_JAVA)
    if '"COPY".equals(key)' in text:
        print("TermuxX11ExtraKeys.java: sudah")
        return None
    new = text
    m = re.findall(r"^    public static final String DEFAULT_IVALUE_EXTRA_KEYS = .*\n", new, re.M)
    if len(m) != 1:
        die("konstanta DEFAULT_IVALUE_EXTRA_KEYS ditemukan %d kali (harus 1)" % len(m))
    new = new.replace(m[0], DEFAULT_KEYS)

    anchor = '        else if ("ZOOM_IN".equals(key))\n'
    once(new, anchor, "ZOOM_IN")
    new = new.replace(anchor, COMBO_BRANCHES + anchor)

    doc = "    /**\n     * Set the terminal extra keys and style.\n     */\n"
    once(new, doc, "setExtraKeys doc")
    new = new.replace(doc, COMBO_HELPER + doc)
    return KEYS_JAVA, text, new, eol


def patch_constants():
    text, eol = load(CONSTANTS_JAVA)
    m = re.findall(r'^[ \t]*put\("PASTE", .*\n', text, re.M)
    if not m:
        print("ExtraKeysConstants.java: sudah")
        return None
    if len(m) != 1:
        die("pemetaan label PASTE ditemukan %d kali (harus 1)" % len(m))
    return CONSTANTS_JAVA, text, text.replace(m[0], ""), eol


def patch_layout():
    text, eol = load(LAYOUT)
    if "x11_menu_keyboard" in text:
        print("main_activity.xml: sudah")
        return None
    if "x11_menu_displays" not in text:
        die("panel menu belum ada; terapkan tools/patch_x11_menu.py lebih dulu")
    anchor = '            <ImageButton\n                android:id="@+id/x11_menu_displays"'
    once(text, anchor, "tombol displays")
    return LAYOUT, text, text.replace(anchor, KEYBOARD_BUTTON + anchor), eol


def patch_strings():
    text, eol = load(STRINGS)
    if "x11_menu_keyboard_text" in text:
        print("strings.xml: sudah")
        return None
    anchor = '    <string name="x11_menu_button_text">Menu</string>\n'
    once(text, anchor, "string x11_menu_button_text")
    return STRINGS, text, text.replace(anchor, anchor + '    <string name="x11_menu_keyboard_text">Keyboard</string>\n'), eol


def patch_controller():
    text, eol = load(CTRL_JAVA)
    if "x11_menu_keyboard" in text:
        print("X11MenuController.java: sudah")
        return None
    anchor = "        bind(R.id.x11_menu_displays, v -> activity.showDisplaysDialog());\n"
    once(text, anchor, "bind displays")
    new = text.replace(anchor, "        bind(R.id.x11_menu_keyboard, v -> activity.toggleKeyboardVisibility());\n" + anchor)
    return CTRL_JAVA, text, new, eol


def patch_profile():
    text, eol = load(PROFILE)
    if "copy-selection" in text:
        print("MolinaShellProfile.java: sudah")
        return None
    anchor = java_line("\tif [ ! -f \"$cfg/autostart\" ]; then")
    if text.count(anchor) != 1:
        print("MolinaShellProfile.java: DILEWATI (patch_x11_desktop.py belum diterapkan); "
              "COPY di xterm baru bekerja setelah aturan Ctrl+Shift+C ditambahkan")
        return None
    block = "".join(java_line(l) for l in XRESOURCES_SH.split("\n"))
    return PROFILE, text, text.replace(anchor, block + anchor), eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir(LORIE):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (
        patch_keys(), patch_constants(), patch_layout(),
        patch_strings(), patch_controller(), patch_profile(),
    ) if c]
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
