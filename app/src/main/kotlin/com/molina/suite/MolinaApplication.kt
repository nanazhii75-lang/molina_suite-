package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.feature.code.CodeEngineModule
import com.molina.suite.feature.mpv.MpvEngineModule
import com.molina.suite.feature.settings.SettingsEngineModule
import com.molina.suite.feature.terminal.TerminalEngineModule
import com.molina.suite.feature.terminal.TermuxBackgroundKeepAlive

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai. */
    val shell: ShellCommandRunner by lazy { TerminalEngineModule.createShellRunner(this) }

    /** Pengetik teks ke sesi aktif tab Terminal, dipakai modul Settings (Library). */
    val terminalInput: TerminalInputSender by lazy { TerminalEngineModule.createInputSender() }

    /** Penjaga CPU untuk proses latar belakang berumur panjang (server code-server). */
    val keepAlive: BackgroundKeepAlive by lazy { TermuxBackgroundKeepAlive(this) }

    override fun onCreate() {
        super.onCreate()
        TerminalEngineModule.install(this, engines)
        CodeEngineModule.install(this, engines)
        MpvEngineModule.install(this, engines)
        SettingsEngineModule.install(engines, terminalInput)
        DaemonStatusBridge.start(engines, daemons)
    }
}
