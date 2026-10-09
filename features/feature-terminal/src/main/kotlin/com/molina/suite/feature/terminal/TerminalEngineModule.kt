package com.molina.suite.feature.terminal

import android.app.Application
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.terminal.app.TermuxApplication

/**
 * Satu-satunya titik masuk shell ke engine terminal: menginisialisasi engine
 * lalu mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas Termux.
 */
object TerminalEngineModule {

    fun install(application: Application, engines: EngineRegistry) {
        TermuxApplication.initialize(application)
        engines.register(TerminalFeature())
    }

    /** Pelaksana perintah shell yang berjalan lewat engine terminal. */
    fun createShellRunner(application: Application): ShellCommandRunner =
        TermuxShellCommandRunner(application)
}
