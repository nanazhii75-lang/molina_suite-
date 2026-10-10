package com.molina.suite.feature.code

import android.app.Application
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner

/** Komponen engine Code yang dipakai bersama oleh fitur dan fragment tab. */
internal class CodeEngine(
    application: Application,
    runner: ShellCommandRunner,
    keepAlive: BackgroundKeepAlive
) {
    val layout = CodeServerLayout(application)
    val installer = CodeServerInstaller(application, runner, layout)
    val controller = CodeServerController(application, runner, layout, keepAlive)
}

/**
 * Satu-satunya titik masuk shell ke modul Code: membuat engine lalu
 * mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas code-server.
 */
object CodeEngineModule {

    @Volatile
    private var engine: CodeEngine? = null

    fun install(
        application: Application,
        engines: EngineRegistry,
        shell: ShellCommandRunner,
        daemons: DaemonStatusBoard,
        keepAlive: BackgroundKeepAlive
    ) {
        val created = CodeEngine(application, shell, keepAlive)
        engine = created
        engines.register(CodeFeature(created, daemons))
    }

    internal fun requireEngine(): CodeEngine =
        checkNotNull(engine) { "CodeEngineModule.install belum dipanggil" }
}
