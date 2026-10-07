package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.feature.terminal.TerminalEngineModule

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    override fun onCreate() {
        super.onCreate()
        TerminalEngineModule.install(this, engines)
    }
}
