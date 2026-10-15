package com.molina.suite.feature.settings

import android.app.Application
import android.content.Context
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.LibraryDraftBoard
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.settings.export.PandocDocumentConverter
import com.molina.suite.feature.settings.library.JsonLibraryRepository
import com.molina.suite.feature.settings.logcat.CrashNotifier
import com.molina.suite.feature.settings.logcat.CrashRecorder
import com.molina.suite.feature.settings.logcat.LogRecorder
import com.molina.suite.feature.settings.logcat.ProcessTracking
import com.molina.suite.feature.settings.packages.PackageToolReadinessChecker

/** Titik masuk shell ke modul Settings: menyiapkan dependensi lalu mendaftarkan fitur. */
object SettingsEngineModule {

    fun install(
        engines: EngineRegistry,
        terminalInput: TerminalInputSender,
        toolSetup: ToolSetupBoard,
        editorOpen: EditorOpenBoard,
        libraryDraft: LibraryDraftBoard
    ) {
        SettingsDependencies.install(
            terminalInput,
            JsonLibraryRepository.createDefault(),
            toolSetup,
            editorOpen,
            libraryDraft
        )
        engines.register(SettingsFeature())
    }

    /** Memasang pencatat crash (stack trace + ekor logcat) yang bisa dilihat dari layar Logcat. */
    fun installCrashRecorder(application: Application) = CrashRecorder.install(application)

    /** Memulai perekam log latar: log mpv, Terminal, dan Code terkumpul sejak aplikasi dibuka. */
    fun installLogRecorder(application: Application) {
        ProcessTracking.install(application)
        LogRecorder.install(application)
        // Crash yang belum sempat diberitahukan (proses mati sebelum notifikasi terkirim, atau crash
        // native yang baru terdeteksi sekarang) ditampilkan di bilah notifikasi.
        Thread({ CrashNotifier.notifyPending(application) }, "CrashNotify").apply { isDaemon = true }.start()
    }

    /** Dipanggil sebelum aplikasi sengaja ditutup: mencatat keluar normal dan menyimpan sisa log ke riwayat. */
    fun prepareExit(application: Application) {
        ProcessTracking.markCleanExit(application)
        LogRecorder.get(application).flushNow()
    }

    /** Pemeriksa alat yang memakai katalog dan detektor menu Paket, untuk modul fitur lain. */
    fun createToolReadinessChecker(context: Context): ToolReadinessChecker =
        PackageToolReadinessChecker(context)

    /** Pelaksana konversi dokumen (pandoc dan Typst di Debian) yang memakai [shell] untuk menjalankan perintah. */
    fun createDocumentConverter(context: Context, shell: ShellCommandRunner): DocumentConverter =
        PandocDocumentConverter(context, shell)
}
