#!/usr/bin/env python3
"""Tahap 2a: kontrak TermuxHost agar klien terminal tidak terikat ke tipe TermuxActivity.

Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_termux_host.py [--apply]
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


APP = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/"
HOST = APP + "TermuxHost.java"
ACTIVITY = APP + "TermuxActivity.java"
CHANGES = "engines/termux/MOLINA-CHANGES.md"

HOST_SRC = '''package com.molina.suite.terminal.app;

import android.app.Activity;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.molina.suite.terminal.app.settings.properties.TermuxAppSharedProperties;
import com.molina.suite.terminal.app.terminal.TermuxActivityRootView;
import com.molina.suite.terminal.app.terminal.TermuxTerminalSessionClient;
import com.molina.suite.terminal.app.terminal.TermuxTerminalViewClient;
import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;
import com.molina.suite.terminal.shared.terminal.io.extrakeys.ExtraKeysView;
import com.molina.suite.terminal.terminal.TerminalSession;
import com.molina.suite.terminal.view.TerminalView;

/**
 * Berkas tambahan molina-suite. Kontrak antara klien terminal (view client, session client,
 * daftar sesi, toolbar) dan tempat terminal ditampilkan, sehingga klien tidak lagi terikat
 * ke tipe {@link TermuxActivity} dan dapat berjalan di dalam fragment shell.
 * <p/>
 * Hanya memuat metode yang benar-benar dipanggil klien. Apa pun yang membutuhkan
 * {@link android.content.Context} atau {@link Activity} (dialog, keyboard, system service)
 * diambil lewat {@link #getHostActivity()}.
 */
public interface TermuxHost {

    /** Activity yang menampung terminal; dipakai untuk Context, dialog, keyboard, dan window. */
    Activity getHostActivity();

    TerminalView getTerminalView();

    TermuxService getTermuxService();

    TermuxAppSharedPreferences getPreferences();

    TermuxAppSharedProperties getProperties();

    DrawerLayout getDrawer();

    ViewPager getTerminalToolbarViewPager();

    ExtraKeysView getExtraKeysView();

    void setExtraKeysView(ExtraKeysView extraKeysView);

    TermuxActivityRootView getTermuxActivityRootView();

    View getTermuxActivityBottomSpaceView();

    TermuxTerminalViewClient getTermuxTerminalViewClient();

    TermuxTerminalSessionClient getTermuxTerminalSessionClient();

    @Nullable
    TerminalSession getCurrentSession();

    boolean isTerminalViewSelected();

    boolean isVisible();

    boolean isOnResumeAfterOnCreate();

    void toggleTerminalToolbar();

    void finishActivityIfNotFinishing();

    void showToast(String text, boolean longDuration);

    void termuxSessionListNotifyUpdated();
}
'''

DECL_OLD = "public final class TermuxActivity extends Activity implements ServiceConnection {"
DECL_NEW = "public final class TermuxActivity extends Activity implements ServiceConnection, TermuxHost {"

VIS_OLD = "    public boolean isVisible() {\n        return mIsVisible;\n    }\n"
VIS_NEW = (
    VIS_OLD
    + "\n    @Override\n    public Activity getHostActivity() {\n        return this;\n    }\n"
)

NOTE = (
    "\n## Kontrak host terminal (tahap 2a)\n"
    "- `app/.../TermuxHost.java` (baru): interface yang memuat metode TermuxActivity yang dipanggil "
    "klien terminal. `TermuxActivity` mengimplementasikannya dan menambah `getHostActivity()`. "
    "Perilaku tidak berubah; klien dipindahkan ke interface ini pada tahap 2b.\n"
)


def edit_activity(text):
    if DECL_NEW in text:
        new = text
    else:
        n = text.count(DECL_OLD)
        if n != 1:
            die("%s: deklarasi kelas harus tepat 1 kali, ditemukan %d" % (ACTIVITY, n))
        new = text.replace(DECL_OLD, DECL_NEW, 1)
    if "getHostActivity" not in new:
        n = new.count(VIS_OLD)
        if n != 1:
            die("%s: jangkar isVisible() harus tepat 1 kali, ditemukan %d" % (ACTIVITY, n))
        new = new.replace(VIS_OLD, VIS_NEW, 1)
    return new


def edit_changes(text):
    if "TermuxHost" in text:
        return text
    return text.rstrip("\n") + "\n" + NOTE


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    writes = []

    if os.path.exists(HOST):
        existing, _ = load(HOST)
        if existing == HOST_SRC:
            print("SUDAH: " + HOST)
        else:
            die(HOST + " sudah ada dengan isi berbeda; periksa manual")
    else:
        print("BARU: %s (%d baris)" % (HOST, HOST_SRC.count("\n")))
        writes.append((HOST, HOST_SRC, "\n"))

    for path, fn in ((ACTIVITY, edit_activity), (CHANGES, edit_changes)):
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
