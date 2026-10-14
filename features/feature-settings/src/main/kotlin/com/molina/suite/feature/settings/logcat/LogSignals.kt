package com.molina.suite.feature.settings.logcat

/**
 * Pengenal sumber dan tingkat sebuah baris logcat mentah (baris yang bukan aktivitas molina).
 * Dari tag dan isi pesannya dipastikan baris itu milik mpv, Terminal, atau lainnya, dan seberapa
 * parah baris itu sebenarnya, supaya kategori dan hitungan masalah di layar Logcat menunjuk
 * bagian yang benar-benar bermasalah. Murni data tanpa Android, jadi bisa diuji di JVM.
 *
 * Tag yang belum dikenal sengaja tetap di [LogCategory.OTHER] agar kelihatan dan bisa ditambahkan.
 */
internal object LogSignals {

    private const val MPV_TAG = "mpv"
    private const val RUNTIME_TAG = "AndroidRuntime"
    private const val SYSTEM_TAG = "System"
    private const val MEDIA_EXTRACTOR_TAG = "MediaExtractor"
    private const val TERMINAL_TAG_PREFIX = "termux"
    private const val CHATTY_TAG = "chatty"

    /** Tag milik mesin pemutar: libmpv sendiri dan lapisan media Android yang dipakainya. */
    private val MPV_TAGS = setOf(
        MPV_TAG,
        MEDIA_EXTRACTOR_TAG,
        "OMXClient",
        "ExtendedACodec",
        "ACodec",
        "MediaCodec",
        "MediaSessionCompat",
        "AudioTrack"
    )

    /** Tag proses yang dijalankan dari Terminal (proot, perkakas shell, dan alat di Debian). */
    private val TERMINAL_TAGS = setOf(
        "proot",
        "lscpu",
        "find",
        "cat",
        "bash",
        "tmux",
        "git",
        "python3",
        "aria2c",
        "ffmpeg",
        "/system/bin/app_process"
    )

    /** Penanda tingkat di awal pesan mpv, mis. "[ffmpeg:error] ..." atau "[cplayer:fatal] ...". */
    private val MPV_LEVEL_MARK = Regex("""^\[[^\]\s]*:(fatal|error|warn)\]""")

    /** Pesan ringkasan logd, mis. "uid=10497(u0_a497) proot identical 2 lines"; kelompok 1 nama prosesnya. */
    private val CHATTY_PROCESS = Regex("""^uid=\d+\([^)]*\) (\S+) identical \d+ lines?""")

    private const val MPV_PROPERTY_PREFIX = "mpv_get_property("
    private const val MPV_PROPERTY_UNAVAILABLE = "property unavailable"
    private const val TRACE_CONNECT_PREFIX = "Trace connect"
    private const val BOOT_CLASSPATH_MARK = "boot classpath entry"
    private const val BOOT_CLASSPATH_FILE = "ifaamanager.jar"
    private const val FILE_NOT_FOUND_PREFIX = "java.io.FileNotFoundException"
    private const val STACK_FRAME_PREFIX = "at "
    private const val HELPER_START_PREFIX = ">>>>>> START com.android.internal.os.RuntimeInit"
    private const val HELPER_MAIN_PREFIX = "Calling main entry "
    private const val HELPER_EXIT_PREFIX = "VM exiting with result code 0"

    /** Kategori tampilan untuk [entry]; [LogCategory.OTHER] bila sumbernya belum dikenal. */
    fun category(entry: LogcatEntry): LogCategory = when {
        entry.tag == CHATTY_TAG -> chattyCategory(entry)
        entry.tag in MPV_TAGS -> LogCategory.MPV
        entry.tag in TERMINAL_TAGS -> LogCategory.TERMINAL
        entry.tag.startsWith(TERMINAL_TAG_PREFIX, ignoreCase = true) -> LogCategory.TERMINAL
        isHelperRuntime(entry) || isBootClasspathNoise(entry) -> LogCategory.TERMINAL
        else -> LogCategory.OTHER
    }

