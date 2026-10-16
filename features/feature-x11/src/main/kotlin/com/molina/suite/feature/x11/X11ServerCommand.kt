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

    private const val DEBIAN_ROOTFS = "/var/lib/proot-distro/installed-rootfs/debian"

    /**
     * X server tidak membawa data keyboard XKB dan bootstrap molina tidak memilikinya; data itu
     * ada di rootfs Debian (paket xkb-data). Bila XKB_CONFIG_ROOT belum diatur, diarahkan ke sana.
     * Tanpa data itu server berhenti dengan pesan yang menunjuk ke pemasangan Desktop X11.
     */
    private const val XKB_SETUP =
        "R=\"\${PREFIX:-/data/data/com.molina.suite/files/usr}$DEBIAN_ROOTFS\"; " +
            "if [ -z \"\${XKB_CONFIG_ROOT-}\" ]; then " +
            "for d in \"\$R/usr/share/xkeyboard-config-2\" \"\$R/usr/share/X11/xkb\"; do " +
            "if [ -f \"\$d/rules/evdev\" ]; then export XKB_CONFIG_ROOT=\"\$d\"; break; fi; done; fi; " +
            "if [ -z \"\${XKB_CONFIG_ROOT-}\" ]; then " +
            "echo 'Data keyboard XKB belum ada. Pasang Desktop X11 di Setting, menu Paket.' >&2; exit 1; fi; "

    /**
     * [apkPath] adalah APK aplikasi, [nativeLibDir] direktori library native hasil ekstraksi.
     * Aplikasi memakai useLegacyPackaging, jadi libXlorie.so tidak bisa dibaca langsung dari
     * dalam APK; path ekstraksinya diberikan lewat MOLINA_X11_LIB.
     */
    fun start(apkPath: String, nativeLibDir: String): ShellCommand {
        val script = "export CLASSPATH=${quote(apkPath)}; " +
            "export MOLINA_X11_LIB=${quote("$nativeLibDir/$NATIVE_LIB")}; " +
            XKB_SETUP +
            "exec $APP_PROCESS / $ENTRY_POINT $DISPLAY"
        return ShellCommand(
            executable = SHELL,
            arguments = listOf("-c", script),
            label = "Molina X11"
        )
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
