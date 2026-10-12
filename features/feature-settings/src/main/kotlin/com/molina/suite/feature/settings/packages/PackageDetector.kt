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

    /**
     * [installed] memetakan id [PackageSpec] ke status terpasang; [versions] memetakan id alat
     * terpasang ke versinya (kosong bila versi tidak terbaca).
     */
    data class Ok(
        val installed: Map<String, Boolean>,
        val versions: Map<String, String> = emptyMap()
    ) : PackageDetection
}

/**
 * Mendeteksi paket langsung dari rootfs Debian tanpa menjalankan proot: paket apt dari
 * var/lib/dpkg/status, yt-dlp dari berkas venv, Node.js dari header rilisnya. Symlink absolut
 * di dalam rootfs tidak ter-resolve di luar proot, jadi berkas venv diperiksa dengan
 * NOFOLLOW_LINKS. Versi dibaca sekali jalan dari berkas yang sama, tanpa menjalankan proses.
 */
internal object PackageDetector {

    private const val DPKG_STATUS = "var/lib/dpkg/status"
    private const val YTDLP_VENV_BIN = "opt/ytdlp/bin/yt-dlp"
    private const val YTDLP_VENV_LIB = "opt/ytdlp/lib"
    private const val NODE_BIN = "opt/node/bin/node"
    private const val NODE_VERSION_HEADER = "opt/node/include/node/node_version.h"
    private const val PACKAGE_PREFIX = "Package: "
    private const val VERSION_PREFIX = "Version: "
    private const val STATUS_INSTALLED = "Status: install ok installed"
    private const val YTDLP_DIST_PREFIX = "yt_dlp-"
    private const val DIST_INFO_SUFFIX = ".dist-info"
    private val NODE_VERSION_LINE = Regex("""^#define NODE_(MAJOR|MINOR|PATCH)_VERSION (\d+)""")

    private class Dpkg(val installed: Set<String>, val versions: Map<String, String>)

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun detect(context: Context): PackageDetection {
        if (!DebianStatus.isInstalled(context)) return PackageDetection.NoDebian
        val root = DebianStatus.rootfsDir(context)
        return try {
            val statusFile = File(root, DPKG_STATUS)
            if (!statusFile.isFile) {
                return PackageDetection.Failed("Berkas status paket tidak ditemukan: ${statusFile.path}")
            }
            val dpkg = readDpkg(statusFile)
            val venvPresent = Files.exists(File(root, YTDLP_VENV_BIN).toPath(), LinkOption.NOFOLLOW_LINKS)
            val nodePresent = Files.exists(File(root, NODE_BIN).toPath(), LinkOption.NOFOLLOW_LINKS)
            val installed = LinkedHashMap<String, Boolean>()
            val versions = LinkedHashMap<String, String>()
            for (spec in PackageCatalog.all) {
                val present = when (spec.kind) {
                    PackageKind.APT -> dpkg.installed.containsAll(spec.aptPackages)
                    PackageKind.YTDLP_VENV -> venvPresent
                    PackageKind.NODE_RUNTIME -> nodePresent
                }
                installed[spec.id] = present
                if (!present) continue
                val version = when (spec.kind) {
                    PackageKind.APT -> spec.versionPackage?.let { dpkg.versions[it] }?.let { shortAptVersion(it) }
                    PackageKind.YTDLP_VENV -> ytdlpVersion(root)
                    PackageKind.NODE_RUNTIME -> nodeVersion(root)
                }
                if (version != null) versions[spec.id] = version
            }
            PackageDetection.Ok(installed, versions)
        } catch (e: IOException) {
            PackageDetection.Failed("Gagal membaca status paket: ${e.message}")
        } catch (e: SecurityException) {
            PackageDetection.Failed("Izin membaca status paket ditolak: ${e.message}")
        }
    }

    /** Membuang epoch dan revisi Debian: "7:5.1.6-0+deb12u1" menjadi "5.1.6". */
    private fun shortAptVersion(raw: String): String =
        raw.substringAfter(':').substringBefore('-').ifEmpty { raw }

    private fun readDpkg(file: File): Dpkg {
        val installed = HashSet<String>()
        val versions = HashMap<String, String>()
        var current: String? = null
        var currentInstalled = false
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith(PACKAGE_PREFIX) -> {
                        current = line.substring(PACKAGE_PREFIX.length).trim()
                        currentInstalled = false
                    }
                    line == STATUS_INSTALLED -> current?.let {
                        installed.add(it)
                        currentInstalled = true
                    }
                    line.startsWith(VERSION_PREFIX) && currentInstalled ->
                        current?.let { versions[it] = line.substring(VERSION_PREFIX.length).trim() }
                    line.isEmpty() -> {
                        current = null
                        currentInstalled = false
                    }
                }
            }
        }
        return Dpkg(installed, versions)
    }

    /** Versi yt-dlp dari nama folder dist-info di venv (pip menormalkan, mis. 2026.8.19). */
    private fun ytdlpVersion(root: File): String? = try {
        val libs = File(root, YTDLP_VENV_LIB).listFiles { f -> f.isDirectory && f.name.startsWith("python3") }
        libs?.asSequence()
            ?.mapNotNull { lib ->
                File(lib, "site-packages")
                    .listFiles { f -> f.name.startsWith(YTDLP_DIST_PREFIX) && f.name.endsWith(DIST_INFO_SUFFIX) }
                    ?.firstOrNull()
            }
            ?.firstOrNull()
            ?.name
            ?.removePrefix(YTDLP_DIST_PREFIX)
            ?.removeSuffix(DIST_INFO_SUFFIX)
    } catch (e: SecurityException) {
        null
    }

    /** Versi Node.js dari makro NODE_*_VERSION di header rilis resmi; null bila tidak lengkap. */
    private fun nodeVersion(root: File): String? {
        val header = File(root, NODE_VERSION_HEADER)
        if (!header.isFile) return null
        val parts = HashMap<String, String>()
        try {
            header.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    NODE_VERSION_LINE.find(line)?.let { parts[it.groupValues[1]] = it.groupValues[2] }
                    if (parts.size == 3) break
                }
            }
        } catch (e: IOException) {
            return null
        }
        val major = parts["MAJOR"] ?: return null
        val minor = parts["MINOR"] ?: return null
        val patch = parts["PATCH"] ?: return null
        return "v$major.$minor.$patch"
    }
}
