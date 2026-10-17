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

/** Tanda dolar untuk perintah shell di dalam string Kotlin, supaya "$V" tidak dibaca sebagai templat. */
private const val DOLLAR = "$"

private const val KOTLIN_RELEASES = "https://github.com/JetBrains/kotlin/releases"

/**
 * Kompilator Kotlin resmi dari rilis stabil terbaru JetBrains/kotlin di GitHub ke /opt/kotlinc,
 * diverifikasi dengan berkas .sha256 dari rilis yang sama. Versi terbaru dibaca dari pengalihan
 * /releases/latest dan harus berbentuk X.Y.Z, jadi rilis uji coba ditolak. Unduhan dan verifikasi
 * selesai dulu sebelum /opt/kotlinc lama dihapus. Dijalankan di subshell supaya direktori kerja
 * perintah berikutnya tidak berubah.
 */
private const val KOTLIN_COMPILER_SETUP =
    """(cd /tmp && V=${DOLLAR}(curl -fsSLI -o /dev/null -w '%{url_effective}' $KOTLIN_RELEASES/latest | sed 's|.*/tag/v||') && """ +
        """echo "${DOLLAR}V" | grep -Eq '^[0-9]+[.][0-9]+[.][0-9]+${DOLLAR}' && """ +
        """curl -fsSL -o kotlin.sha $KOTLIN_RELEASES/download/v${DOLLAR}V/kotlin-compiler-${DOLLAR}V.zip.sha256 && """ +
        """curl -fsSL -o kotlin.zip $KOTLIN_RELEASES/download/v${DOLLAR}V/kotlin-compiler-${DOLLAR}V.zip && """ +
        """awk '{print $1"  kotlin.zip"}' kotlin.sha | sha256sum -c - && """ +
        """rm -rf /tmp/kotlinc && unzip -q kotlin.zip -d /tmp && rm -rf /opt/kotlinc && mv /tmp/kotlinc /opt/kotlinc && """ +
        """ln -sf /opt/kotlinc/bin/kotlinc /usr/local/bin/kotlinc && ln -sf /opt/kotlinc/bin/kotlin /usr/local/bin/kotlin && """ +
        """rm -f kotlin.zip kotlin.sha)"""

private const val PANDOC_RELEASES = "https://github.com/jgm/pandoc/releases"
private const val TYPST_RELEASES = "https://github.com/typst/typst/releases"

/**
 * Pandoc resmi (rilis linux-arm64 dari jgm/pandoc) ke /opt/pandoc. Versi terbaru dibaca dari
 * pengalihan /releases/latest dan harus berbentuk angka bertitik (3.12.1). Rilis pandoc tidak
 * menyertakan berkas checksum, jadi keutuhan dijaga lewat HTTPS dan binary baru harus bisa
 * menjalankan --version sebelum /opt/pandoc lama diganti. Versi ditulis ke .molina-version karena
 * tarball tidak memuat berkas versi. Dijalankan di subshell supaya direktori kerja tidak berubah.
 */
private const val PANDOC_RUNTIME_SETUP =
    """(cd /tmp && V=${DOLLAR}(curl -fsSLI -o /dev/null -w '%{url_effective}' $PANDOC_RELEASES/latest | sed 's|.*/tag/||') && """ +
        """echo "${DOLLAR}V" | grep -Eq '^[0-9]+([.][0-9]+)+${DOLLAR}' && """ +
        """rm -rf pandoc.new pandoc.tgz && """ +
        """curl -fsSL -o pandoc.tgz $PANDOC_RELEASES/download/${DOLLAR}V/pandoc-${DOLLAR}V-linux-arm64.tar.gz && """ +
        """mkdir pandoc.new && tar -xzf pandoc.tgz -C pandoc.new --strip-components=1 && """ +
        """pandoc.new/bin/pandoc --version > /dev/null && echo "${DOLLAR}V" > pandoc.new/.molina-version && """ +
        """rm -rf /opt/pandoc && mv pandoc.new /opt/pandoc && """ +
        """ln -sf /opt/pandoc/bin/pandoc /usr/local/bin/pandoc && rm -f pandoc.tgz)"""

/**
 * Typst resmi (rilis aarch64-unknown-linux-musl dari typst/typst, binary statis) ke /opt/typst,
 * dipakai pandoc sebagai mesin PDF tanpa LaTeX. Tag harus berbentuk vX.Y.Z; rilis tidak
 * menyertakan berkas checksum, jadi langkah keamanannya sama dengan Pandoc. Versi tanpa awalan v
 * ditulis ke .molina-version.
 */
private const val TYPST_RUNTIME_SETUP =
    """(cd /tmp && V=${DOLLAR}(curl -fsSLI -o /dev/null -w '%{url_effective}' $TYPST_RELEASES/latest | sed 's|.*/tag/||') && """ +
        """echo "${DOLLAR}V" | grep -Eq '^v[0-9]+[.][0-9]+[.][0-9]+${DOLLAR}' && """ +
        """rm -rf typst.new typst.txz && """ +
        """curl -fsSL -o typst.txz $TYPST_RELEASES/download/${DOLLAR}V/typst-aarch64-unknown-linux-musl.tar.xz && """ +
        """mkdir typst.new && tar -xJf typst.txz -C typst.new --strip-components=1 && """ +
        """typst.new/typst --version > /dev/null && echo "${DOLLAR}V" | sed 's/^v//' > typst.new/.molina-version && """ +
        """rm -rf /opt/typst && mv typst.new /opt/typst && """ +
        """ln -sf /opt/typst/typst /usr/local/bin/typst && rm -f typst.txz)"""

