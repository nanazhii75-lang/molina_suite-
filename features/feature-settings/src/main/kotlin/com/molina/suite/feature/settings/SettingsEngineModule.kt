package com.molina.suite.feature.settings

import android.app.Application
import android.content.Context
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.settings.library.JsonLibraryRepository
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
        editorOpen: EditorOpenBoard
    ) {
        SettingsDependencies.install(terminalInput, JsonLibraryRepository.createDefault(), toolSetup, editorOpen)
        engines.register(SettingsFeature())
    }

    /** Memasang pencatat crash (stack trace + ekor logcat) yang bisa dilihat dari layar Logcat. */
    fun installCrashRecorder(application: Application) = CrashRecorder.install(application)

    /** Memulai perekam log latar: log mpv, Terminal, dan Code terkumpul sejak aplikasi dibuka. */
    fun installLogRecorder(application: Application) {
        ProcessTracking.install(application)
        LogRecorder.install(application)
    }

    /** Dipanggil sebelum aplikasi sengaja ditutup: mencatat keluar normal dan menyimpan sisa log ke riwayat. */
    fun prepareExit(application: Application) {
        ProcessTracking.markCleanExit(application)
        LogRecorder.get(application).flushNow()
    }

    /** Pemeriksa alat yang memakai katalog dan detektor menu Paket, untuk modul fitur lain. */
    fun createToolReadinessChecker(context: Context): ToolReadinessChecker =
        PackageToolReadinessChecker(context)
}
