#!/usr/bin/env python3
"""Opsi tampilan cepat di menu dropdown X11: layar penuh, orientasi, dan skala.

Satu baris baru di panel menu (di atas baris ikon):
  [layar penuh] [orientasi] [-] [skala %] [+]
  - Layar penuh : menyalakan/mematikan pengaturan "fullscreen" (ikon redup = mati).
  - Orientasi   : berputar auto -> portrait -> landscape (reverse tetap lewat Pengaturan).
  - Skala       : mengubah "displayScale" 30..300% per 10. Skala hanya berlaku pada mode
                  resolusi "scaled", jadi bila mode lain aktif, ketukan pertama pindah ke
                  "scaled" mulai dari 100% (terlihat lewat toast). Skala lebih besar = tampilan
                  lebih besar (resolusi X lebih kecil).
Semua perubahan memakai penyimpanan pengaturan yang sama dengan layar Pengaturan, jadi jalur
penerapannya (applyWindowSettings, hitung ulang resolusi) tidak berubah.
Butuh patch_x11_menu.py lebih dulu. Berkas baru: X11DisplayOptions.java dan dua drawable.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_display.py [--apply]
"""
import argparse
import difflib
import os
import sys

LORIE = "engines/termux-x11/lorie/src/main"
OPTIONS_JAVA = LORIE + "/java/com/molina/suite/x11/X11DisplayOptions.java"
CTRL_JAVA = LORIE + "/java/com/molina/suite/x11/X11MenuController.java"
LAYOUT = LORIE + "/res/layout/main_activity.xml"
STRINGS = LORIE + "/res/values/strings.xml"
DRAW_FULLSCREEN = LORIE + "/res/drawable/x11_ic_fullscreen.xml"
DRAW_ORIENTATION = LORIE + "/res/drawable/x11_ic_orientation.xml"

OPTIONS = r"""package com.molina.suite.x11;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Opsi tampilan cepat di menu dropdown X11: layar penuh, orientasi, dan skala tampilan. Semua
 * perubahan ditulis ke pengaturan yang sama dengan layar Pengaturan, sehingga penerapannya lewat
 * jalur yang sudah ada (MainActivity.onPreferencesChanged).
 */
final class X11DisplayOptions {
    private static final String MODE_SCALED = "scaled";
    private static final List<String> ORIENTATIONS = Arrays.asList("auto", "portrait", "landscape");
    private static final int SCALE_MIN = 30;
    private static final int SCALE_MAX = 300;
    private static final int SCALE_STEP = 10;
    private static final int SCALE_DEFAULT = 100;
    private static final float INACTIVE_ALPHA = 0.5f;

    private final MainActivity activity;
    private final View fullscreenButton;
    private final TextView scaleLabel;

    X11DisplayOptions(@NonNull MainActivity activity) {
        this.activity = activity;
        this.fullscreenButton = activity.findViewById(R.id.x11_menu_fullscreen);
        this.scaleLabel = activity.findViewById(R.id.x11_menu_scale_label);

        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        activity.findViewById(R.id.x11_menu_orientation).setOnClickListener(v -> cycleOrientation());
        activity.findViewById(R.id.x11_menu_scale_down).setOnClickListener(v -> changeScale(-SCALE_STEP));
        activity.findViewById(R.id.x11_menu_scale_up).setOnClickListener(v -> changeScale(SCALE_STEP));
        refresh();
    }

    /** Menyelaraskan tampilan kontrol dengan pengaturan saat ini. */
    void refresh() {
        Prefs prefs = activity.prefs;
        fullscreenButton.setAlpha(prefs.fullscreen.get() ? 1f : INACTIVE_ALPHA);

        boolean scaled = MODE_SCALED.equals(prefs.displayResolutionMode.get());
        scaleLabel.setText(scaled ? prefs.displayScale.get() + "%" : prefs.displayResolutionMode.get());
        scaleLabel.setAlpha(scaled ? 1f : INACTIVE_ALPHA);
    }

    private void toggleFullscreen() {
        Prefs prefs = activity.prefs;
        prefs.fullscreen.put(!prefs.fullscreen.get());
        refresh();
    }

    /** auto -> portrait -> landscape -> auto; nilai reverse dari Pengaturan kembali ke auto. */
    private void cycleOrientation() {
        Prefs prefs = activity.prefs;
        String next = ORIENTATIONS.get((ORIENTATIONS.indexOf(prefs.forceOrientation.get()) + 1) % ORIENTATIONS.size());
        prefs.forceOrientation.put(next);
        Toast.makeText(activity, activity.getString(R.string.x11_menu_orientation_changed, next), Toast.LENGTH_SHORT).show();
    }

    private void changeScale(int delta) {
        Prefs prefs = activity.prefs;
        boolean scaled = MODE_SCALED.equals(prefs.displayResolutionMode.get());
        int next = Math.max(SCALE_MIN, Math.min(SCALE_MAX, (scaled ? prefs.displayScale.get() : SCALE_DEFAULT) + delta));

        prefs.putInt("displayScale", next);
        if (!scaled) {
            prefs.displayResolutionMode.put(MODE_SCALED);
            Toast.makeText(activity, R.string.x11_menu_scale_mode_changed, Toast.LENGTH_SHORT).show();
        }
        refresh();
    }
}
"""

