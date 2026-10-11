package com.molina.suite.feature.settings.tools

import androidx.annotation.StringRes
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.ShellLine

/**
 * Pilih H.264 + AAC bila ada dan gabungkan ke MP4, supaya hasil bisa diputar di pemutar bawaan HP.
 * Berkas tunggal berformat webm (VP9) tidak selalu tampil gambarnya, jadi dipindah ke MKV tanpa
 * mengubah isinya.
 */
private const val COMPAT_ARGS =
    "-S 'vcodec:h264,res,acodec:m4a' --merge-output-format mp4 --remux-video 'webm>mkv'"

/** Pilihan format pada kartu Quick Downloader. [args] adalah argumen yt-dlp. */
internal enum class DownloadFormat(@StringRes val label: Int, val args: String) {
    MP4_1080(R.string.tools_format_mp4_1080, "-f 'bv*[height<=1080]+ba/b[height<=1080]/bv*+ba/b' $COMPAT_ARGS"),
    MP4_720(R.string.tools_format_mp4_720, "-f 'bv*[height<=720]+ba/b[height<=720]/bv*+ba/b' $COMPAT_ARGS"),
    BEST(R.string.tools_format_best, COMPAT_ARGS),
    MP3(R.string.tools_format_mp3, "-x --audio-format mp3")
}

/** Mesin unduh. [extraPackages] adalah id paket menu Paket yang dibutuhkan selain yt-dlp dan ffmpeg. */
internal enum class DownloadEngine(
    @StringRes val label: Int,
    val args: String,
    val extraPackages: List<String>
) {
    YTDLP(R.string.tools_engine_ytdlp, "", emptyList()),
    ARIA2(
        R.string.tools_engine_aria2,
        "--downloader aria2c --downloader-args 'aria2c:-x 16 -s 16 -k 1M'",
        listOf("aria2")
    )
}

/** Menyusun perintah yt-dlp (satu baris, dijalankan di dalam Debian) dari pilihan pengguna. */
internal object DownloaderPlan {

    private val urlPattern = Regex("""^https?://\S+$""")

    private val downloadsDir =
        "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}"

    /** Berkas cookies opsional di folder bersama untuk konten yang butuh login. Path tanpa spasi. */
    private val cookiesPath = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/cookies.txt"

    /** Dievaluasi shell saat perintah jalan: kosong bila berkas cookies tidak ada. */
    private val cookiesFlag = "$(test -f $cookiesPath && echo --cookies $cookiesPath)"

    private const val NODE_BIN = "/opt/node/bin/node"

    /** Runtime JavaScript untuk YouTube; dipasang hanya bila Node.js dari menu Paket sudah ada. */
    private val jsRuntimeFlag = "$(test -x $NODE_BIN && echo --js-runtimes node:$NODE_BIN)"

    /** Tautan yang sudah dirapikan, atau null bila bukan http(s) satu baris tanpa spasi. */
    fun normalizeUrl(raw: String): String? {
        val text = raw.trim()
        return if (urlPattern.matches(text)) text else null
    }

    /** Id paket menu Paket yang harus terpasang. ffmpeg dipakai untuk menggabung dan mengonversi. */
    fun requiredPackages(engine: DownloadEngine): List<String> =
        listOf("ytdlp", "ffmpeg", "node") + engine.extraPackages

    fun command(url: String, format: DownloadFormat, engine: DownloadEngine): String =
        listOf(
            "yt-dlp",
            "--no-playlist",
            "-P",
            ShellLine.quote(downloadsDir),
            format.args,
            engine.args,
            jsRuntimeFlag,
            cookiesFlag,
            ShellLine.quote(url)
        ).filter { it.isNotEmpty() }.joinToString(" ")
}
