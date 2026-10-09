package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.feature.code.CodeEngineModule
import com.molina.suite.feature.terminal.TerminalEngineModule

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai. */
    val shell: ShellCommandRunner by lazy { TerminalEngineModule.createShellRunner(this) }

    override fun onCreate() {
        super.onCreate()
        TerminalEngineModule.install(this, engines)
        CodeEngineModule.install(this, engines, shell)
    }
}
