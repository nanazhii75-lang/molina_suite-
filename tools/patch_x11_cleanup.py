#!/usr/bin/env python3
"""Bersihkan sisa bar extra keys lama di X11 (sudah digantikan menu dropdown).

Dibuang:
  - Pengaturan tak berfungsi: showAdditionalKbd, additionalKbdVisible, adjustHeightForEK,
    ekbarPosition, ekbarPositionIgnoreOrientation, opacityEKBar (xml, string, array, dan
    semua rujukan di LoriePreferences/MainActivity).
  - Logika tata letak bar: posisi pager, rotasi, margin keyboard, inset bar.
  - ViewPager dan view spasi bawah di main_activity.xml.
Dipertahankan: useTermuxEKBarBehaviour dan extra_keys_config (masih dipakai).
MainActivity: setTerminalToolbarView() -> refreshMenuAndInsets(),
setTerminalToolbarViewLayout() -> refreshContentInsets(). Inset keyboard tetap bekerja.
ExtraKeysView: popup tombol tidak lagi bergantung pada posisi bar (selalu muncul di atas tombol).
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_cleanup.py [--apply]
"""
import argparse
import difflib
import os
import re
import sys

LORIE = "engines/termux-x11/lorie/src/main"
MAIN_JAVA = LORIE + "/java/com/molina/suite/x11/MainActivity.java"
KEYS_VIEW = LORIE + "/java/com/molina/suite/x11/extrakeys/ExtraKeysView.java"
PREF_JAVA = LORIE + "/java/com/molina/suite/x11/LoriePreferences.java"
PREF_XML = LORIE + "/res/xml/preferences.xml"
STRINGS = LORIE + "/res/values/strings.xml"
ARRAYS = LORIE + "/res/values/arrays.xml"
LAYOUT = LORIE + "/res/layout/main_activity.xml"

NEW_REGION = r"""    /** Menyegarkan menu dropdown (ikon disembunyikan saat PiP) dan inset layar. */
    private void refreshMenuAndInsets() {
        if (x11Menu != null)
            x11Menu.refresh(!isInPictureInPictureMode);

        refreshContentInsets();
        getLorieView().requestFocus();
    }

    private int imeHeight = 0;

    private void refreshContentInsets() {
        handler.post(() -> {
            applyContentInsets();
            makeSureHelpersAreVisibleAndInScreenBounds();
        });
    }

    /** Keyboard mendorong layar hanya bila Reseed aktif; selain itu ia hanya menutupi bagian bawahnya. */
    private void applyContentInsets() {
        int imeContentInset = prefs.Reseed.get() ? imeHeight : 0;
        getLorieView().setContentInsets(0, 0, 0, imeContentInset);
        getLorieView().setObscuredBottom(imeHeight - imeContentInset);
    }

"""

NEW_GLOBAL_LAYOUT = r"""            int savedRotation;

            @Override
            public void onGlobalLayout() {
                Display d = frm.getDisplay();
                if (d != null && savedRotation != d.getRotation()) {
                    savedRotation = d.getRotation();
                    refreshContentInsets();
                }
            }
"""

PAGER_LOCAL = "        final ViewPager pager = getTerminalToolbarViewPager();\n"


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


def exactly(text, old, n, label):
    if text.count(old) != n:
        die("jangkar '%s' ditemukan %d kali (harus %d)" % (label, text.count(old), n))


def swap(text, old, new, label, n=1):
    exactly(text, old, n, label)
    return text.replace(old, new)


def cut(text, start, end, replacement, label):
    exactly(text, start, 1, label + " (awal)")
    i = text.index(start)
    j = text.index(end, i)
    return text[:i] + replacement + text[j:]


