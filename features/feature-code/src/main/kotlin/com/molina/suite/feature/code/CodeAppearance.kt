package com.molina.suite.feature.code

import android.content.Context

/** Gaya tampilan editor yang disimpan permanen: ukuran font (sp) dan nomor baris. */
internal data class CodeAppearance(
    val textSizeSp: Int = DEFAULT_SP,
    val lineNumbers: Boolean = true
) {
    companion object {
        const val MIN_SP = 10
        const val MAX_SP = 28
        const val DEFAULT_SP = 14
    }
}

internal class CodeAppearanceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): CodeAppearance = CodeAppearance(
        textSizeSp = prefs.getInt(KEY_SIZE, CodeAppearance.DEFAULT_SP)
            .coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP),
        lineNumbers = prefs.getBoolean(KEY_LINES, true)
    )

    fun save(value: CodeAppearance) {
        prefs.edit()
            .putInt(KEY_SIZE, value.textSizeSp.coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP))
            .putBoolean(KEY_LINES, value.lineNumbers)
            .apply()
    }

    private companion object {
        const val PREFS = "molina_code_appearance"
        const val KEY_SIZE = "text_size_sp"
        const val KEY_LINES = "line_numbers"
    }
}
