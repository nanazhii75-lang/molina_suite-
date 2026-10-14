package com.molina.suite.feature.settings.logcat

/** Tingkat prioritas logcat; urutan deklarasi sama dengan urutan prioritasnya (VERBOSE terendah). */
internal enum class LogcatLevel(val symbol: Char, val label: String) {
    VERBOSE('V', "Verbose"),
    DEBUG('D', "Debug"),
    INFO('I', "Info"),
    WARN('W', "Warn"),
    ERROR('E', "Error"),
    FATAL('F', "Fatal");

    companion object {
        /** Huruf prioritas logcat menjadi tingkat; 'A' (assert) disamakan dengan FATAL, 'S' (silent) tidak valid. */
        fun fromSymbol(symbol: Char): LogcatLevel? = when (symbol) {
            'V' -> VERBOSE
            'D' -> DEBUG
            'I' -> INFO
            'W' -> WARN
            'E' -> ERROR
            'F', 'A' -> FATAL
            else -> null
        }
    }
}
