package com.molina.suite

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import com.molina.suite.core.common.AppExitBoard
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.LibraryDraftBoard
import com.molina.suite.core.common.PlaybackBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.LoggingShellCommandRunner
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.core.common.X11Controller
import com.molina.suite.feature.code.CodeEngineModule
import com.molina.suite.feature.mpv.MpvEngineModule
import com.molina.suite.feature.settings.SettingsEngineModule
import com.molina.suite.feature.terminal.TerminalEngineModule
import com.molina.suite.feature.terminal.TermuxBackgroundKeepAlive
import com.molina.suite.feature.x11.X11EngineModule
import kotlin.system.exitProcess

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()

    /** Papan status pemutaran untuk mini-player; modul mpv mempublikasikan, shell mengamati. */
    val playback = PlaybackBoard()

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

    /** Pelaksana konversi dokumen (pandoc dan Typst di Debian) untuk modul lain, mis. Ekspor di tab Code. */
    val documents: DocumentConverter by lazy { SettingsEngineModule.createDocumentConverter(this, shell) }

    /** Pengetik teks ke sesi aktif tab Terminal, dipakai modul Settings (Library). */
    val terminalInput: TerminalInputSender by lazy { TerminalEngineModule.createInputSender() }

    /** Penjaga CPU untuk proses latar belakang berumur panjang (server code-server). */
    val keepAlive: BackgroundKeepAlive by lazy { TermuxBackgroundKeepAlive(this) }

    /** Pengendali X11 (pantau memori, hentikan tuntas) untuk notifikasi X11; pelaksananya modul Settings. */
    val x11Controller: X11Controller by lazy { SettingsEngineModule.createX11Controller(this, engines) }

    /** Papan permintaan keluar; fitur (mis. Exit di notifikasi Terminal) meminta, shell menutup aplikasi. */
    val appExit = AppExitBoard()

    override fun onCreate() {
        super.onCreate()
        SettingsEngineModule.installCrashRecorder(this)
        SettingsEngineModule.installLogRecorder(this)
        appExit.setHandler { exitApp() }
        TerminalEngineModule.install(this, engines, appExit)
        CodeEngineModule.install(this, engines, editorOpen, terminalInput, toolReadiness, toolSetup, shell, libraryDraft, documents)
        MpvEngineModule.install(this, engines, toolReadiness, toolSetup, playback, terminalInput)
        SettingsEngineModule.install(engines, terminalInput, toolSetup, editorOpen, libraryDraft)
        X11EngineModule.install(this, engines, shell, keepAlive, x11Controller)
        DaemonStatusBridge.start(engines, daemons)
        X11ErrorToaster.start(this, engines)
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
