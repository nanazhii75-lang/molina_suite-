package com.molina.suite.feature.code

import android.app.Application
import com.molina.suite.core.common.EngineRegistry

/** Komponen engine Code yang dipakai bersama oleh fitur dan fragment tab. */
internal class CodeEngine(application: Application) {
    val textMate = CodeTextMate(application.assets)
    val session = CodeEditorSession(CodeFileRepository(), textMate)
}

/**
 * Satu-satunya titik masuk shell ke modul Code: membuat engine lalu
 * mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas editor.
 */
object CodeEngineModule {

    @Volatile
    private var engine: CodeEngine? = null

    fun install(application: Application, engines: EngineRegistry) {
        engine = CodeEngine(application)
        engines.register(CodeFeature())
    }

    internal fun requireEngine(): CodeEngine =
        checkNotNull(engine) { "CodeEngineModule.install belum dipanggil" }
}
