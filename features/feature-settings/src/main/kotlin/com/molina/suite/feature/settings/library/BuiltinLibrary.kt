package com.molina.suite.feature.settings.library

import com.molina.suite.core.storage.MolinaStorage

/** Entri Library bawaan. Id tetap agar "Pulihkan" tidak menggandakan entri. */
internal object BuiltinLibrary {

    private val downloads = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}"
    private const val MPV_PACKAGE = "com.molina.suite"
    private const val ENSURE_CURL = "command -v curl >/dev/null 2>&1 || { apt update && apt install -y curl; }; "
    private const val SPEED_URL = "https://speed.cloudflare.com/__down?bytes=50000000"

    /**
     * Satu baris: fungsi v mencetak nama, lalu baris pertama yang tidak kosong dari perintah versi
     * (stderr ikut dibaca karena java menulis di sana), atau "belum terpasang" bila alatnya tidak ada.
     */
    private const val TOOL_VERSIONS_SCRIPT =
        """v() { printf '%-12s' "$1"; shift; if command -v "$1" >/dev/null 2>&1; then "$@" 2>&1 | sed -n '/./{p;q}' | cut -c1-72; else echo 'belum terpasang'; fi; }; """ +
            """v Debian cat /etc/debian_version; v Python python3 --version; v pip pip3 --version; v yt-dlp yt-dlp --version; """ +
            """v Node.js node --version; v FFmpeg ffmpeg -version; v FFprobe ffprobe -version; v aria2 aria2c --version; """ +
            """v Java java -version; v git git --version; v curl curl --version; v wget wget --version; v unzip unzip -v; """ +
            """v jq jq --version; v rsync rsync --version; v nano nano --version; """ +
            """printf '%-12s' mediainfo; command -v mediainfo >/dev/null 2>&1 && mediainfo --Version 2>&1 | sed -n '/v[0-9]/{p;q}' || echo 'belum terpasang'"""

    val entries: List<LibraryEntry> = listOf(
        IptvIndonesia.entry,
        entry(
            "builtin-ytdlp-playlist", "Unduh playlist (yt-dlp)",
            "yt-dlp -P $downloads -o '%(playlist)s/%(playlist_index)s - %(title)s.%(ext)s' {URL}",
            "Satu folder per playlist, file diberi nomor urut.", RunMode.DEBIAN
        ),
        entry(
            "builtin-aria2-download", "Unduh cepat (aria2c)",
            "aria2c -d $downloads -x 8 -s 8 {URL}",
            "Unduh langsung dengan 8 koneksi paralel.", RunMode.DEBIAN
        ),
        entry(
            "builtin-mpv-url", "Putar URL di mpv",
            "am start -a android.intent.action.VIEW -d {URL} -t 'video/*' -p $MPV_PACKAGE",
            "Membuka tautan video atau audio langsung di tab mpv.", RunMode.ANDROID
        ),
        entry(
            "builtin-mpv-file", "Putar file di mpv",
            "am start -a android.intent.action.VIEW -d file://{FILE} -t 'video/*' -p $MPV_PACKAGE",
            "Isi path file di penyimpanan, mis. $downloads/video.mp4.", RunMode.ANDROID
        ),
        entry(
            "builtin-mpv-stream", "Putar stream (IPTV, rtsp, rtmp, udp)",
            "U={URL}; case \"\$U\" in http://*|https://*) " +
                "am start -a android.intent.action.VIEW -d \"\$U\" -t 'video/*' -p $MPV_PACKAGE;; " +
                "*) am start -a android.intent.action.VIEW -d \"\$U\" -p $MPV_PACKAGE;; esac",
            "Tautan http(s) memakai tipe video; rtsp, rtmp, udp, dan sejenisnya tanpa tipe.", RunMode.ANDROID
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
            TOOL_VERSIONS_SCRIPT,
            "Satu klik menampilkan versi semua alat di Debian; yang belum ada tertulis belum terpasang.", RunMode.DEBIAN
        ),
        entry(
            "builtin-apt-clean", "Bersihkan cache apt",
            "apt-get clean && apt-get autoremove -y",
            "Membebaskan ruang dari paket yang tidak terpakai.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-public-ip", "IP publik",
            ENSURE_CURL + "curl -s https://api.ipify.org; echo",
            "Alamat IP yang terlihat dari internet.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-timing", "Waktu respons situs",
            ENSURE_CURL + "curl -s -o /dev/null -w 'DNS %{time_namelookup}s, konek %{time_connect}s, " +
                "total %{time_total}s\\n' https://{HOST}",
            "Isi nama host saja tanpa https://, mis. github.com.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-dns", "Cek DNS",
            "getent hosts {HOST}",
            "Menampilkan alamat IP dari nama host. Kosong berarti DNS gagal.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-headers", "Header HTTP situs",
            ENSURE_CURL + "curl -sIL {URL}",
            "Menampilkan status dan header, mengikuti pengalihan.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-resolv", "DNS yang dipakai Debian",
            "cat /etc/resolv.conf",
            "Daftar server DNS di dalam Debian.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-share", "Bagikan folder Downloads (HTTP)",
            "python3 -m http.server 8080 --directory $downloads",
            "Buka http://IP-HP:8080 dari perangkat lain di Wi-Fi yang sama. Hentikan dengan Ctrl+C.",
            RunMode.DEBIAN
        )
    )

    /**
     * Entri bawaan lama yang fungsinya kini punya menu sendiri. Tidak lagi disemai atau dipulihkan;
     * hanya dipakai BuiltinRetirer untuk menghapus salinan lama yang belum diubah pengguna.
     */
    val retiredEntries: List<LibraryEntry> = listOf(
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
            "builtin-ytdlp-update", "Update yt-dlp",
            "if [ -x /opt/ytdlp/bin/pip ]; then /opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]'; " +
                "else yt-dlp -U; fi",
            "Memakai pip bila yt-dlp dipasang di /opt/ytdlp, kalau tidak update mandiri.", RunMode.DEBIAN
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
            "builtin-install-media", "Install ffmpeg + aria2",
            "apt update && apt install -y ffmpeg aria2",
            "Dibutuhkan untuk konversi, info media, dan unduh cepat. Unduhan cukup besar.", RunMode.DEBIAN
        ),
        entry(
            "builtin-install-ffmpeg", "Install ffmpeg",
            "apt update && apt install -y ffmpeg",
            "ffmpeg dan ffprobe untuk konversi dan info media. Unduhan cukup besar.", RunMode.DEBIAN
        ),
        entry(
            "builtin-install-python", "Install Python",
            "apt update && apt install -y python3 python3-pip python3-venv",
            "Python 3 beserta pip dan venv.", RunMode.DEBIAN
        ),
        entry(
            "builtin-install-jdk17", "Install JDK 17",
            "apt update && apt install -y openjdk-17-jdk-headless",
            "OpenJDK 17 resmi Debian 12, tanpa komponen GUI.", RunMode.DEBIAN
        ),
        entry(
            "builtin-install-tools", "Install alat bantu",
            "apt update && apt install -y git curl wget unzip p7zip-full nano rsync jq mediainfo",
            "git, curl, wget, unzip, p7zip, nano, rsync, jq, dan mediainfo.", RunMode.DEBIAN
        ),
        entry(
            "builtin-install-ytdlp", "Install yt-dlp + curl_cffi",
            "apt update && apt install -y python3 python3-venv && python3 -m venv /opt/ytdlp && " +
                "/opt/ytdlp/bin/pip install -U 'yt-dlp[default,curl-cffi]' && " +
                "ln -sf /opt/ytdlp/bin/yt-dlp /usr/local/bin/yt-dlp",
            "Wajib untuk TikTok. Menggantikan yt-dlp lama di /usr/local/bin.", RunMode.DEBIAN
        ),
        entry(
            "builtin-apt-upgrade", "Update + upgrade Debian",
            "apt update && apt upgrade -y",
            "Memperbarui daftar paket dan semua paket terpasang.", RunMode.DEBIAN
        ),
        entry(
            "builtin-net-speedtest", "Tes kecepatan (speedtest-cli)",
            "command -v speedtest-cli >/dev/null 2>&1 || { apt update && apt install -y speedtest-cli; }; " +
                "speedtest-cli --simple",
            "Ping, unduh, dan unggah. Memakai data beberapa puluh MB. Bila ditolak server, pakai tes curl.",
            RunMode.DEBIAN
        ),
        entry(
            "builtin-net-speed-curl", "Tes kecepatan unduh (curl, 50 MB)",
            ENSURE_CURL + "curl -L -o /dev/null " + SPEED_URL,
            "Mengunduh 50 MB dari Cloudflare; kolom kecepatan rata-rata tampil di tabel curl.",
            RunMode.DEBIAN
        ),
        entry(
            "builtin-net-check", "Cek internet",
            ENSURE_CURL + "curl -s -o /dev/null -w 'HTTP %{http_code} dalam %{time_total} detik\\n' " +
                "https://www.google.com/generate_204",
            "HTTP 204 berarti internet tersambung.", RunMode.DEBIAN
        )
    )

    private fun entry(id: String, name: String, content: String, note: String, runIn: RunMode) =
        LibraryEntry(id = id, name = name, content = content, note = note, runIn = runIn, builtin = true)
}