DRAWABLE_FULLSCREEN = r"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M4,9V4h5M20,9V4h-5M4,15v5h5M20,15v5h-5"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
"""

DRAWABLE_ORIENTATION = r"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M8,3h8a2,2 0 0 1 2,2v14a2,2 0 0 1 -2,2H8a2,2 0 0 1 -2,-2V5a2,2 0 0 1 2,-2zM11,18h2"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
"""

STRINGS_ADD = r"""    <string name="x11_menu_fullscreen_text">Layar penuh</string>
    <string name="x11_menu_orientation_text">Orientasi layar</string>
    <string name="x11_menu_orientation_changed">Orientasi: %1$s</string>
    <string name="x11_menu_scale_down_text">Kecilkan skala</string>
    <string name="x11_menu_scale_up_text">Besarkan skala</string>
    <string name="x11_menu_scale_mode_changed">Mode resolusi diubah ke skala</string>
"""

DISPLAY_ROW = r"""
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="44dp"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/x11_menu_fullscreen"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_fullscreen_text"
                android:padding="10dp"
                android:src="@drawable/x11_ic_fullscreen" />

            <ImageButton
                android:id="@+id/x11_menu_orientation"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_orientation_text"
                android:padding="10dp"
                android:src="@drawable/x11_ic_orientation" />

            <Button
                android:id="@+id/x11_menu_scale_down"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_scale_down_text"
                android:minWidth="0dp"
                android:minHeight="0dp"
                android:padding="0dp"
                android:text="-"
                android:textColor="@android:color/white"
                android:textSize="22sp" />

            <TextView
                android:id="@+id/x11_menu_scale_label"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1.4"
                android:gravity="center"
                android:textColor="@android:color/white"
                android:textSize="14sp" />

            <Button
                android:id="@+id/x11_menu_scale_up"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_scale_up_text"
                android:minWidth="0dp"
                android:minHeight="0dp"
                android:padding="0dp"
                android:text="+"
                android:textColor="@android:color/white"
                android:textSize="22sp" />
        </LinearLayout>
"""


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


def exactly(text, old, label):
    if text.count(old) != 1:
        die("jangkar '%s' ditemukan %d kali (harus 1)" % (label, text.count(old)))


def new_file(path, content):
    if os.path.isfile(path):
        cur, _ = load(path)
        if cur == content:
            print(os.path.basename(path) + ": sudah")
            return None
        die("berkas sudah ada dengan isi berbeda: " + path)
    return path, "", content, "\n"


def patch_layout():
    text, eol = load(LAYOUT)
    if "x11_menu_fullscreen" in text:
        print("main_activity.xml: sudah")
        return None
    if "x11_menu_extra_keys" not in text:
        die("panel menu belum ada; terapkan tools/patch_x11_menu.py lebih dulu")
    anchor = '            android:textCursorDrawable="@null" />\n'
    exactly(text, anchor, "kolom ketik teks")
    return LAYOUT, text, text.replace(anchor, anchor + DISPLAY_ROW), eol


def patch_strings():
    text, eol = load(STRINGS)
    if "x11_menu_fullscreen_text" in text:
        print("strings.xml: sudah")
        return None
    anchor = '    <string name="x11_menu_text_hint">Ketik teks lalu kirim</string>\n'
    exactly(text, anchor, "string x11_menu_text_hint")
    return STRINGS, text, text.replace(anchor, anchor + STRINGS_ADD), eol


def patch_controller():
    text, eol = load(CTRL_JAVA)
    if "X11DisplayOptions" in text:
        print("X11MenuController.java: sudah")
        return None
    t = text
    for old, new, label in (
        ("    private boolean open;\n", "    private boolean open;\n    private X11DisplayOptions displayOptions;\n", "field open"),
        ("        bindTextInput();\n", "        bindTextInput();\n        displayOptions = new X11DisplayOptions(activity);\n", "bindTextInput"),
        ("            layoutPanel();\n            panel.setVisibility(View.VISIBLE);\n",
         "            layoutPanel();\n            displayOptions.refresh();\n            panel.setVisibility(View.VISIBLE);\n", "buka panel"),
    ):
        exactly(t, old, label)
        t = t.replace(old, new)
    return CTRL_JAVA, text, t, eol


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir(LORIE):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (
        patch_layout(), patch_strings(), patch_controller(),
        new_file(OPTIONS_JAVA, OPTIONS),
        new_file(DRAW_FULLSCREEN, DRAWABLE_FULLSCREEN),
        new_file(DRAW_ORIENTATION, DRAWABLE_ORIENTATION),
    ) if c]
    if not changes:
        print("Tidak ada perubahan (semua sudah diterapkan).")
        return
    for path, old, new, eol in changes:
        if old == "":
            print("BARU: %s (%d baris)" % (path, new.count("\n")))
        else:
            print("\n".join(difflib.unified_diff(old.splitlines(), new.splitlines(), path, path, lineterm="", n=1)))
        if args.apply:
            save(path, new, eol)
            print("DITULIS: " + path)
    if not args.apply:
        print("\nDry-run. Jalankan lagi dengan --apply untuk menulis.")


if __name__ == "__main__":
    main()
