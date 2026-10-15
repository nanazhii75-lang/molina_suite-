package com.molina.suite

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import com.molina.suite.core.common.AppExitBoard
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.LibraryDraftBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.LoggingShellCommandRunner
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.code.CodeEngineModule
import com.molina.suite.feature.mpv.MpvEngineModule
import com.molina.suite.feature.settings.SettingsEngineModule
import com.molina.suite.feature.terminal.TerminalEngineModule
import com.molina.suite.feature.terminal.TermuxBackgroundKeepAlive
import kotlin.system.exitProcess

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    /** Papan permintaan membuka menu pemasangan alat; fitur meminta, Settings yang menangani. */
    val toolSetup = ToolSetupBoard()

    /** Papan permintaan membuka berkas di tab Code; Library meminta, tab Code yang menangani. */
    val editorOpen = EditorOpenBoard()

    /** Papan kiriman skrip dari tab Code ke Library; Code mengirim, Library yang menangani. */
    val libraryDraft = LibraryDraftBoard()

    /** Pemeriksa alat berbasis katalog menu Paket, dipakai modul mpv (Play by URL). */
    val toolReadiness: ToolReadinessChecker by lazy { SettingsEngineModule.createToolReadinessChecker(this) }

    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai, setiap perintahnya dicatat ke logcat. */
    val shell: ShellCommandRunner by lazy {
        LoggingShellCommandRunner(TerminalEngineModule.createShellRunner(this))
    }

    /** Pengetik teks ke sesi aktif tab Terminal, dipakai modul Settings (Library). */
    val terminalInput: TerminalInputSender by lazy { TerminalEngineModule.createInputSender() }

    /** Penjaga CPU untuk proses latar belakang berumur panjang (server code-server). */
    val keepAlive: BackgroundKeepAlive by lazy { TermuxBackgroundKeepAlive(this) }

    /** Papan permintaan keluar; fitur (mis. Exit di notifikasi Terminal) meminta, shell menutup aplikasi. */
    val appExit = AppExitBoard()

    override fun onCreate() {
        super.onCreate()
        SettingsEngineModule.installCrashRecorder(this)
        SettingsEngineModule.installLogRecorder(this)
        appExit.setHandler { exitApp() }
        TerminalEngineModule.install(this, engines, appExit)
        CodeEngineModule.install(this, engines, editorOpen, terminalInput, toolReadiness, toolSetup, shell, libraryDraft)
        MpvEngineModule.install(this, engines, toolReadiness, toolSetup)
        SettingsEngineModule.install(engines, terminalInput, toolSetup, editorOpen, libraryDraft)
        DaemonStatusBridge.start(engines, daemons)
    }

    /**
     * Menutup seluruh aplikasi: mencatat keluar sengaja dan menyimpan sisa log lebih dulu supaya
     * proses berikutnya tidak menilainya mati mendadak, menutup semua task (hilang dari recents),
     * lalu mematikan proses dengan SIGKILL seperti `kill -9`.
     */
    private fun exitApp() {
        SettingsEngineModule.prepareExit(this)
        try {
            getSystemService(ActivityManager::class.java)?.appTasks?.forEach { it.finishAndRemoveTask() }
        } catch (e: RuntimeException) {
            // Tugas tidak bisa ditutup; proses tetap dimatikan di bawah.
        }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
