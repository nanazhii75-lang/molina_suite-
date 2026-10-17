package com.molina.suite.core.common

/**
 * Kontrak nama yang dipakai bersama oleh modul X11 (peluncur) dan Setting (pemantau memori dan
 * penghentian), supaya keduanya tidak saling bergantung.
 */
object X11Contract {
    /** Kelas titik masuk X server lorie; muncul sebagai argumen proses app_process. */
    const val SERVER_ENTRY_POINT = "com.molina.suite.x11.CmdEntryPoint"
}
