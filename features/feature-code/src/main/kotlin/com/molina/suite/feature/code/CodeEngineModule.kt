package com.molina.suite.feature.code

import android.app.Application
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.LibraryDraftBoard
import com.molina.suite.core.common.MediaOpener
import com.molina.suite.core.common.PrefixScripts
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard

/** Komponen engine Code yang dipakai bersama oleh fitur dan fragment tab. */
internal class CodeEngine(
    application: Application,
    val editorOpen: EditorOpenBoard,
    val terminalInput: TerminalInputSender,
    val toolReadiness: ToolReadinessChecker,
    val toolSetup: ToolSetupBoard,
    shell: ShellCommandRunner,
    val libraryDraft: LibraryDraftBoard,
    val documents: DocumentConverter,
    val media: MediaOpener
) {
    val textMate = CodeTextMate(application.assets)
    val session = CodeEditorSession(CodeFileRepository(), textMate)
    val workspace = CodeWorkspace(application)
    val checkRunner = CodeCheckRunner(application.filesDir, shell)
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
        terminalInput: TerminalInputSender,
        toolReadiness: ToolReadinessChecker,
        toolSetup: ToolSetupBoard,
        shell: ShellCommandRunner,
        libraryDraft: LibraryDraftBoard,
        documents: DocumentConverter,
        media: MediaOpener
    ) {
        engine = CodeEngine(application, editorOpen, terminalInput, toolReadiness, toolSetup, shell, libraryDraft, documents, media)
        engines.register(CodeFeature())
        PrefixScripts.installWhenReady(application, listOf("molina-open"))
    }

    internal fun requireEngine(): CodeEngine =
        checkNotNull(engine) { "CodeEngineModule.install belum dipanggil" }
}
