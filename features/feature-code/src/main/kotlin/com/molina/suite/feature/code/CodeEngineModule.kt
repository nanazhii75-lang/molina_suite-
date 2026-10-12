package com.molina.suite.feature.code

import android.app.Application
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.TerminalInputSender

/** Komponen engine Code yang dipakai bersama oleh fitur dan fragment tab. */
internal class CodeEngine(
    application: Application,
    val editorOpen: EditorOpenBoard,
    val terminalInput: TerminalInputSender
) {
    val textMate = CodeTextMate(application.assets)
    val session = CodeEditorSession(CodeFileRepository(), textMate)
    val workspace = CodeWorkspace(application)
}

/**
 * Satu-satunya titik masuk shell ke modul Code: membuat engine lalu
 * mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas editor.
 */
object CodeEngineModule {

    @Volatile
    private var engine: CodeEngine? = null

    fun install(
        application: Application,
        engines: EngineRegistry,
        editorOpen: EditorOpenBoard,
        terminalInput: TerminalInputSender
    ) {
        engine = CodeEngine(application, editorOpen, terminalInput)
        engines.register(CodeFeature())
    }

    internal fun requireEngine(): CodeEngine =
        checkNotNull(engine) { "CodeEngineModule.install belum dipanggil" }
}
