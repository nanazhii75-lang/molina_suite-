package com.molina.suite.feature.settings.run

import android.content.Context
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.library.LibraryResult
import com.molina.suite.feature.settings.library.ScriptStager
import java.io.File
import java.io.IOException

/**
 * Satu kali jalan proses. [command] adalah satu baris untuk Debian (tanpa pembungkus) yang
 * menjalankan skrip dengan berkas status [statusFile] sebagai argumen.
 */
internal data class RunSession(
    val kind: RunKind,
    val statusFile: File,
    val startedAt: Long,
    val command: String
)

internal object RunSessions {

    private const val RUN_DIR_NAME = ".run"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /**
     * Menyiapkan skrip di folder bersama dan nama berkas status yang unik, lalu membersihkan
     * berkas status lama. Memblokir I/O: panggil dari Dispatchers.IO.
     */
    fun prepare(context: Context, kind: RunKind): LibraryResult<RunSession> {
        val script = try {
            context.assets.open(kind.scriptAsset).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            return LibraryResult.Failure("Skrip ${kind.scriptAsset} tidak terbaca: ${e.message}")
        }
        val scriptPath = when (val staged = ScriptStager.stage(kind.scriptId, script)) {
            is LibraryResult.Failure -> return staged
            is LibraryResult.Ok -> staged.value
        }
        val dir = File(MolinaStorage.sharedRoot(), RUN_DIR_NAME)
        try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                return LibraryResult.Failure("Gagal membuat folder ${dir.path}; izin penyimpanan belum diberikan")
            }
            cleanOld(dir)
        } catch (e: SecurityException) {
            return LibraryResult.Failure("Izin menulis ${dir.path} ditolak: ${e.message}")
        }
        val startedAt = System.currentTimeMillis()
        val statusFile = File(dir, "${kind.prefix}-$startedAt.status")
        val terminalStatusPath = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/$RUN_DIR_NAME/${statusFile.name}"
        val command = "bash ${ShellLine.quote(scriptPath)} ${ShellLine.quote(terminalStatusPath)}"
        return LibraryResult.Ok(RunSession(kind, statusFile, startedAt, command))
    }

    private fun cleanOld(dir: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }
}