/** Berkas tambahan yang ikut dihapus saat alat apt dibuang, di luar yang dibersihkan apt. */
private val CLEAN_PATHS: Map<String, List<String>> = mapOf(
    "python" to listOf("/root/.cache/pip"),
    "jdk21" to listOf("/root/.java"),
    "aria2" to listOf("/root/.aria2", "/root/.cache/aria2"),
    "git" to listOf("/root/.gitconfig", "/root/.git-credentials", "/root/.config/git")
)

internal enum class PackageKind { APT, YTDLP_VENV, NODE_RUNTIME, KOTLIN_COMPILER, PANDOC_RUNTIME, TYPST_RUNTIME }

/**
 * Satu baris pada menu Paket. Untuk [PackageKind.APT] semua [aptPackages] harus terpasang;
 * untuk [PackageKind.YTDLP_VENV] [aptPackages] hanyalah prasyarat pembuatan venv.
 */
internal data class PackageSpec(
    val id: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val kind: PackageKind,
    val aptPackages: List<String>,
    /** True bila tidak ikut "Install semua": alat besar yang dipasang manual satu per satu. */
    val optional: Boolean = false
) {
    private val aptList: String get() = aptPackages.joinToString(" ")

    /** Langkah setelah paket apt terpasang; null untuk paket apt murni. */
    val setupCommand: String? get() = when (kind) {
        PackageKind.APT -> null
        PackageKind.YTDLP_VENV -> YTDLP_VENV_SETUP
        PackageKind.NODE_RUNTIME -> NODE_RUNTIME_SETUP
        PackageKind.KOTLIN_COMPILER -> KOTLIN_COMPILER_SETUP
        PackageKind.PANDOC_RUNTIME -> PANDOC_RUNTIME_SETUP
        PackageKind.TYPST_RUNTIME -> TYPST_RUNTIME_SETUP
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
        PackageKind.KOTLIN_COMPILER -> KOTLIN_COMPILER_SETUP
        PackageKind.PANDOC_RUNTIME -> PANDOC_RUNTIME_SETUP
        PackageKind.TYPST_RUNTIME -> TYPST_RUNTIME_SETUP
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
            PackageKind.KOTLIN_COMPILER ->
                steps.add("rm -rf /opt/kotlinc /usr/local/bin/kotlinc /usr/local/bin/kotlin /root/.kotlin")
            PackageKind.PANDOC_RUNTIME ->
                steps.add("rm -rf /opt/pandoc /usr/local/bin/pandoc /root/.pandoc /root/.local/share/pandoc")
            PackageKind.TYPST_RUNTIME ->
                steps.add("rm -rf /opt/typst /usr/local/bin/typst /root/.cache/typst /root/.local/share/typst")
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
            "jdk21", R.string.pkg_jdk_title, R.string.pkg_jdk_desc,
            PackageKind.APT, listOf("openjdk-21-jdk-headless")
        ),
        PackageSpec(
            "kotlin", R.string.pkg_kotlin_title, R.string.pkg_kotlin_desc,
            PackageKind.KOTLIN_COMPILER,
            listOf("openjdk-21-jdk-headless", "curl", "ca-certificates", "unzip"),
            optional = true
        ),
        PackageSpec(
            "gcc", R.string.pkg_gcc_title, R.string.pkg_gcc_desc,
            PackageKind.APT, listOf("gcc", "g++", "make"), optional = true
        ),
        PackageSpec(
            "go", R.string.pkg_go_title, R.string.pkg_go_desc,
            PackageKind.APT, listOf("golang-go"), optional = true
        ),
        PackageSpec(
            "rust", R.string.pkg_rust_title, R.string.pkg_rust_desc,
            PackageKind.APT, listOf("rustc", "cargo"), optional = true
        ),
        PackageSpec(
            "php", R.string.pkg_php_title, R.string.pkg_php_desc,
            PackageKind.APT, listOf("php-cli"), optional = true
        ),
        PackageSpec(
            "ruby", R.string.pkg_ruby_title, R.string.pkg_ruby_desc,
            PackageKind.APT, listOf("ruby"), optional = true
        ),
        PackageSpec(
            "lua", R.string.pkg_lua_title, R.string.pkg_lua_desc,
            PackageKind.APT, listOf("lua5.4"), optional = true
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
            "poppler", R.string.pkg_poppler_title, R.string.pkg_poppler_desc,
            PackageKind.APT, listOf("poppler-utils")
        ),
        PackageSpec(
            "ocr", R.string.pkg_ocr_title, R.string.pkg_ocr_desc,
            PackageKind.APT, listOf("tesseract-ocr", "tesseract-ocr-ind", "tesseract-ocr-eng"), optional = true
        ),
        PackageSpec(
            "pandoc", R.string.pkg_pandoc_title, R.string.pkg_pandoc_desc,
            PackageKind.PANDOC_RUNTIME, listOf("curl", "ca-certificates"), optional = true
        ),
        PackageSpec(
            "typst", R.string.pkg_typst_title, R.string.pkg_typst_desc,
            PackageKind.TYPST_RUNTIME, listOf("curl", "ca-certificates", "xz-utils"), optional = true
        ),
        PackageSpec(
            "x11desktop", R.string.pkg_x11desktop_title, R.string.pkg_x11desktop_desc,
            PackageKind.APT,
            listOf("openbox", "xterm", "pcmanfm", "dbus-x11", "x11-xserver-utils", "xkb-data", "python3-xdg", "xclip", "fonts-dejavu-core"),
            optional = true
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
