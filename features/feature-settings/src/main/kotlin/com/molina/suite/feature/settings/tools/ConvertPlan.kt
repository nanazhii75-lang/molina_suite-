package com.molina.suite.feature.settings.tools

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.ShellLine

/** Format hasil konverter media. [extension] dipakai untuk nama file keluaran. */
internal enum class ConvertTarget(@StringRes val label: Int, val extension: String, val isVideo: Boolean) {
    MP3(R.string.tools_target_mp3, "mp3", false),
    MP4(R.string.tools_target_mp4, "mp4", true),
    MKV(R.string.tools_target_mkv, "mkv", true),
    WEBM(R.string.tools_target_webm, "webm", true),
    M4A(R.string.tools_target_m4a, "m4a", false),
    OPUS(R.string.tools_target_opus, "opus", false)
}

/** Cara memproses video: menyalin stream apa adanya, atau menyandikan ulang. */
internal enum class ConvertMode(@StringRes val label: Int) {
    COPY(R.string.tools_mode_copy),
    REENCODE(R.string.tools_mode_reencode)
}

/** Menyusun satu baris perintah ffmpeg; path di-quote untuk shell. */
internal object ConvertPlan {

    private const val H264 = "-c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p"
    private const val VP9 = "-c:v libvpx-vp9 -crf 32 -b:v 0 -deadline good -cpu-used 4 -row-mt 1"
    private const val STREAMS = "-map 0:v:0? -map 0:a?"

    fun command(input: String, output: String, target: ConvertTarget, mode: ConvertMode): String =
        "ffmpeg -hide_banner -n -i ${ShellLine.quote(input)} ${args(target, mode)} ${ShellLine.quote(output)}"

    private fun args(target: ConvertTarget, mode: ConvertMode): String = when (target) {
        ConvertTarget.MP3 -> "-vn -c:a libmp3lame -b:a 320k"
        ConvertTarget.M4A -> "-vn -c:a aac -b:a 256k -movflags +faststart"
        ConvertTarget.OPUS -> "-vn -c:a libopus -b:a 160k"
        ConvertTarget.MP4 -> when (mode) {
            ConvertMode.COPY -> "$STREAMS -sn -c copy -movflags +faststart"
            ConvertMode.REENCODE -> "$STREAMS -sn $H264 -c:a aac -b:a 192k -movflags +faststart"
        }
        ConvertTarget.MKV -> when (mode) {
            ConvertMode.COPY -> "$STREAMS -map 0:s? -c copy"
            ConvertMode.REENCODE -> "$STREAMS -map 0:s? $H264 -c:a aac -b:a 192k -c:s copy"
        }
        ConvertTarget.WEBM -> when (mode) {
            ConvertMode.COPY -> "$STREAMS -sn -c copy"
            ConvertMode.REENCODE -> "$STREAMS -sn $VP9 -c:a libopus -b:a 128k"
        }
    }
}
