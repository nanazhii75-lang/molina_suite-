#!/usr/bin/env python3
"""Layar X11: tanpa layar status awal, toolbar jadi satu menu dropdown mengambang.

  1. Layar "Not connected" beserta 4 tombolnya diganti latar hitam dengan teks kecil
     "Menghubungkan..." (stub tetap ada supaya logika tersambung/terputus tidak berubah).
  2. Bar extra keys di bawah layar dimatikan. Sebagai gantinya satu ikon mengambang di pojok
     kanan atas layar monitor membuka panel: extra keys (ESC, CTRL, ALT, panah, keyboard,
     pengaturan, dst) + display, zoom, bantuan, keluar. Panel tetap terbuka sampai ikon
     diketuk lagi atau layar di luar panel diketuk, jadi CTRL/ALT yang menempel tetap bekerja.
  3. Halaman kedua bar lama (kolom ketik teks) pindah ke panel, dengan id yang sama
     (terminal_toolbar_text_input) sehingga logika shouldInterceptKeys tetap berlaku.
  4. Kode bar lama yang tak terpakai dibuang: X11ToolbarViewPager dan dua layout-nya.
Berkas baru: X11MenuController.java, x11_menu_background.xml, x11_ic_menu.xml.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_x11_menu.py [--apply]
"""
import argparse
import difflib
import os
import sys

LORIE = "engines/termux-x11/lorie/src/main"
MAIN_JAVA = LORIE + "/java/com/molina/suite/x11/MainActivity.java"
CTRL_JAVA = LORIE + "/java/com/molina/suite/x11/X11MenuController.java"
LAYOUT = LORIE + "/res/layout/main_activity.xml"
STRINGS = LORIE + "/res/values/strings.xml"
DRAW_BG = LORIE + "/res/drawable/x11_menu_background.xml"
DRAW_ICON = LORIE + "/res/drawable/x11_ic_menu.xml"
OLD_FILES = [
    LORIE + "/java/com/molina/suite/x11/utils/X11ToolbarViewPager.java",
    LORIE + "/res/layout/lorie_view_terminal_toolbar_extra_keys.xml",
    LORIE + "/res/layout/lorie_view_terminal_toolbar_text_input.xml",
]

