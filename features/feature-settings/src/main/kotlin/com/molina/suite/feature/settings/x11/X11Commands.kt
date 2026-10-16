package com.molina.suite.feature.settings.x11

import com.molina.suite.core.common.ScriptRunLine

/** Baris ketik yang mengendalikan desktop X11 lewat perintah molina-x11 di dalam Debian. */
internal object X11Commands {

    private const val DESKTOP_START = "molina-x11 desktop"
    private const val DESKTOP_STOP = "molina-x11 stop"
    private const val NO_DEBIAN = "echo 'Debian belum terpasang. Pasang dulu lewat Setting, bagian Debian.'"

    fun startDesktop(): String = inDebian(DESKTOP_START)

    fun stopDesktop(): String = inDebian(DESKTOP_STOP)

    /**
     * Di dalam Debian perintah dijalankan langsung. Dari shell host dipakai proot-distro dengan
     * tmp bersama (supaya socket X terlihat) dan bash interaktif (supaya fungsi molina-x11 dari
     * .bashrc termuat).
     */
    private fun inDebian(command: String): String =
        "if ${ScriptRunLine.IN_DEBIAN_TEST}; then $command; " +
            "elif command -v proot-distro >/dev/null 2>&1; then " +
            "proot-distro login debian --shared-tmp -- bash -ic ${ScriptRunLine.quote(command)}; " +
            "else $NO_DEBIAN; fi"
}
