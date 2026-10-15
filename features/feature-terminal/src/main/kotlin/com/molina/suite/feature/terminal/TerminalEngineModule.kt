package com.molina.suite.feature.terminal

import android.app.Application
import com.molina.suite.core.common.AppExitBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.terminal.app.TermuxApplication
import com.molina.suite.terminal.shared.termux.TermuxExitHook

/**
 * Satu-satunya titik masuk shell ke engine terminal: menginisialisasi engine
 * lalu mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas Termux.
 */
object TerminalEngineModule {

    /** [appExit] menerima permintaan keluar dari tombol Exit di notifikasi Terminal. */
    fun install(application: Application, engines: EngineRegistry, appExit: AppExitBoard) {
        TermuxApplication.initialize(application)
        TermuxExitHook.setHandler(TermuxExitHook.Handler { appExit.request() })
        engines.register(TerminalFeature())
    }

    /** Pengirim teks ke sesi aktif tab Terminal (tampil interaktif). */
    fun createInputSender(): TerminalInputSender = TermuxTerminalInputSender()

    /** Pelaksana perintah shell yang berjalan lewat engine terminal. */
    fun createShellRunner(application: Application): ShellCommandRunner =
        TermuxShellCommandRunner(application)
}
