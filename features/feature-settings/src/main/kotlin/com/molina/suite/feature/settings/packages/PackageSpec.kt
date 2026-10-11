package com.molina.suite.feature.settings.packages

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

private const val YTDLP_PIP_UPGRADE = "/opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]'"
private const val YTDLP_VENV_SETUP =
    "python3 -m venv /opt/ytdlp && $YTDLP_PIP_UPGRADE && ln -sf /opt/ytdlp/bin/yt-dlp /usr/local/bin/yt-dlp"

internal enum class PackageKind { APT, YTDLP_VENV }

/**
 * Satu baris pada menu Paket. Untuk [PackageKind.APT] semua [aptPackages] harus terpasang;
 * untuk [PackageKind.YTDLP_VENV] [aptPackages] hanyalah prasyarat pembuatan venv.
 */
internal data class PackageSpec(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val kind: PackageKind,
    val aptPackages: List<String>
) {
    private val aptList: String get() = aptPackages.joinToString(" ")

    /** Perintah yang dijalankan di dalam Debian (satu baris). */
    fun installCommand(): String = when (kind) {
        PackageKind.APT -> "apt update && apt install -y $aptList"
        PackageKind.YTDLP_VENV -> "apt update && apt install -y $aptList && $YTDLP_VENV_SETUP"
    }

    fun updateCommand(): String = when (kind) {
        PackageKind.APT -> "apt update && apt install -y --only-upgrade $aptList"
        PackageKind.YTDLP_VENV -> YTDLP_PIP_UPGRADE
    }
}

internal object PackageCatalog {

    val all: List<PackageSpec> = listOf(
        PackageSpec(
            "python", R.string.pkg_python_title, R.string.pkg_python_desc,
            PackageKind.APT, listOf("python3", "python3-pip", "python3-venv")
        ),
        PackageSpec(
            "ytdlp", R.string.pkg_ytdlp_title, R.string.pkg_ytdlp_desc,
            PackageKind.YTDLP_VENV, listOf("python3", "python3-venv")
        ),
        PackageSpec(
            "ffmpeg", R.string.pkg_ffmpeg_title, R.string.pkg_ffmpeg_desc,
            PackageKind.APT, listOf("ffmpeg")
        ),
        PackageSpec(
            "aria2", R.string.pkg_aria2_title, R.string.pkg_aria2_desc,
            PackageKind.APT, listOf("aria2")
        ),
        PackageSpec(
            "jdk17", R.string.pkg_jdk_title, R.string.pkg_jdk_desc,
            PackageKind.APT, listOf("openjdk-17-jdk-headless")
        ),
        PackageSpec(
            "tools", R.string.pkg_tools_title, R.string.pkg_tools_desc,
            PackageKind.APT,
            listOf("git", "curl", "wget", "unzip", "p7zip-full", "nano", "rsync", "jq", "mediainfo")
        )
    )

    /** Satu perintah untuk semua paket yang belum terpasang; null bila tidak ada. */
    fun installAllCommand(missing: List<PackageSpec>): String? {
        if (missing.isEmpty()) return null
        val apt = LinkedHashSet<String>()
        var needsVenv = false
        for (spec in missing) {
            apt.addAll(spec.aptPackages)
            if (spec.kind == PackageKind.YTDLP_VENV) needsVenv = true
        }
        val base = "apt update && apt install -y ${apt.joinToString(" ")}"
        return if (needsVenv) "$base && $YTDLP_VENV_SETUP" else base
    }
}
