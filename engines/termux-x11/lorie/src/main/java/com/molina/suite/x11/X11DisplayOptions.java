package com.molina.suite.x11;

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
