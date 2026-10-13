package com.molina.suite.feature.code

import android.content.Context

/** Gaya tampilan editor yang disimpan permanen: ukuran font, nomor baris, dan tema warna. */
internal data class CodeAppearance(
    val textSizeSp: Int = DEFAULT_SP,
    val lineNumbers: Boolean = true,
    val theme: String = THEME_OBSIDIAN
) {
    companion object {
        const val MIN_SP = 10
        const val MAX_SP = 28
        const val DEFAULT_SP = 14
        const val THEME_DARCULA = "darcula"
        const val THEME_OBSIDIAN = "obsidian"
        val THEMES = listOf(THEME_OBSIDIAN, THEME_DARCULA)
    }
}

/** Tema warna yang sedang aktif; dibaca CodeTextMate saat membuat skema warna. */
internal object CodeThemeState {
    @Volatile
    var name: String = CodeAppearance.THEME_OBSIDIAN
}

internal class CodeAppearanceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): CodeAppearance {
        val saved = prefs.getString(KEY_THEME, CodeAppearance.THEME_OBSIDIAN)
        return CodeAppearance(
            textSizeSp = prefs.getInt(KEY_SIZE, CodeAppearance.DEFAULT_SP)
                .coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP),
            lineNumbers = prefs.getBoolean(KEY_LINES, true),
            theme = if (saved != null && saved in CodeAppearance.THEMES) saved else CodeAppearance.THEME_OBSIDIAN
        )
    }

    fun save(value: CodeAppearance) {
        prefs.edit()
            .putInt(KEY_SIZE, value.textSizeSp.coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP))
            .putBoolean(KEY_LINES, value.lineNumbers)
            .putString(KEY_THEME, value.theme)
            .apply()
    }

    private companion object {
        const val PREFS = "molina_code_appearance"
        const val KEY_SIZE = "text_size_sp"
        const val KEY_LINES = "line_numbers"
        const val KEY_THEME = "theme"
    }
}
