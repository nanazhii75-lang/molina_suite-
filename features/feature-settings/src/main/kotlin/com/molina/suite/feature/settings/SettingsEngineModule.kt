package com.molina.suite.feature.settings

import android.content.Context
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.settings.library.JsonLibraryRepository
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

    /** Pemeriksa alat yang memakai katalog dan detektor menu Paket, untuk modul fitur lain. */
    fun createToolReadinessChecker(context: Context): ToolReadinessChecker =
        PackageToolReadinessChecker(context)
}