    /**
     * True bila [entry] berasal dari proses anak aplikasi (proot, perkakas shell, proses pembantu),
     * bukan dari proses utama. Pemilik sebenarnya proses anak tidak bisa dibaca dari tag: yang
     * menjalankannya bisa mpv (yt-dlp), Terminal, atau Setting. Tampilan menentukannya dari
     * aktivitas yang sedang berjalan; tanpa aktivitas, baris itu dianggap milik Terminal.
     */
    fun isSubprocess(entry: LogcatEntry): Boolean {
        if (entry.tag == CHATTY_TAG) return chattyProcess(entry) in TERMINAL_TAGS
        return entry.tag in TERMINAL_TAGS || isHelperRuntime(entry) || isBootClasspathNoise(entry)
    }

    /** Nama proses yang barisnya diringkas logd, atau null bila [entry] bukan ringkasan logd. */
    private fun chattyProcess(entry: LogcatEntry): String? =
        CHATTY_PROCESS.find(entry.message)?.groupValues?.get(1)

    /** Ringkasan logd mengikuti kategori proses yang barisnya diringkas; proses tak dikenal masuk Lainnya. */
    private fun chattyCategory(entry: LogcatEntry): LogCategory {
        val process = chattyProcess(entry) ?: return LogCategory.OTHER
        return category(entry.copy(tag = process, message = ""))
    }

    /**
     * Tingkat sebenarnya dari [entry]. Pesan mpv bertanda error/fatal/warn dinaikkan karena
     * libmpv mencetak semuanya sebagai Verbose; gangguan perangkat yang sudah dikenal dan tidak
     * berbahaya ([isKnownNoise]) diturunkan paling tinggi ke Warn agar tidak dihitung masalah.
     */
    fun severity(entry: LogcatEntry): LogcatLevel {
        if (isKnownNoise(entry)) {
            return if (entry.level > LogcatLevel.WARN) LogcatLevel.WARN else entry.level
        }
        if (entry.tag != MPV_TAG) return entry.level
        val mark = MPV_LEVEL_MARK.find(entry.message) ?: return entry.level
        val marked = if (mark.groupValues[1] == "warn") LogcatLevel.WARN else LogcatLevel.ERROR
        return if (marked > entry.level) marked else entry.level
    }

    /** True bila [entry] adalah gangguan perangkat atau pustaka yang sudah dikenal dan tidak berbahaya. */
    fun isKnownNoise(entry: LogcatEntry): Boolean = when (entry.tag) {
        MPV_TAG ->
            entry.message.startsWith(MPV_PROPERTY_PREFIX) && entry.message.contains(MPV_PROPERTY_UNAVAILABLE)
        MEDIA_EXTRACTOR_TAG -> entry.message.startsWith(TRACE_CONNECT_PREFIX)
        SYSTEM_TAG -> isBootClasspathNoise(entry)
        else -> false
    }

    /**
     * ROM yang tidak punya berkas boot classpath tertentu mencetak galat dan stack trace di tag
     * System setiap kali proses pembantu (mis. perintah am) dimulai; prosesnya tetap jalan normal.
     */
    private fun isBootClasspathNoise(entry: LogcatEntry): Boolean {
        if (entry.tag != SYSTEM_TAG || entry.level != LogcatLevel.ERROR) return false
        val text = entry.message.trimStart()
        return text.contains(BOOT_CLASSPATH_MARK) ||
            text.contains(BOOT_CLASSPATH_FILE) ||
            text.startsWith(FILE_NOT_FOUND_PREFIX) ||
            text.startsWith(STACK_FRAME_PREFIX)
    }

    /** Baris AndroidRuntime milik proses pembantu Terminal yang berjalan sebentar lalu keluar normal. */
    private fun isHelperRuntime(entry: LogcatEntry): Boolean {
        if (entry.tag != RUNTIME_TAG) return false
        val text = entry.message
        return text.startsWith(HELPER_START_PREFIX) ||
            text.startsWith(HELPER_MAIN_PREFIX) ||
            text.startsWith(HELPER_EXIT_PREFIX)
    }
}
