package com.molina.suite.feature.code

import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ScriptRunTarget
import com.molina.suite.core.common.TerminalInputResult
import com.molina.suite.core.common.TerminalInputSender
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/** Hasil mengirim perintah jalankan ke tab Terminal. */
internal sealed interface CodeRunOutcome {
    object Started : CodeRunOutcome
    object Unsupported : CodeRunOutcome
    object NoSwitcher : CodeRunOutcome
    data class Rejected(val reason: String) : CodeRunOutcome
}

/**
 * Menjalankan berkas lewat tab Terminal. Baris perintah disusun oleh [ScriptRunLine] di
 * core-common; di sini hanya pengiriman ke sesi aktif, dengan menunggu prompt siap sampai 30 detik
 * bila tab Terminal baru dibuka. Tidak mengenal Settings maupun kelas Terminal.
 */
internal object CodeScriptRunner {

    private const val HEAD_BYTES = 128
    private const val MAX_ATTEMPTS = 60
    private const val RETRY_DELAY_MS = 500L

    /** Awal isi berkas untuk memeriksa shebang; null bila tidak terbaca. Panggil dari Dispatchers.IO. */
    fun readHead(file: File): String? = try {
        file.inputStream().use { stream ->
            val buffer = ByteArray(HEAD_BYTES)
            val count = stream.read(buffer)
            if (count <= 0) "" else String(buffer, 0, count, Charsets.UTF_8)
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** Panggil dari main thread. */
    suspend fun run(
        sender: TerminalInputSender,
        switcher: HostTabSwitcher?,
        file: File,
        head: String,
        target: ScriptRunTarget
    ): CodeRunOutcome {
        val line = CodeRunCommand.lineFor(file.absolutePath, head, target) ?: return CodeRunOutcome.Unsupported
        val host = switcher ?: return CodeRunOutcome.NoSwitcher

        // Perintah selalu diketik ke sesi Terminal yang sudah ada (tidak membuat tab baru);
        // folder berkas dicapai lewat cd di baris yang sama.
        val workdir = ScriptRunLine.normalizePath(file.absolutePath).substringBeforeLast('/', "").ifEmpty { "/" }
        val typed = "cd " + ScriptRunLine.quote(workdir) + " 2>/dev/null; " + line
        var result = sender.sendLineWhenReady(typed)
        host.showEngine(EngineId.TERMINAL)
        var attempts = 0
        while (result is TerminalInputResult.Rejected && attempts < MAX_ATTEMPTS) {
            delay(RETRY_DELAY_MS)
            result = sender.sendLineWhenReady(typed)
            attempts++
        }
        return when (result) {
            is TerminalInputResult.Rejected -> CodeRunOutcome.Rejected(result.reason)
            else -> CodeRunOutcome.Started
        }
    }
}
