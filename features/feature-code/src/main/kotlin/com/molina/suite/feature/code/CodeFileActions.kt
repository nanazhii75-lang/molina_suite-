package com.molina.suite.feature.code

import android.content.Context
import androidx.annotation.StringRes
import com.molina.suite.core.common.ScriptRunLine
import java.io.File

/** Aksi yang bisa ditawarkan untuk berkas aktif di lembar Aksi file. */
internal enum class CodeFileAction(@StringRes val label: Int) {
    RUN_DEBIAN(R.string.code_tree_run_debian),
    RUN_HOST(R.string.code_tree_run_host),
    PREVIEW(R.string.code_action_preview),
    PLAY_MPV(R.string.code_action_play_mpv),
    PLAY_MPV_OFF(R.string.code_action_play_mpv_off),
    CHECK(R.string.code_action_check),
    EXPORT(R.string.code_menu_export),
    LIBRARY(R.string.code_menu_library)
}

/**
 * Lembar aksi satu pintu: mengumpulkan aksi yang berlaku untuk sebuah berkas dari ekstensinya
 * (Jalankan, Tinjau, Periksa, Ekspor, Kirim ke Library). Hanya menyusun daftar dan
 * menampilkannya; setiap aksi dijalankan oleh tab lewat alur yang sudah ada.
 */
internal object CodeFileActions {

    fun available(
        file: File?,
        canExport: Boolean,
        hasContent: Boolean,
        canPlay: Boolean = false,
        playEnabled: Boolean = true
    ): List<CodeFileAction> {
        if (file == null) return emptyList()
        val name = file.name
        val actions = ArrayList<CodeFileAction>()
        if (CodeRunCommand.isRunnable(name)) actions.add(CodeFileAction.RUN_DEBIAN)
        if (ScriptRunLine.isRunnableName(name)) actions.add(CodeFileAction.RUN_HOST)
        if (CodePreviewKind.of(name) != null) actions.add(CodeFileAction.PREVIEW)
        if (canPlay) actions.add(if (playEnabled) CodeFileAction.PLAY_MPV else CodeFileAction.PLAY_MPV_OFF)
        if (CodeCheckLanguage.of(name) != null) actions.add(CodeFileAction.CHECK)
        if (canExport) actions.add(CodeFileAction.EXPORT)
        if (hasContent) actions.add(CodeFileAction.LIBRARY)
        return actions
    }

    fun show(context: Context, actions: List<CodeFileAction>, onChosen: (CodeFileAction) -> Unit) {
        CodeChoiceDialog.show(
            context,
            R.string.code_actions_title,
            actions.map { context.getString(it.label) },
            -1
        ) { index -> actions.getOrNull(index)?.let(onChosen) }
    }
}
