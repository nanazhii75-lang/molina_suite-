package com.molina.suite.feature.code

import android.app.Application
import java.io.File
import java.io.IOException

/**
 * Lokasi berkas code-server dilihat dari sisi Android (host). Path di dalam
 * Debian (guest) selalu berawalan /opt dan menunjuk ke berkas yang sama.
 */
internal class CodeServerLayout(application: Application) {

    private val prefixDir = File(application.filesDir, "usr")

    val prootDistro = File(prefixDir, "bin/proot-distro")
    val rootfsDir = File(prefixDir, "var/lib/proot-distro/installed-rootfs/debian")

    val molinaDir = File(rootfsDir, "opt/molina")
    val installScript = File(molinaDir, "code-install.sh")
    val statusFile = File(molinaDir, "install.status")
    val installLog = File(molinaDir, "install.log")
    val downloadFile = File(molinaDir, "download/code-server.tar.gz")

    private val codeServerDir = File(rootfsDir, "opt/code-server")
    private val codeServerBinary = File(codeServerDir, "bin/code-server")
    private val versionMarker = File(codeServerDir, ".molina-version")

    fun hasRootfs(): Boolean = rootfsDir.isDirectory && prootDistro.canExecute()

    fun isInstalled(): Boolean =
        codeServerBinary.isFile && readVersionMarker() == CodeServerRelease.VERSION

    /** Fase terakhir yang ditulis skrip pemasangan; PHASE_PREPARE bila belum ada. */
    fun readPhase(): String =
        try {
            statusFile.readText().trim().ifEmpty { PHASE_PREPARE }
        } catch (e: IOException) {
            PHASE_PREPARE
        }

    /** Beberapa baris terakhir log pemasangan, tanpa baris kosong. */
    fun readLogTail(maxLines: Int): String =
        try {
            installLog.readLines().filter { it.isNotBlank() }.takeLast(maxLines).joinToString("\n")
        } catch (e: IOException) {
            ""
        }

    private fun readVersionMarker(): String? =
        try {
            versionMarker.readText().trim()
        } catch (e: IOException) {
            null
        }

    companion object {
        const val GUEST_INSTALL_SCRIPT = "/opt/molina/code-install.sh"

        const val PHASE_PREPARE = "prepare"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_VERIFY = "verify"
        const val PHASE_EXTRACT = "extract"
        const val PHASE_FINISH = "finish"
        const val PHASE_DONE = "done"
        const val PHASE_FAILED = "failed"
    }
}