def patch_main():
    text, eol = load(MAIN_JAVA)
    if "getTerminalToolbarViewPager" not in text:
        print("MainActivity.java: sudah")
        return None
    if "x11Menu" not in text:
        die("menu dropdown belum ada; terapkan tools/patch_x11_menu.py lebih dulu")
    t = text
    t = swap(t, "import androidx.viewpager.widget.ViewPager;\n", "", "import ViewPager")

    t = cut(t, "            String savedPos;\n", "        });\n\n        ImeHeightProvider.assistActivity(this);",
            NEW_GLOBAL_LAYOUT, "global layout listener")
    # cut() keeps the closing "        });" because end marker starts there

    # getVisibleFrmRect: tanpa bar
    t = swap(t, "        final ViewPager pager = getTerminalToolbarViewPager();\n        final LorieView lorieView = getLorieView();\n        final View sharedParent",
             "        final LorieView lorieView = getLorieView();\n        final View sharedParent", "getVisibleFrmRect pager")
    t = cut(t, "        // With adjustHeightForEK on, availableRect already excludes the bar's own space.\n",
            "        return result;\n", "", "blok bar di getVisibleFrmRect")
    t = swap(t, "        result.bottom = Math.min(result.bottom, sharedParent.getHeight() - imeHeight);\n\n        return result;",
             "        result.bottom = Math.min(result.bottom, sharedParent.getHeight() - imeHeight);\n        return result;",
             "rapikan getVisibleFrmRect")

    # wilayah bar: dari getter pager sampai sebelum setImeHeight
    t = cut(t, "    public ViewPager getTerminalToolbarViewPager() {\n",
            "    public void setImeHeight(int height) {", NEW_REGION, "wilayah bar")

    t = swap(t, "        final ViewPager pager = getTerminalToolbarViewPager();\n        pager.setAlpha(isInPictureInPictureMode ? 0.f : prefs.adjustHeightForEK.get() ? 1.f : ((float) prefs.opacityEKBar.get())/100);\n",
             "", "alpha pager di PiP")

    # variabel lokal pager yang tidak terpakai
    t = swap(t, PAGER_LOCAL, "", "variabel pager lokal", n=3)

    t = swap(t, '    void onPreferencesChanged(String key) {\n        if ("additionalKbdVisible".equals(key))\n            return;\n\n',
             "    void onPreferencesChanged(String key) {\n", "early return additionalKbdVisible")
    t = swap(t, "        getTerminalToolbarViewPager().setAlpha(isInPictureInPictureMode ? 0.f : prefs.adjustHeightForEK.get() ? 1.f : ((float) prefs.opacityEKBar.get())/100);\n\n",
             "", "alpha pager di preferensi")

    t = t.replace("setTerminalToolbarViewLayout()", "refreshContentInsets()")
    t = t.replace("setTerminalToolbarView()", "refreshMenuAndInsets()")
    for sisa in ("getTerminalToolbarViewPager", "PAGER_POSITION", "ekbarPosition", "adjustHeightForEK", "opacityEKBar", "additionalKbdVisible"):
        if sisa in t:
            die("masih ada rujukan '%s' di MainActivity.java setelah pembersihan" % sisa)
    return MAIN_JAVA, text, t, eol


def patch_keys_view():
    text, eol = load(KEYS_VIEW)
    if "getPagerPosition" not in text:
        print("ExtraKeysView.java: sudah")
        return None
    t = text
    t = swap(t, "import static com.molina.suite.x11.MainActivity.*;\n", "", "import static MainActivity")
    t = swap(t, "        boolean reverseRows = activity.getPagerPosition() == PAGER_POSITION_TOP;\n\n", "", "reverseRows")
    t = swap(t, "            int actualRow = reverseRows ? buttons.length - 1 - row : row;\n", "", "actualRow")
    t = swap(t, "actualRow", "row", "pemakaian actualRow", n=2)
    t = swap(t, "        int pos = activity.getPagerPosition();\n"
                "        int width = pos == PAGER_POSITION_TOP || pos == PAGER_POSITION_BOTTOM ? view.getMeasuredWidth() : view.getMeasuredHeight();\n"
                "        int height = pos == PAGER_POSITION_TOP || pos == PAGER_POSITION_BOTTOM ? view.getMeasuredHeight() : view.getMeasuredWidth();\n",
             "        int width = view.getMeasuredWidth();\n        int height = view.getMeasuredHeight();\n", "ukuran popup")
    t = swap(t, "        switch (pos) {\n"
                "            case PAGER_POSITION_TOP: mPopupWindow.showAsDropDown(view, 0, 0); break;\n"
                "            case PAGER_POSITION_BOTTOM: mPopupWindow.showAsDropDown(view, 0, -2 * height); break;\n"
                "            case PAGER_POSITION_LEFT: mPopupWindow.showAsDropDown(view, 0, -width); break;\n"
                "            case PAGER_POSITION_RIGHT: mPopupWindow.showAsDropDown(view, -width, -height -width); break;\n"
                "        }\n",
             "        // Popup muncul tepat di atas tombol yang ditekan.\n        mPopupWindow.showAsDropDown(view, 0, -2 * height);\n", "posisi popup")
    t = swap(t, "(reverseRows ? (event.getY() > 0) : (event.getY() < 0))", "event.getY() < 0", "gestur geser atas")
    t = swap(t, "(reverseRows ? (event.getY() < 0) : (event.getY() > 0))", "event.getY() > 0", "gestur geser bawah")
    if "reverseRows" in t:
        die("masih ada rujukan 'reverseRows' di ExtraKeysView.java")
    return KEYS_VIEW, text, t, eol


