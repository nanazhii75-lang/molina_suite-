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