CONTROLLER = r"""package com.molina.suite.x11;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.molina.suite.x11.extrakeys.ExtraKeysInfo;
import com.molina.suite.x11.extrakeys.ExtraKeysView;
import com.molina.suite.x11.utils.TermuxX11ExtraKeys;

/**
 * Menu dropdown mengambang di dalam layar monitor X11. Menggantikan bar extra keys di bawah layar
 * dan tombol-tombol layar status: satu ikon membuka panel berisi extra keys (termasuk CTRL/ALT
 * yang menempel, keyboard dan pengaturan) serta display, zoom, bantuan, dan keluar.
 *
 * Panel tetap terbuka sampai ikon diketuk lagi atau layar di luar panel diketuk.
 */
final class X11MenuController {
    private static final float ROW_HEIGHT_DP = 37.5f;
    private static final float MAX_PANEL_WIDTH_DP = 440f;
    private static final float SIDE_MARGIN_DP = 16f;
    private static final float BUTTON_ALPHA = 0.75f;
    private static final int ZOOM_STEP = 25;

    private final MainActivity activity;
    private final View menuButton;
    private final View panel;
    private final ExtraKeysView extraKeysView;
    private boolean open;

    X11MenuController(@NonNull MainActivity activity) {
        this.activity = activity;
        this.menuButton = activity.findViewById(R.id.x11_menu_button);
        this.panel = activity.findViewById(R.id.x11_menu_panel);
        this.extraKeysView = activity.findViewById(R.id.x11_menu_extra_keys);
    }

    /** Menyambungkan extra keys ke pengirim input X11 dan memasang semua aksi menu. */
    void install(@NonNull View.OnKeyListener keyListener) {
        activity.mExtraKeys = new TermuxX11ExtraKeys(keyListener, activity, extraKeysView);
        extraKeysView.reload();
        extraKeysView.setOnHoverListener((v, e) -> true);
        extraKeysView.setOnGenericMotionListener((v, e) -> true);

        menuButton.setAlpha(BUTTON_ALPHA);
        menuButton.setOnClickListener(v -> toggle());
        bindTextInput();

        bind(R.id.x11_menu_displays, v -> activity.showDisplaysDialog());
        bind(R.id.x11_menu_zoom_in, v -> activity.getLorieView().adjustRendererZoom(ZOOM_STEP));
        bind(R.id.x11_menu_zoom_out, v -> activity.getLorieView().adjustRendererZoom(-ZOOM_STEP));
        bind(R.id.x11_menu_zoom_reset, v -> activity.getLorieView().resetRendererZoom());
        bind(R.id.x11_menu_help, v -> openHelp());
        bind(R.id.x11_menu_exit, v -> activity.finish());

        layoutPanel();
    }

    boolean isOpen() {
        return open;
    }

    void toggle() {
        setOpen(!open);
    }

    void setOpen(boolean visible) {
        if (open == visible)
            return;

        open = visible;
        if (visible) {
            layoutPanel();
            panel.setVisibility(View.VISIBLE);
            panel.bringToFront();
            menuButton.bringToFront();
        } else {
            panel.setVisibility(View.GONE);
            // CTRL/ALT yang masih menempel harus dilepas saat panel ditutup.
            if (activity.mExtraKeys != null)
                activity.mExtraKeys.unsetSpecialKeys();
        }
        activity.getLorieView().requestFocus();
    }

    /** Menampilkan atau menyembunyikan ikon menu (misalnya disembunyikan saat mode PiP). */
    void refresh(boolean allowed) {
        menuButton.setVisibility(allowed ? View.VISIBLE : View.GONE);
        if (!allowed)
            setOpen(false);
    }

    /** Dipanggil dari dispatchTouchEvent: ketukan di luar panel dan ikon menutup panel. Tidak pernah menelan event. */
    void onDispatchTouchEvent(@NonNull MotionEvent event) {
        if (!open || event.getActionMasked() != MotionEvent.ACTION_DOWN)
            return;

        if (!contains(panel, event) && !contains(menuButton, event))
            setOpen(false);
    }

    private void layoutPanel() {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        ExtraKeysInfo info = activity.mExtraKeys == null ? null : activity.mExtraKeys.getExtraKeysInfo();
        int rows = info == null ? 0 : info.getMatrix().length;

        int sideMargin = Math.round(SIDE_MARGIN_DP * metrics.density);
        int width = Math.min(Math.round(MAX_PANEL_WIDTH_DP * metrics.density), metrics.widthPixels - 2 * sideMargin);

        ViewGroup.LayoutParams params = extraKeysView.getLayoutParams();
        params.width = width;
        params.height = Math.round(ROW_HEIGHT_DP * metrics.density * rows);
        extraKeysView.setLayoutParams(params);
        extraKeysView.setVisibility(rows == 0 ? View.GONE : View.VISIBLE);
    }

    /** Kolom ketik teks: isinya dikirim ke X11 saat tombol kirim IME ditekan; kosong berarti Enter. */
    private void bindTextInput() {
        EditText input = activity.findViewById(R.id.terminal_toolbar_text_input);
        input.setOnEditorActionListener((v, actionId, event) -> {
            String text = input.getText().toString();
            if (text.isEmpty())
                text = "\r";
            activity.getLorieView().sendTextEvent(text.getBytes(UTF_8));
            input.setText("");
            return true;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            input.setOnCapturedPointerListener((v, e) -> {
                activity.setCapturingEnabled(false);
                return false;
            });
    }

    private void bind(int id, View.OnClickListener listener) {
        activity.findViewById(id).setOnClickListener(listener);
    }

    private void openHelp() {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.x11_help_url))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.x11_help_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean contains(View view, MotionEvent event) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = event.getRawX();
        float y = event.getRawY();
        return x >= location[0] && x < location[0] + view.getWidth()
                && y >= location[1] && y < location[1] + view.getHeight();
    }
}
"""

DRAWABLE_BG = r"""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#D9101418" />
    <stroke android:width="1dp" android:color="#44FFFFFF" />
    <corners android:radius="10dp" />
</shape>
"""

DRAWABLE_ICON = r"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M4,7h16M4,12h16M4,17h16"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round" />
</vector>
"""

STRINGS_ADD = r"""    <string name="x11_connecting">Menghubungkan...</string>
    <string name="x11_menu_button_text">Menu</string>
    <string name="x11_menu_text_hint">Ketik teks lalu kirim</string>
    <string name="x11_menu_zoom_in_text">Perbesar</string>
    <string name="x11_menu_zoom_out_text">Perkecil</string>
    <string name="x11_menu_zoom_reset_text">Zoom 100%</string>
    <string name="x11_help_url">https://github.com/nanazhii75-lang/molina_suite-</string>
    <string name="x11_help_unavailable">Tidak ada aplikasi untuk membuka tautan bantuan.</string>
