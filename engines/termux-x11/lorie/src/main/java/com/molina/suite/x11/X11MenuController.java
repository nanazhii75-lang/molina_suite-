package com.molina.suite.x11;

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
