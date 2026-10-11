package com.molina.suite.feature.settings

import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.feature.settings.library.LibraryRepository

/**
 * Titik pasok dependensi untuk fragment Settings. Fragment dibuat ulang oleh
 * FragmentManager lewat konstruktor kosong, jadi dependensi diambil dari sini.
 * Diisi sekali oleh [SettingsEngineModule.install] saat Application dibuat.
 */
internal object SettingsDependencies {
    @Volatile private var sender: TerminalInputSender? = null
    @Volatile private var repository: LibraryRepository? = null
    @Volatile private var board: ToolSetupBoard? = null

    fun install(
        terminalInput: TerminalInputSender,
        libraryRepository: LibraryRepository,
        toolSetupBoard: ToolSetupBoard
    ) {
        sender = terminalInput
        repository = libraryRepository
        board = toolSetupBoard
    }

    fun terminalInput(): TerminalInputSender =
        checkNotNull(sender) { "SettingsEngineModule.install belum dipanggil" }

    fun libraryRepository(): LibraryRepository =
        checkNotNull(repository) { "SettingsEngineModule.install belum dipanggil" }

    fun toolSetup(): ToolSetupBoard =
        checkNotNull(board) { "SettingsEngineModule.install belum dipanggil" }
}
