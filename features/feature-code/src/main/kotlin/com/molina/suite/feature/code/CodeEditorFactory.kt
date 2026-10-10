package com.molina.suite.feature.code

import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.widget.CodeEditor

/** Pembuat [CodeEditor] dengan pengaturan dasar tab Code. */
internal object CodeEditorFactory {

    private const val TEXT_SIZE_SP = 14f

    fun create(context: Context): CodeEditor = CodeEditor(context).apply {
        setTypefaceText(Typeface.MONOSPACE)
        setTextSize(TEXT_SIZE_SP)
        setLineNumberEnabled(true)
        setWordwrap(true)
    }
}