"""

NEW_STUB = r"""        <LinearLayout
            android:id="@+id/stub"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@android:color/black"
            android:gravity="center"
            android:orientation="vertical">

            <TextView
                android:id="@+id/textView"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/x11_connecting"
                android:textColor="#99FFFFFF"
                android:textSize="14sp" />
        </LinearLayout>
"""

OVERLAY = r"""
    <ImageButton
        android:id="@+id/x11_menu_button"
        android:layout_width="44dp"
        android:layout_height="44dp"
        android:layout_gravity="top|end"
        android:layout_margin="8dp"
        android:background="@drawable/x11_menu_background"
        android:contentDescription="@string/x11_menu_button_text"
        android:padding="10dp"
        android:scaleType="fitCenter"
        android:src="@drawable/x11_ic_menu" />

    <LinearLayout
        android:id="@+id/x11_menu_panel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_marginStart="8dp"
        android:layout_marginTop="60dp"
        android:layout_marginEnd="8dp"
        android:background="@drawable/x11_menu_background"
        android:clickable="true"
        android:focusable="false"
        android:orientation="vertical"
        android:padding="4dp"
        android:visibility="gone">

        <com.molina.suite.x11.extrakeys.ExtraKeysView
            android:id="@+id/x11_menu_extra_keys"
            style="?android:attr/buttonBarStyle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal" />

        <androidx.appcompat.widget.AppCompatEditText
            android:id="@+id/terminal_toolbar_text_input"
            android:layout_width="match_parent"
            android:layout_height="40dp"
            android:layout_marginTop="4dp"
            android:hint="@string/x11_menu_text_hint"
            android:imeOptions="actionSend|flagNoFullscreen"
            android:importantForAutofill="no"
            android:inputType="text"
            android:maxLines="1"
            android:paddingTop="0dp"
            android:paddingBottom="0dp"
            android:textColor="@android:color/white"
            android:textColorHighlight="@android:color/darker_gray"
            android:textColorHint="#66FFFFFF"
            android:textCursorDrawable="@null" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="44dp"
            android:layout_marginTop="4dp"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/x11_menu_displays"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/displays_button_text"
                android:padding="10dp"
                android:src="@drawable/lorie_ic_displays" />

            <ImageButton
                android:id="@+id/x11_menu_zoom_in"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_zoom_in_text"
                android:padding="10dp"
                android:src="@drawable/ic_zoom_in" />

            <ImageButton
                android:id="@+id/x11_menu_zoom_out"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_zoom_out_text"
                android:padding="10dp"
                android:src="@drawable/ic_zoom_out" />

            <ImageButton
                android:id="@+id/x11_menu_zoom_reset"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/x11_menu_zoom_reset_text"
                android:padding="10dp"
                android:src="@drawable/ic_zoom_reset" />

            <ImageButton
                android:id="@+id/x11_menu_help"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/help_button_text"
                android:padding="10dp"
                android:src="@drawable/lorie_ic_help" />

            <ImageButton
                android:id="@+id/x11_menu_exit"
                android:layout_width="0dp"
                android:layout_height="match_parent"
                android:layout_weight="1"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="@string/exit_button_text"
                android:padding="10dp"
                android:src="@drawable/lorie_ic_exit" />
        </LinearLayout>
    </LinearLayout>
"""

NEW_TOOLBAR_METHOD = r"""    /**
     * Bar extra keys di bawah layar sudah digantikan menu dropdown (X11MenuController) dan pager
     * lamanya tidak pernah ditampilkan lagi. Yang tersisa: menyegarkan menu dan tata letak.
     */
    private void setTerminalToolbarView() {
        getTerminalToolbarViewPager().setVisibility(View.GONE);
        if (x11Menu != null)
            x11Menu.refresh(!isInPictureInPictureMode);

        setTerminalToolbarViewLayout();
        getLorieView().requestFocus();
    }
"""

NEW_TOGGLE = r"""    public void toggleExtraKeys(boolean visible, boolean saveState) {
        if (x11Menu != null)
            x11Menu.setOpen(visible);
    }

    public void toggleExtraKeys() {
        if (x11Menu != null)
            x11Menu.toggle();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (x11Menu != null)
            x11Menu.onDispatchTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }
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


def once(text, old, label):
    if text.count(old) != 1:
        die("jangkar '%s' ditemukan %d kali (harus 1)" % (label, text.count(old)))


def cut(text, start, end, replacement, label):
    """Ganti teks dari penanda start sampai sebelum penanda end."""
    once(text, start, label + " (awal)")
    i = text.index(start)
    j = text.index(end, i)
    return text[:i] + replacement + text[j:]


