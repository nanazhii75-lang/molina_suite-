package com.molina.suite.feature.terminal

import android.app.Application
import com.molina.suite.core.common.ShellCommand
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.ShellResult
import com.molina.suite.terminal.shared.termux.TermuxConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

/**
 * Memasang rootfs Debian dari tarball yang dibundel di assets APK. Tarball disalin ke
 * dlcache milik proot-distro supaya `proot-distro install debian` tidak mengunduh ulang
 * (hash tetap diverifikasi oleh proot-distro sendiri).
 */
internal class DebianRootfsInstaller(
    private val application: Application,
    private val shell: ShellCommandRunner = TermuxShellCommandRunner(application)
) {

    enum class Phase { COPYING, INSTALLING }

    sealed interface Outcome {
        object Installed : Outcome
        class Failed(val message: String) : Outcome
    }

    private val prefix = TermuxConstants.TERMUX_PREFIX_DIR_PATH
    private val rootfsDir = File(prefix, "var/lib/proot-distro/installed-rootfs/debian")
    private val cacheDir = File(prefix, "var/lib/proot-distro/dlcache")
    private val prootDistro = File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "proot-distro")

    fun bundledTarballName(): String? = try {
        application.assets.list(ASSET_DIR)?.firstOrNull { it.endsWith(".tar.xz") }
    } catch (e: IOException) {
        null
    }

    fun isBundled(): Boolean = bundledTarballName() != null

    fun isInstalled(): Boolean = File(rootfsDir, "usr/bin").isDirectory

    suspend fun ensureInstalled(onPhase: (Phase) -> Unit): Outcome = installLock.withLock {
        val name = bundledTarballName()
            ?: return@withLock Outcome.Failed("Paket Debian bawaan tidak ditemukan di APK")
        if (isInstalled()) return@withLock Outcome.Installed
        if (!prootDistro.canExecute()) {
            return@withLock Outcome.Failed("proot-distro belum terpasang di bootstrap: ${prootDistro.path}")
        }

        onPhase(Phase.COPYING)
        try {
            stageTarball(name)
        } catch (e: IOException) {
            return@withLock Outcome.Failed("Gagal menyalin paket Debian: ${e.message}")
        }

        onPhase(Phase.INSTALLING)
        val result = withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
            shell.runInBackground(
                ShellCommand(
                    executable = prootDistro.path,
                    arguments = listOf("install", "debian"),
                    label = "Molina: pasang Debian"
                )
            )
        } ?: return@withLock Outcome.Failed("Pemasangan Debian melebihi batas waktu 20 menit")

        when (result) {
            is ShellResult.Failed -> Outcome.Failed(result.reason)
            is ShellResult.Finished ->
                if (result.exitCode == 0 && isInstalled()) {
                    File(cacheDir, name).delete()
                    Outcome.Installed
                } else {
                    val detail = result.stderr.ifBlank { result.stdout }.trim().takeLast(DETAIL_MAX_CHARS)
                    Outcome.Failed("proot-distro berhenti dengan kode ${result.exitCode}\n$detail")
                }
        }
    }

    private fun stageTarball(name: String) {
        val assetPath = "$ASSET_DIR/$name"
        val expected = application.assets.openFd(assetPath).use { it.length }
        val target = File(cacheDir, name)
        if (target.isFile && target.length() == expected) return

        if (application.filesDir.usableSpace < MIN_FREE_BYTES) {
            throw IOException("ruang penyimpanan kurang (butuh sekitar 500 MB bebas)")
        }
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw IOException("tidak bisa membuat ${cacheDir.path}")
        }

        val partial = File(cacheDir, "$name.part")
        partial.delete()
        application.assets.open(assetPath).use { input ->
            partial.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_BYTES) }
        }
        if (partial.length() != expected) {
            partial.delete()
            throw IOException("ukuran hasil salinan tidak cocok dengan paket bawaan")
        }
        target.delete()
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("tidak bisa menamai berkas ${target.name}")
        }
    }

    private companion object {
        const val ASSET_DIR = "molina-debian"
        const val INSTALL_TIMEOUT_MS = 20L * 60L * 1000L
        const val MIN_FREE_BYTES = 500L * 1024L * 1024L
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val DETAIL_MAX_CHARS = 600

        /** Mencegah dua pemasangan berjalan bersamaan (misalnya fragment dibuat ulang). */
        val installLock = Mutex()
    }
}
