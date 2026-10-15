package com.molina.suite.feature.settings

import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.LibraryDraftBoard
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
    @Volatile private var editorBoard: EditorOpenBoard? = null
    @Volatile private var draftBoard: LibraryDraftBoard? = null

    fun install(
        terminalInput: TerminalInputSender,
        libraryRepository: LibraryRepository,
        toolSetupBoard: ToolSetupBoard,
        editorOpenBoard: EditorOpenBoard,
        libraryDraftBoard: LibraryDraftBoard
    ) {
        sender = terminalInput
        repository = libraryRepository
        board = toolSetupBoard
        editorBoard = editorOpenBoard
        draftBoard = libraryDraftBoard
    }

    fun terminalInput(): TerminalInputSender =
        checkNotNull(sender) { "SettingsEngineModule.install belum dipanggil" }

    fun libraryRepository(): LibraryRepository =
        checkNotNull(repository) { "SettingsEngineModule.install belum dipanggil" }

    fun toolSetup(): ToolSetupBoard =
        checkNotNull(board) { "SettingsEngineModule.install belum dipanggil" }

    fun editorOpen(): EditorOpenBoard =
        checkNotNull(editorBoard) { "SettingsEngineModule.install belum dipanggil" }

    fun libraryDraft(): LibraryDraftBoard =
        checkNotNull(draftBoard) { "SettingsEngineModule.install belum dipanggil" }
}
