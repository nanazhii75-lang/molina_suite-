package com.molina.suite.feature.x11

import com.molina.suite.core.common.ShellCommand

/**
 * Menyusun perintah shell yang menjalankan X server lorie di proses terpisah
 * (app_process) dari shell Molina Terminal, dengan APK aplikasi sebagai CLASSPATH.
 */
internal object X11ServerCommand {
    /** Kelas titik masuk X server; juga dipakai untuk mengenali prosesnya. */
    const val ENTRY_POINT = "com.molina.suite.x11.CmdEntryPoint"

    /** Activity jendela X11 (lorie). Dipanggil lewat nama agar modul ini tidak bergantung pada kelasnya. */
    const val WINDOW_ACTIVITY = "com.molina.suite.x11.MainActivity"

    /** Action privat yang ditangani receiver lorie untuk menutup jendela X11. */
    const val ACTION_STOP = "com.molina.suite.x11.ACTION_STOP"

    private const val DISPLAY = ":0"
    private const val SHELL = "/system/bin/sh"
    private const val APP_PROCESS = "/system/bin/app_process"
    private const val NATIVE_LIB = "libXlorie.so"

    /**
     * [apkPath] adalah APK aplikasi, [nativeLibDir] direktori library native hasil ekstraksi.
     * Aplikasi memakai useLegacyPackaging, jadi libXlorie.so tidak bisa dibaca langsung dari
     * dalam APK; path ekstraksinya diberikan lewat MOLINA_X11_LIB.
     */
    fun start(apkPath: String, nativeLibDir: String): ShellCommand {
        val script = "export CLASSPATH=${quote(apkPath)}; " +
            "export MOLINA_X11_LIB=${quote("$nativeLibDir/$NATIVE_LIB")}; " +
            "exec $APP_PROCESS / $ENTRY_POINT $DISPLAY"
        return ShellCommand(
            executable = SHELL,
            arguments = listOf("-c", script),
            label = "Molina X11"
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
