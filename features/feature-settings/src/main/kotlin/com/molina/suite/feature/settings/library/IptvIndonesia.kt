package com.molina.suite.feature.settings.library

/**
 * Entri bawaan "IPTV Indonesia". Isinya bukan skrip: tombol jalankan membuka dialog sumber IPTV
 * (lihat [IptvSource]) dan playlist dibuka langsung di mpv lewat Intent, tanpa lewat shell.
 */
internal object IptvIndonesia {

    const val ENTRY_ID = "builtin-iptv-indonesia"

    val entry: LibraryEntry = LibraryEntry(
        id = ENTRY_ID,
        name = "IPTV Indonesia",
        content = "echo 'IPTV Indonesia dibuka dari tombol jalankan (dialog sumber IPTV).'",
        note = "Daftar channel TV Indonesia (iptv-org) di mpv. Sumber bisa diganti ke URL sendiri dan " +
            "diperbarui manual; isi dari jaringan disaring ketat dan tidak pernah dijalankan sebagai perintah.",
        runIn = RunMode.ANDROID,
        builtin = true
    )
}
