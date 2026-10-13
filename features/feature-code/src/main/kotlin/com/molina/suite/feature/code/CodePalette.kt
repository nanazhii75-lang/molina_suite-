package com.molina.suite.feature.code

/** Warna chrome tab Code; selaras dengan tema TextMate Darcula yang dimuat. */
internal object CodePalette {
    val BACKGROUND: Int = 0xFF2B2B2B.toInt()

    /** Latar halaman kerja (editor, nomor baris, tab aktif): hitam pekat. */
    val EDITOR_BACKGROUND: Int = 0xFF000000.toInt()

    /** Sorotan baris aktif; sedikit lebih terang dari latar supaya tetap terlihat. */
    val EDITOR_CURRENT_LINE: Int = 0xFF161616.toInt()

    /** Nomor baris di tepi editor: cyan terang, kontras tinggi di latar hitam pekat. */
    val EDITOR_LINE_NUMBER: Int = 0xFF4DD0E1.toInt()

    /** Nomor baris tempat kursor berada: amber, senada dengan aksen molina. */
    val EDITOR_LINE_NUMBER_CURRENT: Int = 0xFFFFB300.toInt()
    val PANEL: Int = 0xFF313335.toInt()
    val TEXT: Int = 0xFFA9B7C6.toInt()
    val TEXT_MUTED: Int = 0xFF808080.toInt()
    val ACCENT: Int = 0xFFFF9800.toInt()
    val ACCENT_DARK: Int = 0xFF8D5A2B.toInt()
    val FOLDER: Int = 0xFF7FB8E8.toInt()

    /** Warna ikon toolbar bawah: satu warna per aksi. */
    val TOOL_SAVE: Int = 0xFF42A5F5.toInt()
    val TOOL_PASTE: Int = 0xFF26C6DA.toInt()
    val TOOL_UNDO: Int = 0xFFFFA726.toInt()
    val TOOL_REDO: Int = 0xFFFF8A65.toInt()
    val TOOL_SEARCH: Int = 0xFFFFEE58.toInt()
    val TOOL_CURSOR: Int = 0xFFBA68C8.toInt()
    val TOOL_COPY_ALL: Int = 0xFFF06292.toInt()
    val TOOL_RUN: Int = 0xFF4CAF50.toInt()
    val TOOL_CLOSE: Int = 0xFFEF5350.toInt()

    /** Ikon toolbar yang tidak bisa dipakai pada keadaan sekarang. */
    val TOOL_DISABLED: Int = 0xFF5A5D5F.toInt()

    /** Blok latar semua kecocokan pencarian; gelap supaya teks terang di atasnya tetap terbaca. */
    val MATCH_BLOCK: Int = 0xFF7A4A12.toInt()
}
