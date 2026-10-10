package com.molina.suite.core.common

import android.view.Menu

/**
 * Kontrak opsional untuk fragment tab: shell meneruskan tombol Back ke fragment
 * yang sedang tampil. Mengembalikan true bila Back sudah dikonsumsi fragment.
 */
interface HostBackPressHandler {
    fun onHostBackPressed(): Boolean
}

/** Kontrak opsional untuk fragment tab yang perlu tahu context menu shell ditutup. */
interface HostContextMenuListener {
    fun onHostContextMenuClosed(menu: Menu)
}

/**
 * Kontrak opsional untuk shell: fragment tab meminta mode ringkas (header dan
 * navigasi bawah disembunyikan) agar area kerja lebih luas, misalnya saat keyboard tampil.
 */
interface HostChromeController {
    fun setChromeCompact(compact: Boolean)
}