def patch_pref_java():
    text, eol = load(PREF_JAVA)
    if "showAdditionalKbd" not in text:
        print("LoriePreferences.java: sudah")
        return None
    t = text
    t = swap(t, '            with("showAdditionalKbd", p -> p.setLayoutResource(R.layout.preference));\n', "", "with showAdditionalKbd")
    t = swap(t, '            if ("showAdditionalKbd".contentEquals(key) && (Boolean) newValue)\n                prefs().additionalKbdVisible.put(true);\n\n\n',
             "\n", "blok showAdditionalKbd di onPreferenceChange")
    t = swap(t, '                                    if ("showAdditionalKbd".contentEquals(key) && "true".contentEquals(newValue))\n'
                '                                        edit.putBoolean("additionalKbdVisible", true);\n',
             "", "blok showAdditionalKbd di respons jarak jauh")
    for sisa in ("showAdditionalKbd", "additionalKbdVisible"):
        if sisa in t:
            die("masih ada rujukan '%s' di LoriePreferences.java" % sisa)
    return PREF_JAVA, text, t, eol


def patch_pref_xml():
    text, eol = load(PREF_XML)
    if "adjustHeightForEK" not in text:
        print("preferences.xml: sudah")
        return None
    t = text
    for line in (
        '        <SwitchPreferenceCompat app:key="showAdditionalKbd" app:defaultValue="true" />\n',
        '        <!-- this preference should be a part of `Prefs` but should not appear in PreferenceScreen -->\n',
        '        <SwitchPreferenceCompat app:key="additionalKbdVisible" app:defaultValue="true" app:isPreferenceVisible="false" />\n',
        '        <SwitchPreferenceCompat app:key="adjustHeightForEK" app:defaultValue="true" />\n',
        '        <ListPreference app:key="ekbarPosition" app:defaultValue="bottom" app:entries="@array/ekbarPositionValues" app:entryValues="@array/ekbarPositionValues" />\n',
        '        <SwitchPreferenceCompat app:key="ekbarPositionIgnoreOrientation" app:defaultValue="false" />\n',
        '        <SeekBarPreference app:key="opacityEKBar" app:defaultValue="100" app:min="1" android:max="100" app:seekBarIncrement="1" app:showSeekBarValue="true" app:updatesContinuously="true" />\n',
    ):
        t = swap(t, line, "", line.strip()[:50])
    return PREF_XML, text, t, eol


def patch_strings():
    text, eol = load(STRINGS)
    if "lorie_pref_adjustHeightForEK" not in text:
        print("strings.xml: sudah")
        return None
    t = text
    for name in ("showAdditionalKbd", "showAdditionalKbd_summary", "adjustHeightForEK",
                 "ekbarPosition", "ekbarPositionIgnoreOrientation", "opacityEKBar"):
        pat = re.compile(r'^[ \t]*<string name="lorie_pref_%s">.*</string>\n' % re.escape(name), re.M)
        found = pat.findall(t)
        if len(found) != 1:
            die("string lorie_pref_%s ditemukan %d kali (harus 1)" % (name, len(found)))
        t = pat.sub("", t)
    return STRINGS, text, t, eol


def patch_arrays():
    text, eol = load(ARRAYS)
    if "ekbarPositionValues" not in text:
        print("arrays.xml: sudah")
        return None
    pat = re.compile(r'    <string-array name="ekbarPositionValues">.*?</string-array>\n', re.S)
    if len(pat.findall(text)) != 1:
        die("array ekbarPositionValues tidak ditemukan tepat sekali")
    return ARRAYS, text, pat.sub("", text), eol


def patch_layout():
    text, eol = load(LAYOUT)
    if "terminal_toolbar_view_pager" not in text:
        print("main_activity.xml: sudah")
        return None
    pat = re.compile(r'    <androidx\.viewpager\.widget\.ViewPager\n.*?/>\n\n    <View\n        android:id="@\+id/activity_termux_bottom_space_view"\n.*?/>\n\n', re.S)
    if len(pat.findall(text)) != 1:
        die("blok ViewPager dan view spasi bawah tidak ditemukan tepat sekali")
    return LAYOUT, text, pat.sub("", text), eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir(LORIE):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (
        patch_main(), patch_keys_view(), patch_pref_java(), patch_pref_xml(),
        patch_strings(), patch_arrays(), patch_layout(),
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
