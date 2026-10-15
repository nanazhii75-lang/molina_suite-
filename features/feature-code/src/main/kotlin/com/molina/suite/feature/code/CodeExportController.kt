package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityLog
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.core.storage.MolinaStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Alur Ekspor di menu titik tiga: memilih format tujuan, menyimpan berkas bila berubah, meminta
 * [DocumentConverter] di core-common mengonversinya ke /sdcard/Molina/Export, lalu menampilkan
 * hasilnya. Modul Code tidak mengenal pandoc, Typst, maupun Debian; semuanya di balik kontrak.
 * Fragment hanya menyediakan [Host]; tidak ada logika ekspor di tampilan tab.
 *
 * Hanya nama berkas dan hasilnya yang dicatat ke log aktivitas, tidak pernah isinya.
 */
internal class CodeExportController(
    private val host: Host,
    private val converter: DocumentConverter
) {

    /** Hal-hal milik tab Code yang dibutuhkan alur ini. */
    interface Host {
        val context: Context?
        val scope: CoroutineScope
        fun currentFile(): File?
        fun isDirty(): Boolean

        /** Menyimpan dokumen aktif; false bila gagal (alasannya sudah ditampilkan oleh tab). */
        suspend fun save(): Boolean

        /** Membuka menu pemasangan di Setting dengan [toolIds] disorot. */
        fun openSetup(target: ToolSetupTarget, toolIds: List<String>)
    }

    private var job: Job? = null

    /** True bila [file] punya ekstensi yang bisa diekspor; dipakai untuk mengaktifkan item menu. */
    fun isAvailableFor(file: File?): Boolean =
        file != null && converter.targetsFor(file.extension).isNotEmpty()

    fun start() {
        val context = host.context ?: return
        val file = host.currentFile()
        if (file == null) {
            CodeExportDialogs.showMessage(context, R.string.code_export_failed_title, context.getString(R.string.code_export_untitled))
            return
        }
        val source = DocumentFormat.fromExtension(file.extension)
        val targets = converter.targetsFor(file.extension)
        if (source == null || targets.isEmpty()) {
            CodeExportDialogs.showMessage(context, R.string.code_export_failed_title, context.getString(R.string.code_export_unsupported))
            return
        }
        CodeChoiceDialog.show(context, R.string.code_export_title, targets.map { label(context, it) }, -1) { index ->
            confirmAndRun(file, source, targets[index])
        }
    }

    private fun confirmAndRun(file: File, source: DocumentFormat, target: DocumentFormat) {
        val context = host.context ?: return
        val baseName = file.nameWithoutExtension.ifEmpty { FALLBACK_NAME }
        val output = File(MolinaStorage.exportDir(), baseName + "." + target.extension)
        if (!output.exists()) {
            run(file, source, target, output)
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_export_overwrite_title)
            .setMessage(context.getString(R.string.code_export_overwrite_message, output.name))
            .setPositiveButton(R.string.code_export_overwrite_confirm) { _, _ -> run(file, source, target, output) }
            .setNegativeButton(R.string.code_picker_cancel, null)
            .show()
    }

    private fun run(file: File, source: DocumentFormat, target: DocumentFormat, output: File) {
        job?.cancel()
        job = host.scope.launch {
            if (host.isDirty() && !host.save()) return@launch
            val context = host.context ?: return@launch
            val request = try {
                ConversionRequest(file.absolutePath, output.absolutePath, source, target)
            } catch (e: IllegalArgumentException) {
                CodeExportDialogs.showMessage(context, R.string.code_export_failed_title, e.message.orEmpty())
                return@launch
            }
            val running = CodeExportDialogs.showRunning(
                context,
                context.getString(R.string.code_export_running, file.name, label(context, target))
            ) { job?.cancel() }
            val result = try {
                Activities.log.track(ActivitySource.CODE, "Ekspor ${file.name} ke .${target.extension}") { entry ->
                    converter.convert(request).also { entry.reportExport(it) }
                }
            } finally {
                running.dismiss()
            }
            val current = host.context ?: return@launch
            show(current, result)
        }
    }

    private fun show(context: Context, result: ConversionResult) {
        when (result) {
            is ConversionResult.Success -> CodeExportDialogs.showMessage(
                context,
                R.string.code_export_ok_title,
                context.getString(R.string.code_export_ok_message, result.outputPath)
            )
            is ConversionResult.ToolMissing -> CodeExportDialogs.showMessage(
                context,
                R.string.code_export_failed_title,
                context.getString(
                    R.string.code_export_missing,
                    result.toolIds.joinToString(", ") { id -> id.replaceFirstChar { it.uppercase() } }
                ),
                R.string.code_export_open_packages
            ) { host.openSetup(ToolSetupTarget.PACKAGES, result.toolIds) }
            is ConversionResult.NoDebian -> CodeExportDialogs.showMessage(
                context,
                R.string.code_export_failed_title,
                context.getString(R.string.code_export_no_debian),
                R.string.code_export_open_debian
            ) { host.openSetup(ToolSetupTarget.DEBIAN, emptyList()) }
            is ConversionResult.Failure -> CodeExportDialogs.showMessage(
                context,
                R.string.code_export_failed_title,
                result.reason
            )
        }
    }

    private fun label(context: Context, format: DocumentFormat): String = context.getString(
        when (format) {
            DocumentFormat.MARKDOWN -> R.string.code_export_format_markdown
            DocumentFormat.HTML -> R.string.code_export_format_html
            DocumentFormat.DOCX -> R.string.code_export_format_docx
            DocumentFormat.EPUB -> R.string.code_export_format_epub
            DocumentFormat.RST -> R.string.code_export_format_rst
            DocumentFormat.PDF -> R.string.code_export_format_pdf
            DocumentFormat.PLAIN -> R.string.code_export_format_plain
        }
    )

    private companion object {
        const val FALLBACK_NAME = "dokumen"
    }
}

/** Mencatat hasil ekspor; hanya status dan kode keluar, tidak pernah pesan galat yang bisa memuat isi berkas. */
private fun ActivityLog.Activity.reportExport(result: ConversionResult) {
    when (result) {
        is ConversionResult.Success -> Unit
        is ConversionResult.ToolMissing -> end(false, "alat belum terpasang: " + result.toolIds.joinToString(", "))
        is ConversionResult.NoDebian -> end(false, "Debian belum terpasang")
        is ConversionResult.Failure -> end(false, result.exitCode?.let { "gagal (kode $it)" } ?: "gagal")
    }
}
