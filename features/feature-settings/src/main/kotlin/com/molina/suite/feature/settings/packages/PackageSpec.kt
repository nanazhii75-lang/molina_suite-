package com.molina.suite.feature.settings.packages

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

private const val YTDLP_PIP_UPGRADE = "/opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]'"
private const val YTDLP_VENV_SETUP =
    "python3 -m venv /opt/ytdlp && $YTDLP_PIP_UPGRADE && ln -sf /opt/ytdlp/bin/yt-dlp /usr/local/bin/yt-dlp"

private const val NODE_BASE = "https://nodejs.org/dist/latest-v24.x"

/**
 * Node.js 24 resmi dari nodejs.org ke /opt/node, diverifikasi dengan SHASUMS256.txt dari rilis
 * yang sama. Unduhan dan verifikasi selesai dulu sebelum /opt/node lama dihapus. Dijalankan di
 * subshell supaya direktori kerja perintah berikutnya tidak berubah.
 */
private const val NODE_RUNTIME_SETUP =
    """(cd /tmp && curl -fsSL ${NODE_BASE}/SHASUMS256.txt | grep 'linux-arm64\.tar\.xz$' > node.sum && """ +
        """curl -fsSL -o node.tar.xz ${NODE_BASE}/$(awk '{print $2}' node.sum) && """ +
        """awk '{print $1"  node.tar.xz"}' node.sum | sha256sum -c - && """ +
        """rm -rf /opt/node && mkdir -p /opt/node && """ +
        """tar -xJf node.tar.xz -C /opt/node --strip-components=1 && """ +
        """ln -sf /opt/node/bin/node /usr/local/bin/node && rm -f node.tar.xz node.sum)"""

/** Berkas tambahan yang ikut dihapus saat alat apt dibuang, di luar yang dibersihkan apt. */
private val CLEAN_PATHS: Map<String, List<String>> = mapOf(
    "python" to listOf("/root/.cache/pip"),
    "jdk17" to listOf("/root/.java"),
    "aria2" to listOf("/root/.aria2", "/root/.cache/aria2"),
    "git" to listOf("/root/.gitconfig", "/root/.git-credentials", "/root/.config/git")
)

