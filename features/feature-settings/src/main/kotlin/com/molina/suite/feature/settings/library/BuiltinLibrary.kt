package com.molina.suite.feature.settings.library

import com.molina.suite.core.storage.MolinaStorage

/** Entri Library bawaan. Id tetap agar "Pulihkan" tidak menggandakan entri. */
internal object BuiltinLibrary {

    private val downloads = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}"
    private const val MPV_PACKAGE = "com.molina.suite"

    val entries: List<LibraryEntry> = listOf(
        entry(
            "builtin-ytdlp-version", "Versi yt-dlp",
            "yt-dlp --version",
            "Memeriksa versi yt-dlp di Debian.", RunMode.DEBIAN
        ),
        entry(
            "builtin-ytdlp-video", "Unduh video (yt-dlp)",
            "yt-dlp -P $downloads {URL}",
            "Mengunduh video ke folder Downloads. TikTok butuh curl_cffi.", RunMode.DEBIAN
        ),
        entry(
            "builtin-ytdlp-mp3", "Unduh audio MP3 (yt-dlp)",
            "yt-dlp -x --audio-format mp3 -P $downloads {URL}",
            "Hanya audio, dikonversi ke MP3 (butuh ffmpeg).", RunMode.DEBIAN
        ),
        entry(
            "builtin-ytdlp-playlist", "Unduh playlist (yt-dlp)",
            "yt-dlp -P $downloads -o '%(playlist)s/%(playlist_index)s - %(title)s.%(ext)s' {URL}",
            "Satu folder per playlist, file diberi nomor urut.", RunMode.DEBIAN
        ),
        entry(
            "builtin-ytdlp-update", "Update yt-dlp",
            "if [ -x /opt/ytdlp/bin/pip ]; then /opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]'; " +
                "else yt-dlp -U; fi",
            "Memakai pip bila yt-dlp dipasang di /opt/ytdlp, kalau tidak update mandiri.", RunMode.DEBIAN
        ),
        entry(
            "builtin-aria2-download", "Unduh cepat (aria2c)",
            "aria2c -d $downloads -x 8 -s 8 {URL}",
            "Unduh langsung dengan 8 koneksi paralel.", RunMode.DEBIAN
        ),
        entry(
            "builtin-ffprobe-info", "Info media (ffprobe)",
            "ffprobe -hide_banner {FILE}",
            "Menampilkan codec, durasi, dan resolusi.", RunMode.DEBIAN
        ),
        entry(
            "builtin-ffmpeg-mp3", "Konversi ke MP3 (ffmpeg)",
            "ffmpeg -hide_banner -i {FILE} -vn -codec:a libmp3lame -q:a 2 {OUT}",
            "Isi path hasil berakhiran .mp3. ffmpeg bertanya bila berkas sudah ada.", RunMode.DEBIAN
        ),
        entry(
            "builtin-mpv-url", "Putar URL di mpv",
            "am start -a android.intent.action.VIEW -d {URL} -t 'video/*' -p $MPV_PACKAGE",
            "Membuka tautan video atau audio langsung di tab mpv.", RunMode.HOST
        ),
        entry(
            "builtin-mpv-file", "Putar file di mpv",
            "am start -a android.intent.action.VIEW -d file://{FILE} -t 'video/*' -p $MPV_PACKAGE",
            "Isi path file di penyimpanan, mis. $downloads/video.mp4.", RunMode.HOST
        ),
        entry(
            "builtin-list-downloads", "Isi folder Downloads",
            "ls -lh $downloads",
            "Daftar file hasil unduhan.", RunMode.DEBIAN
        ),
        entry(
            "builtin-disk-free", "Sisa ruang penyimpanan",
            "df -h /sdcard",
            "Ruang bebas di penyimpanan bersama.", RunMode.DEBIAN
        ),
        entry(
            "builtin-tool-versions", "Versi semua alat",
            "python3 --version; ffmpeg -version | head -n 1; aria2c --version | head -n 1; " +
                "yt-dlp --version; java -version 2>&1 | head -n 1",
            "Alat yang belum terpasang menampilkan pesan not found.", RunMode.DEBIAN
        ),
        entry(
            "builtin-apt-clean", "Bersihkan cache apt",
            "apt-get clean && apt-get autoremove -y",
            "Membebaskan ruang dari paket yang tidak terpakai.", RunMode.DEBIAN
        )
    )

    private fun entry(id: String, name: String, content: String, note: String, runIn: RunMode) =
        LibraryEntry(id = id, name = name, content = content, note = note, runIn = runIn, builtin = true)
}
