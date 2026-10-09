package com.molina.suite.terminal.app;

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