internal enum class PackageKind { APT, YTDLP_VENV, NODE_RUNTIME }

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

    /** Langkah setelah paket apt terpasang; null untuk paket apt murni. */
    val setupCommand: String? get() = when (kind) {
        PackageKind.APT -> null
        PackageKind.YTDLP_VENV -> YTDLP_VENV_SETUP
        PackageKind.NODE_RUNTIME -> NODE_RUNTIME_SETUP
    }

    /** Perintah yang dijalankan di dalam Debian (satu baris). */
    fun installCommand(): String {
        val apt = "apt update && apt install -y $aptList"
        val setup = setupCommand ?: return apt
        return "$apt && $setup"
    }

    fun updateCommand(): String = when (kind) {
        PackageKind.APT -> "apt update && apt install -y --only-upgrade $aptList"
        PackageKind.YTDLP_VENV -> YTDLP_PIP_UPGRADE
        PackageKind.NODE_RUNTIME -> NODE_RUNTIME_SETUP
    }

    /** Paket apt yang versinya ditampilkan di menu; null untuk alat di luar apt. */
    val versionPackage: String? get() =
        if (kind == PackageKind.APT) aptPackages.firstOrNull() else null

    /** Paket apt yang benar-benar dihapus: [aptPackages] dikurangi yang masih dipakai alat lain. */
    fun purgeTargets(protectedApt: Set<String>): List<String> =
        if (kind == PackageKind.APT) aptPackages.filter { it !in protectedApt } else emptyList()

    /** Paket apt yang dipertahankan karena dipakai alat lain yang masih terpasang. */
    fun keptPackages(protectedApt: Set<String>): List<String> =
        if (kind == PackageKind.APT) aptPackages.filter { it in protectedApt } else emptyList()

    /** False bila seluruh paket alat apt ini masih dipakai alat lain yang terpasang. */
    fun isRemovable(protectedApt: Set<String>): Boolean =
        kind != PackageKind.APT || purgeTargets(protectedApt).isNotEmpty()

    /**
     * Satu baris di dalam Debian yang membuang alat sampai bersih: paket beserta konfigurasi
     * sistemnya (purge), sisa dependensi yang tak terpakai (autoremove), cache apt, dan berkas
     * tambahan alat. Kunci SSH dan /sdcard/Molina tidak disentuh.
     */
    fun removeCommand(protectedApt: Set<String>): String {
        val steps = ArrayList<String>()
        when (kind) {
            PackageKind.APT -> {
                val targets = purgeTargets(protectedApt)
                steps.add("DEBIAN_FRONTEND=noninteractive apt-get purge -y ${targets.joinToString(" ")}")
                steps.add("DEBIAN_FRONTEND=noninteractive apt-get autoremove -y --purge")
                steps.add("apt-get clean")
            }
            PackageKind.YTDLP_VENV ->
                steps.add("rm -rf /opt/ytdlp /usr/local/bin/yt-dlp /root/.cache/yt-dlp /root/.config/yt-dlp")
            PackageKind.NODE_RUNTIME ->
                steps.add("rm -rf /opt/node /usr/local/bin/node /root/.npm")
        }
        CLEAN_PATHS[id]?.let { steps.add("rm -rf " + it.joinToString(" ")) }
        return steps.joinToString(" && ")
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
            "node", R.string.pkg_node_title, R.string.pkg_node_desc,
            PackageKind.NODE_RUNTIME, listOf("curl", "ca-certificates", "xz-utils")
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
            "git", R.string.pkg_git_title, R.string.pkg_git_desc,
            PackageKind.APT, listOf("git")
        ),
        PackageSpec(
            "curl", R.string.pkg_curl_title, R.string.pkg_curl_desc,
            PackageKind.APT, listOf("curl")
        ),
        PackageSpec(
            "wget", R.string.pkg_wget_title, R.string.pkg_wget_desc,
            PackageKind.APT, listOf("wget")
        ),
        PackageSpec(
            "unzip", R.string.pkg_unzip_title, R.string.pkg_unzip_desc,
            PackageKind.APT, listOf("unzip")
        ),
        PackageSpec(
            "p7zip", R.string.pkg_p7zip_title, R.string.pkg_p7zip_desc,
            PackageKind.APT, listOf("p7zip-full")
        ),
        PackageSpec(
            "nano", R.string.pkg_nano_title, R.string.pkg_nano_desc,
            PackageKind.APT, listOf("nano")
        ),
        PackageSpec(
            "rsync", R.string.pkg_rsync_title, R.string.pkg_rsync_desc,
            PackageKind.APT, listOf("rsync")
        ),
        PackageSpec(
            "jq", R.string.pkg_jq_title, R.string.pkg_jq_desc,
            PackageKind.APT, listOf("jq")
        ),
        PackageSpec(
            "mediainfo", R.string.pkg_mediainfo_title, R.string.pkg_mediainfo_desc,
            PackageKind.APT, listOf("mediainfo")
        ),
        PackageSpec(
            "tmux", R.string.pkg_tmux_title, R.string.pkg_tmux_desc,
            PackageKind.APT, listOf("tmux")
        ),
        PackageSpec(
            "ssh", R.string.pkg_ssh_title, R.string.pkg_ssh_desc,
            PackageKind.APT, listOf("openssh-client")
        )
    )

    /** Satu perintah untuk semua paket yang belum terpasang; null bila tidak ada. */
    fun installAllCommand(missing: List<PackageSpec>): String? {
        if (missing.isEmpty()) return null
        val apt = LinkedHashSet<String>()
        val setups = LinkedHashSet<String>()
        for (spec in missing) {
            apt.addAll(spec.aptPackages)
            spec.setupCommand?.let { setups.add(it) }
        }
        val base = "apt update && apt install -y ${apt.joinToString(" ")}"
        return setups.fold(base) { acc, step -> "$acc && $step" }
    }
}
