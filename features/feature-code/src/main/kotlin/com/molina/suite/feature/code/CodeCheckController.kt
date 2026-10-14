package com.molina.suite.feature.code

import android.content.Context
import com.molina.suite.core.common.ToolReadiness
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Alur fitur Lakukan di menu titik tiga: memeriksa alat yang dibutuhkan (lewat kontrak
 * [ToolReadinessChecker] di core-common), menampilkan dialog Kompilator, menyimpan berkas bila
 * berubah, menjalankan pemeriksa lewat [CodeCheckRunner], lalu menampilkan hasilnya. Fragment
 * hanya menyediakan [Host]; tidak ada logika pemeriksaan di tampilan tab.
 */
internal class CodeCheckController(
    private val host: Host,
    private val checker: ToolReadinessChecker,
    private val runner: CodeCheckRunner
) {

    /** Hal-hal milik tab Code yang dibutuhkan alur ini. */
    interface Host {
        val context: Context?
        val scope: CoroutineScope
        fun currentFile(): File?
        fun isDirty(): Boolean

        /** Menyimpan dokumen aktif; false bila gagal (alasannya sudah ditampilkan oleh tab). */
        suspend fun save(): Boolean
        fun goToLine(line: Int): Boolean

        /** Membuka menu pemasangan di Setting dengan [toolIds] disorot. */
        fun openSetup(target: ToolSetupTarget, toolIds: List<String>)
    }

    private var job: Job? = null

    fun start() {
        val context = host.context ?: return
        val file = host.currentFile()
        val language = file?.let { CodeCheckLanguage.of(it.name) }
        if (file == null || language == null) {
            CodeCheckDialogs.showSetup(
                context,
                unavailableSetup(context, file),
                onRun = {},
                onOpenSetup = { host.openSetup(ToolSetupTarget.PACKAGES, emptyList()) }
            )
            return
        }
        job?.cancel()
        job = host.scope.launch {
            val readiness = checker.check(language.toolIds)
            val versions = language.versionToolId?.let { checker.versions(listOf(it)) }.orEmpty()
            val current = host.context ?: return@launch
            val setup = setupFor(current, language, readiness, versions)
            CodeCheckDialogs.showSetup(
                current,
                setup,
                onRun = { run(file, language) },
                onOpenSetup = { host.openSetup(setup.setupTarget, setup.setupToolIds) }
            )
        }
    }

    private fun run(file: File, language: CodeCheckLanguage) {
        job?.cancel()
        job = host.scope.launch {
            if (host.isDirty() && !host.save()) return@launch
            val context = host.context ?: return@launch
            val running = CodeCheckDialogs.showRunning(
                context,
                context.getString(R.string.code_check_running, file.name, language.label)
            ) { job?.cancel() }
            val result = try {
                runner.run(language, file)
            } finally {
                running.dismiss()
            }
            val current = host.context ?: return@launch
            when (result) {
                is CodeCheckRunResult.Finished ->
                    CodeCheckDialogs.showResult(current, file.name, language, result.outcome) { line -> host.goToLine(line) }
                is CodeCheckRunResult.Unavailable ->
                    CodeCheckDialogs.showMessage(current, R.string.code_check_failed_title, result.reason)
            }
        }
    }

    private fun setupFor(
        context: Context,
        language: CodeCheckLanguage,
        readiness: ToolReadiness,
        versions: Map<String, String>
    ): CodeCheckSetup {
        val version = language.versionToolId?.let { versions[it] }
        val label = if (version != null) "${language.label} ($version)" else language.label
        val dirtyNote = if (host.isDirty()) context.getString(R.string.code_check_dirty_note) else null
        return when (readiness) {
            is ToolReadiness.Ready -> CodeCheckSetup(
                label, joinNotes(dirtyNote), true, ToolSetupTarget.PACKAGES, emptyList()
            )
            is ToolReadiness.Unknown -> CodeCheckSetup(
                label,
                joinNotes(context.getString(R.string.code_check_unknown), dirtyNote),
                true,
                ToolSetupTarget.PACKAGES,
                emptyList()
            )
            is ToolReadiness.Missing -> CodeCheckSetup(
                label + context.getString(R.string.code_check_not_installed),
                context.getString(R.string.code_check_missing, readiness.tools.joinToString(", ") { it.name }),
                false,
                ToolSetupTarget.PACKAGES,
                readiness.tools.map { it.id }
            )
            is ToolReadiness.NoDebian -> CodeCheckSetup(
                label, context.getString(R.string.code_check_no_debian), false, ToolSetupTarget.DEBIAN, emptyList()
            )
            is ToolReadiness.Unregistered -> CodeCheckSetup(
                label,
                context.getString(R.string.code_check_unregistered, readiness.ids.joinToString(", ")),
                false,
                ToolSetupTarget.PACKAGES,
                emptyList()
            )
        }
    }

    private fun unavailableSetup(context: Context, file: File?): CodeCheckSetup {
        val note = when {
            file == null -> context.getString(R.string.code_check_untitled)
            else -> {
                val extension = file.name.substringAfterLast('.', "")
                when {
                    extension.equals("kts", ignoreCase = true) -> context.getString(R.string.code_check_kts)
                    extension.isEmpty() -> context.getString(R.string.code_check_unsupported_no_extension)
                    else -> context.getString(R.string.code_check_unsupported, ".$extension")
                }
            }
        }
        return CodeCheckSetup(
            context.getString(R.string.code_check_none),
            note,
            false,
            ToolSetupTarget.PACKAGES,
            emptyList()
        )
    }

    private fun joinNotes(vararg notes: String?): String? =
        notes.filterNotNull().joinToString("\n").ifEmpty { null }
}
