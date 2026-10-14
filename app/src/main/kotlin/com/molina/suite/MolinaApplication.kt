package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.code.CodeEngineModule
import com.molina.suite.feature.mpv.MpvEngineModule
import com.molina.suite.feature.settings.SettingsEngineModule
import com.molina.suite.feature.terminal.TerminalEngineModule
import com.molina.suite.feature.terminal.TermuxBackgroundKeepAlive

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    /** Papan permintaan membuka menu pemasangan alat; fitur meminta, Settings yang menangani. */
    val toolSetup = ToolSetupBoard()

    /** Papan permintaan membuka berkas di tab Code; Library meminta, tab Code yang menangani. */
    val editorOpen = EditorOpenBoard()

    /** Pemeriksa alat berbasis katalog menu Paket, dipakai modul mpv (Play by URL). */
    val toolReadiness: ToolReadinessChecker by lazy { SettingsEngineModule.createToolReadinessChecker(this) }

    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai. */
    val shell: ShellCommandRunner by lazy { TerminalEngineModule.createShellRunner(this) }

    /** Pengetik teks ke sesi aktif tab Terminal, dipakai modul Settings (Library). */
    val terminalInput: TerminalInputSender by lazy { TerminalEngineModule.createInputSender() }

    /** Penjaga CPU untuk proses latar belakang berumur panjang (server code-server). */
    val keepAlive: BackgroundKeepAlive by lazy { TermuxBackgroundKeepAlive(this) }

    override fun onCreate() {
        super.onCreate()
        SettingsEngineModule.installCrashRecorder(this)
        SettingsEngineModule.installLogRecorder(this)
        TerminalEngineModule.install(this, engines)
        CodeEngineModule.install(this, engines, editorOpen, terminalInput, toolReadiness, toolSetup, shell)
        MpvEngineModule.install(this, engines, toolReadiness, toolSetup)
        SettingsEngineModule.install(engines, terminalInput, toolSetup, editorOpen)
        DaemonStatusBridge.start(engines, daemons)
    }
}
