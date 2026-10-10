package com.molina.suite.feature.settings

import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.feature.settings.library.JsonLibraryRepository

/** Titik masuk shell ke modul Settings: menyiapkan dependensi lalu mendaftarkan fitur. */
object SettingsEngineModule {

    fun install(engines: EngineRegistry, terminalInput: TerminalInputSender) {
        SettingsDependencies.install(terminalInput, JsonLibraryRepository.createDefault())
        engines.register(SettingsFeature())
    }
}
