package com.molina.suite.x11;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/**
 * Penempatan ikon menu dan panelnya di dalam layar monitor X11. Ikon dapat digeser dengan jari
 * dan posisinya diingat; panel selalu ditempatkan di sisi ikon yang paling lapang dan di dalam
 * layar. Ruang yang dipakai keyboard dikecualikan supaya ikon dan panel tidak tertutup.
 */
final class X11MenuPlacement {
    private static final String STORE_NAME = "x11_menu_placement";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final float EDGE_MARGIN_DP = 8f;
    private static final float PANEL_GAP_DP = 6f;

    /** Ruang yang tersedia untuk ikon dan batas geraknya, dalam piksel. */
    private static final class Area {
        int width;
        int height;
        int size;
        int minX;
        int maxX;
        int minY;
        int maxY;
    }

    private final ViewGroup parent;
    private final View button;
    private final View panel;
    private final SharedPreferences store;
    private final int touchSlop;
    private final int edgeMargin;
    private final int panelGap;

    /** Posisi ikon sebagai pecahan dari ruang geraknya: 0 = tepi kiri/atas, 1 = tepi kanan/bawah. */
    private float fractionX;
    private float fractionY;
    private int imeHeight;

    private float downRawX;
    private float downRawY;
    private int downLeft;
    private int downTop;
    private boolean dragging;

    X11MenuPlacement(@NonNull Context context, @NonNull View button, @NonNull View panel) {
        float density = context.getResources().getDisplayMetrics().density;
        this.parent = (ViewGroup) button.getParent();
        this.button = button;
        this.panel = panel;
        this.store = context.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.edgeMargin = Math.round(EDGE_MARGIN_DP * density);
        this.panelGap = Math.round(PANEL_GAP_DP * density);
    }

    /** Memuat posisi tersimpan (bawaan: kanan bawah), memasang geser, dan menempatkan ikon. */
    void attach() {
        fractionX = store.getFloat(KEY_X, 1f);
        fractionY = store.getFloat(KEY_Y, 1f);
        button.setOnTouchListener(this::onButtonTouch);
        parent.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)
                update();
        });
        update();
    }

    void setImeHeight(int height) {
        if (imeHeight == height)
            return;

        imeHeight = height;
        update();
    }

    /** Menempatkan ulang ikon, dan panel bila sedang terbuka. */
    void update() {
        Area area = area();
        if (area == null)
            return;

        int x = area.minX + Math.round(fractionX * (area.maxX - area.minX));
        int y = area.minY + Math.round(fractionY * (area.maxY - area.minY));
        moveTo(button, x, y);
        if (panel.getVisibility() == View.VISIBLE)
            placePanel(area, x, y);
    }

    private Area area() {
        Area area = new Area();
        area.width = parent.getWidth();
        area.height = parent.getHeight() - imeHeight;
        area.size = button.getWidth() > 0 ? button.getWidth() : button.getLayoutParams().width;
        if (area.width <= 0 || area.height <= 0 || area.size <= 0)
            return null;

        area.minX = edgeMargin;
        area.maxX = Math.max(area.minX, area.width - area.size - edgeMargin);
        area.minY = edgeMargin;
        area.maxY = Math.max(area.minY, area.height - area.size - edgeMargin);
        return area;
    }

    private void placePanel(Area area, int buttonX, int buttonY) {
        panel.measure(
                View.MeasureSpec.makeMeasureSpec(Math.max(1, area.width - 2 * edgeMargin), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(Math.max(1, area.height - 2 * edgeMargin), View.MeasureSpec.AT_MOST));
        int panelWidth = panel.getMeasuredWidth();
        int panelHeight = panel.getMeasuredHeight();

        boolean buttonInRightHalf = buttonX + area.size / 2 > area.width / 2;
        boolean buttonInLowerHalf = buttonY + area.size / 2 > area.height / 2;
        int x = buttonInRightHalf ? buttonX + area.size - panelWidth : buttonX;
        int y = buttonInLowerHalf ? buttonY - panelGap - panelHeight : buttonY + area.size + panelGap;

        moveTo(panel,
                clamp(x, edgeMargin, Math.max(edgeMargin, area.width - panelWidth - edgeMargin)),
                clamp(y, edgeMargin, Math.max(edgeMargin, area.height - panelHeight - edgeMargin)));
    }

    private static void moveTo(View view, int x, int y) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        int gravity = Gravity.TOP | Gravity.LEFT;
        if (params.gravity == gravity && params.leftMargin == x && params.topMargin == y
                && params.rightMargin == 0 && params.bottomMargin == 0)
            return;

        params.gravity = gravity;
        params.setMargins(x, y, 0, 0);
        view.setLayoutParams(params);
    }

    private boolean onButtonTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downLeft = params.leftMargin;
                downTop = params.topMargin;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop)
                    dragging = true;
                if (dragging)
                    dragTo(downLeft + Math.round(dx), downTop + Math.round(dy));
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging)
                    save();
                else
                    view.performClick();
                dragging = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging)
                    save();
                dragging = false;
                return true;
            default:
                return false;
        }
    }

    private void dragTo(int x, int y) {
        Area area = area();
        if (area == null)
            return;

        fractionX = area.maxX > area.minX ? clamp01((x - area.minX) / (float) (area.maxX - area.minX)) : 1f;
        fractionY = area.maxY > area.minY ? clamp01((y - area.minY) / (float) (area.maxY - area.minY)) : 1f;
        update();
    }

    private void save() {
        store.edit().putFloat(KEY_X, fractionX).putFloat(KEY_Y, fractionY).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
