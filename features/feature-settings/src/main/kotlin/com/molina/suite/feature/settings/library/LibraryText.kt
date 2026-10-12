package com.molina.suite.feature.settings.library

/** Batas isi entri Library. Skrip panjang dijalankan lewat berkas, jadi batasnya jumlah baris. */
internal object LibraryLimits {

    const val MAX_SCRIPT_LINES = 3000

    /** Jumlah baris; baris terakhir tanpa newline tetap dihitung, teks kosong 0 baris. */
    fun lineCount(text: String): Int {
        if (text.isEmpty()) return 0
        var lines = 0
        for (c in text) if (c == '\n') lines++
        return if (text.last() == '\n') lines else lines + 1
    }
}

/** Pratinjau singkat isi entri untuk daftar, agar skrip ribuan baris tidak dimuat utuh ke TextView. */
internal object LibraryPreview {

    private const val MAX_CHARS = 200
    private const val MAX_LINES = 2

    /** Paling banyak [MAX_LINES] baris pertama dan [MAX_CHARS] karakter. */
    fun head(content: String): String {
        var end = 0
        var lines = 1
        while (end < content.length && end < MAX_CHARS) {
            if (content[end] == '\n') {
                if (lines == MAX_LINES) break
                lines++
            }
            end++
        }
        return content.substring(0, end).trimEnd()
    }

    const val PREVIEW_LINES = MAX_LINES
}
