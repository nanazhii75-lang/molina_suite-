package com.molina.suite.feature.code

import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.widget.CodeEditor

/** Pembuat [CodeEditor] dengan pengaturan dasar tab Code. */
internal object CodeEditorFactory {

    fun create(context: Context): CodeEditor {
        val appearance = CodeAppearanceStore(context).load()
        CodeThemeState.name = appearance.theme
        return CodeEditor(context).apply {
            setTypefaceText(Typeface.MONOSPACE)
            setTextSize(appearance.textSizeSp.toFloat())
            setLineNumberEnabled(appearance.lineNumbers)
            setWordwrap(true)
        }
    }
}