def patch_layout():
    text, eol = load(LAYOUT)
    if "x11_menu_button" in text:
        print("main_activity.xml: sudah")
        return None
    start = '        <LinearLayout\n            android:id="@+id/stub"'
    end = '\n\n\n    </FrameLayout>'
    new = cut(text, start, end, NEW_STUB.rstrip("\n"), "stub")
    tail = "</FrameLayout>"
    if not new.rstrip().endswith(tail):
        die("main_activity.xml tidak diakhiri </FrameLayout>")
    k = new.rstrip().rfind(tail)
    new = new[:k].rstrip("\n") + "\n" + OVERLAY + tail + "\n"
    return LAYOUT, text, new, eol


def patch_strings():
    text, eol = load(STRINGS)
    if "x11_connecting" in text:
        print("strings.xml: sudah")
        return None
    once(text, "</resources>", "</resources>")
    new = text.replace("</resources>", STRINGS_ADD + "</resources>")
    return STRINGS, text, new, eol


def patch_main():
    text, eol = load(MAIN_JAVA)
    if "x11Menu" in text:
        print("MainActivity.java: sudah")
        return None
    new = text

    old_fields = (
        "    // We can not define function-static variables in Java, so we are defining them outside a function\n"
        "    private final X11ToolbarViewPager.PageAdapter mPageAdapter =\n"
        "            new X11ToolbarViewPager.PageAdapter(this, (v, k, e) -> mInputHandler.sendKeyEvent(e));\n"
        "    private final X11ToolbarViewPager.OnPageChangeListener mOnPageListener = new X11ToolbarViewPager.OnPageChangeListener(this);\n"
    )
    once(new, old_fields, "field pager lama")
    new = new.replace(old_fields, "")
    old_import = "import com.molina.suite.x11.utils.X11ToolbarViewPager;\n"
    once(new, old_import, "import X11ToolbarViewPager")
    new = new.replace(old_import, "")

    old = "    public TermuxX11ExtraKeys mExtraKeys;\n"
    once(new, old, "mExtraKeys")
    new = new.replace(old, old + "    X11MenuController x11Menu;\n")

    old = "    private void showDisplaysDialog() {"
    once(new, old, "showDisplaysDialog")
    new = new.replace(old, "    void showDisplaysDialog() {")

    old_buttons = (
        '        findViewById(R.id.preferences_button).setOnClickListener((l) -> startActivity(new Intent(this, LoriePreferences.class) {{ setAction(Intent.ACTION_MAIN); }}));\n'
        '        findViewById(R.id.help_button).setOnClickListener((l) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-x11/blob/master/README.md#running-graphical-applications"))));\n'
        '        findViewById(R.id.exit_button).setOnClickListener((l) -> finish());\n'
        '        findViewById(R.id.displays_button).setOnClickListener((l) -> showDisplaysDialog());\n'
    )
    once(new, old_buttons, "tombol layar status")
    new = new.replace(old_buttons,
        "        x11Menu = new X11MenuController(this);\n"
        "        x11Menu.install((v, k, e) -> mInputHandler.sendKeyEvent(e));\n")

    new = cut(new, "    private void setTerminalToolbarView() {",
              "\n    // Keep in sync with Surface.ROTATION_*", NEW_TOOLBAR_METHOD, "setTerminalToolbarView")

    new = cut(new, "    public void toggleExtraKeys(boolean visible, boolean saveState) {",
              "    public boolean handleKey(KeyEvent e) {", NEW_TOGGLE + "\n", "toggleExtraKeys")
    return MAIN_JAVA, text, new, eol


def old_file(path):
    if not os.path.isfile(path):
        print(os.path.basename(path) + ": sudah dihapus")
        return None
    return ("DEL", path)


def new_file(path, content):
    if os.path.isfile(path):
        cur, _ = load(path)
        if cur == content:
            print(os.path.basename(path) + ": sudah")
            return None
        die("berkas sudah ada dengan isi berbeda: " + path)
    return path, "", content, "\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    if not os.path.isdir(LORIE):
        die("jalankan dari root repo molina-suite")
    changes = [c for c in (
        patch_layout(), patch_strings(), patch_main(),
        new_file(CTRL_JAVA, CONTROLLER),
        new_file(DRAW_BG, DRAWABLE_BG),
        new_file(DRAW_ICON, DRAWABLE_ICON),
    ) if c] + [d for d in (old_file(p) for p in OLD_FILES) if d]
    if not changes:
        print("Tidak ada perubahan (semua sudah diterapkan).")
        return
    for change in changes:
        if change[0] == "DEL":
            print("HAPUS: " + change[1])
            if args.apply:
                os.remove(change[1])
            continue
        path, old, new, eol = change
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
