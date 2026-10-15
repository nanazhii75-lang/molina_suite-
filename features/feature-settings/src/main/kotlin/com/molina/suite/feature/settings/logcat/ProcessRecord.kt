package com.molina.suite.feature.settings.logcat

/**
 * Keadaan satu proses aplikasi yang disimpan ke disk supaya proses berikutnya tahu bagaimana
 * pendahulunya berakhir: [pid], waktu mulai ([startedAt], milidetik epoch), apakah sedang tampil
 * di depan layar ([foreground]), apakah sudah mencatat crash Java/Kotlin ([javaCrash]), dan
 * apakah proses sengaja ditutup lewat tombol Exit ([cleanExit]).
 */
internal data class ProcessRecord(
    val pid: Int,
    val startedAt: Long,
    val foreground: Boolean,
    val javaCrash: Boolean,
    val cleanExit: Boolean = false
) {
    /** Bentuk teks satu baris, mis. "pid=123;start=1760000000000;fg=1;crash=0;exit=0". */
    fun encode(): String =
        "pid=$pid;start=$startedAt;fg=${flag(foreground)};crash=${flag(javaCrash)};exit=${flag(cleanExit)}"

    companion object {
        private const val ON = "1"
        private const val OFF = "0"

        private fun flag(value: Boolean): String = if (value) ON else OFF

        /** Catatan dari teks hasil [encode]; null bila teksnya rusak atau tidak lengkap. */
        fun decode(text: String): ProcessRecord? {
            val fields = HashMap<String, String>()
            for (part in text.trim().split(';')) {
                val at = part.indexOf('=')
                if (at > 0) fields[part.substring(0, at)] = part.substring(at + 1)
            }
            val pid = fields["pid"]?.toIntOrNull() ?: return null
            val startedAt = fields["start"]?.toLongOrNull() ?: return null
            return ProcessRecord(pid, startedAt, fields["fg"] == ON, fields["crash"] == ON, fields["exit"] == ON)
        }
    }
}
