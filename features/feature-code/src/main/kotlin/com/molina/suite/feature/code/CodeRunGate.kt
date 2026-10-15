package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ScriptRunTarget
import com.molina.suite.core.common.ToolReadiness
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupTarget
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Pemeriksa alat sebelum Jalankan: bila alat yang dibutuhkan bahasa berkas belum terpasang di
 * Debian, pengguna ditawari membuka menu Paket (atau tetap menjalankan). Pemeriksaannya lewat
 * kontrak [ToolReadinessChecker] di core-common, sama seperti Lakukan; tab hanya menyediakan [Host].
 * Status yang tidak terbaca atau id yang tidak terdaftar tidak menghalangi: biarkan galat asli
 * yang tampil di Terminal.
 */
internal class CodeRunGate(
    private val host: Host,
    private val checker: ToolReadinessChecker
) {

    /** Hal-hal milik tab Code yang dibutuhkan alur ini. */
    interface Host {
        val context: Context?

        /** Membuka menu pemasangan di Setting dengan [toolIds] disorot. */
        fun openSetup(target: ToolSetupTarget, toolIds: List<String>)
    }

    /** True bila [file] boleh langsung dijalankan; false bila dibatalkan atau pengguna dialihkan ke Setting. */
    suspend fun allowRun(file: File, head: String, target: ScriptRunTarget): Boolean {
        if (target != ScriptRunTarget.DEBIAN || ScriptRunLine.hasShebang(head)) return true
        val language = CodeCheckLanguage.of(file.name) ?: return true
        if (language.toolIds.isEmpty() || !CodeRunCommand.isRunnable(file.name)) return true
        return when (val readiness = checker.check(language.toolIds)) {
            is ToolReadiness.Missing -> ask(
                file.name,
                readiness.tools.joinToString(", ") { it.name },
                ToolSetupTarget.PACKAGES,
                readiness.tools.map { it.id },
                canForce = true
            )
            is ToolReadiness.NoDebian -> ask(
                file.name, "", ToolSetupTarget.DEBIAN, emptyList(), canForce = false
            )
            else -> true
        }
    }

    private suspend fun ask(
        fileName: String,
        tools: String,
        setupTarget: ToolSetupTarget,
        setupToolIds: List<String>,
        canForce: Boolean
    ): Boolean {
        val context = host.context ?: return false
        return suspendCancellableCoroutine<Boolean> { continuation ->
            val message = if (canForce) {
                context.getString(R.string.code_run_missing_message, fileName, tools)
            } else {
                context.getString(R.string.code_run_no_debian)
            }
            val builder = AlertDialog.Builder(context)
                .setTitle(R.string.code_run_missing_title)
                .setMessage(message)
                .setPositiveButton(
                    if (canForce) R.string.code_run_open_packages else R.string.code_run_open_debian
                ) { _, _ ->
                    host.openSetup(setupTarget, setupToolIds)
                    if (continuation.isActive) continuation.resume(false)
                }
                .setNegativeButton(R.string.code_picker_cancel) { _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                }
                .setOnCancelListener {
                    if (continuation.isActive) continuation.resume(false)
                }
            if (canForce) {
                builder.setNeutralButton(R.string.code_run_anyway) { _, _ ->
                    if (continuation.isActive) continuation.resume(true)
                }
            }
            val dialog = builder.show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }
    }
}
