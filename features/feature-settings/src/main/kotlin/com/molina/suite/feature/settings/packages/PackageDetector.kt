package com.molina.suite.feature.settings.packages

import android.content.Context
import com.molina.suite.feature.settings.debian.DebianStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

internal sealed interface PackageDetection {
    object NoDebian : PackageDetection
    data class Failed(val reason: String) : PackageDetection

    /** [installed] memetakan id [PackageSpec] ke status terpasang. */
    data class Ok(val installed: Map<String, Boolean>) : PackageDetection
}

/**
 * Mendeteksi paket langsung dari rootfs Debian tanpa menjalankan proot: paket apt dari
 * var/lib/dpkg/status, yt-dlp dari berkas venv. Symlink absolut di dalam rootfs tidak
 * ter-resolve di luar proot, jadi berkas venv diperiksa dengan NOFOLLOW_LINKS.
 */
internal object PackageDetector {

    private const val DPKG_STATUS = "var/lib/dpkg/status"
    private const val YTDLP_VENV_BIN = "opt/ytdlp/bin/yt-dlp"
    private const val NODE_BIN = "opt/node/bin/node"
    private const val PACKAGE_PREFIX = "Package: "
    private const val STATUS_INSTALLED = "Status: install ok installed"

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun detect(context: Context): PackageDetection {
        if (!DebianStatus.isInstalled(context)) return PackageDetection.NoDebian
        val root = DebianStatus.rootfsDir(context)
        return try {
            val statusFile = File(root, DPKG_STATUS)
            if (!statusFile.isFile) {
                return PackageDetection.Failed("Berkas status paket tidak ditemukan: ${statusFile.path}")
            }
            val aptInstalled = readInstalled(statusFile)
            val venvPresent = Files.exists(File(root, YTDLP_VENV_BIN).toPath(), LinkOption.NOFOLLOW_LINKS)
            val nodePresent = Files.exists(File(root, NODE_BIN).toPath(), LinkOption.NOFOLLOW_LINKS)
            val result = LinkedHashMap<String, Boolean>()
            for (spec in PackageCatalog.all) {
                result[spec.id] = when (spec.kind) {
                    PackageKind.APT -> aptInstalled.containsAll(spec.aptPackages)
                    PackageKind.YTDLP_VENV -> venvPresent
                    PackageKind.NODE_RUNTIME -> nodePresent
                }
            }
            PackageDetection.Ok(result)
        } catch (e: IOException) {
            PackageDetection.Failed("Gagal membaca status paket: ${e.message}")
        } catch (e: SecurityException) {
            PackageDetection.Failed("Izin membaca status paket ditolak: ${e.message}")
        }
    }

    private fun readInstalled(file: File): Set<String> {
        val installed = HashSet<String>()
        var current: String? = null
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith(PACKAGE_PREFIX) -> current = line.substring(PACKAGE_PREFIX.length).trim()
                    line == STATUS_INSTALLED -> current?.let { installed.add(it) }
                    line.isEmpty() -> current = null
                }
            }
        }
        return installed
    }
}
