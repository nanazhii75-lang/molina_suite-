package com.molina.suite.terminal.shared.termux;

import androidx.annotation.Nullable;

/**
 * Kait keluar dari aplikasi. Bila aplikasi induk (shell molina) memasangnya, tombol Exit di
 * notifikasi Terminal menutup seluruh aplikasi seperti Termux asli, bukan hanya menghentikan
 * service. Engine tidak mengenal shell: shell memasang {@link Handler} lewat feature-terminal.
 * Tanpa handler (engine dipakai sendiri), Exit tetap hanya menghentikan service.
 */
public final class TermuxExitHook {

    /** Penangan yang menutup seluruh aplikasi. */
    public interface Handler {
        void onExitRequested();
    }

    @Nullable
    private static volatile Handler sHandler;

    private TermuxExitHook() {
    }

    /** Memasang (atau melepas dengan null) penangan keluar. */
    public static void setHandler(@Nullable Handler handler) {
        sHandler = handler;
    }

    /** True bila aplikasi induk sudah memasang penangan keluar. */
    public static boolean isInstalled() {
        return sHandler != null;
    }

    /** Meminta aplikasi ditutup; true bila penangan dipanggil. */
    public static boolean request() {
        Handler handler = sHandler;
        if (handler == null) return false;
        handler.onExitRequested();
        return true;
    }
}
