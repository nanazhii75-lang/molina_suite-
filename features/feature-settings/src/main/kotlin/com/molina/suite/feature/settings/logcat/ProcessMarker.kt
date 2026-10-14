package com.molina.suite.feature.settings.logcat

/** Cara proses sebelumnya berakhir menurut sistem (Android 11 ke atas) atau perkiraan kita. */
internal enum class ExitKind { NORMAL, JAVA_CRASH, NATIVE_CRASH, ANR, LOW_MEMORY, SIGNALED, RESOURCE, OTHER }

/** Sebab berakhirnya proses dari sistem, dengan keterangan tambahan bila ada. */
internal data class ExitCause(val kind: ExitKind, val detail: String? = null)

/**
 * Peristiwa pergantian proses. [abnormal] true bila proses sebelumnya berakhir tidak wajar;
 * [needsReport] true bila perlu laporan sendiri (crash Java sudah punya laporan dari pencatat crash).
 */
internal data class ProcessEvent(
    val level: LogcatLevel,
    val message: String,
    val abnormal: Boolean,
    val needsReport: Boolean
)

/**
 * Menilai bagaimana proses sebelumnya berakhir dan menyusun penanda "proses berhenti, dimulai
 * ulang" untuk daftar Logcat, seperti PROCESS ENDED/STARTED di Android Studio. Penanda hanya
 * dibuat dari fakta yang ada: catatan crash Java, keterangan sistem bila tersedia, dan apakah
 * aplikasi sedang di depan layar saat berhenti. Murni data tanpa Android, jadi bisa diuji di JVM.
 */
internal object ProcessMarker {

    /** Tag baris penanda di daftar Logcat dan riwayat. */
    const val TAG = "MolinaProcess"

    /** Penanda untuk pergantian dari [previous] ke [currentPid]; null bila bukan pergantian proses. */
    fun evaluate(previous: ProcessRecord?, currentPid: Int, cause: ExitCause?): ProcessEvent? {
        if (previous == null || previous.pid == currentPid) return null
        val head = "Proses berhenti, aplikasi dimulai ulang (PID ${previous.pid} \u2192 $currentPid)"
        val detail = cause?.detail?.takeIf { it.isNotBlank() }
        val kind = when {
            previous.javaCrash -> ExitKind.JAVA_CRASH
            cause != null && cause.kind != ExitKind.OTHER -> cause.kind
            else -> null
        }
        return when (kind) {
            ExitKind.JAVA_CRASH -> event(
                head, LogcatLevel.ERROR, "crash Java/Kotlin, lihat Crash terakhir", detail, abnormal = true, report = false
            )
            ExitKind.NATIVE_CRASH -> event(
                head, LogcatLevel.ERROR, "crash native (mis. libmpv atau proot)", detail, abnormal = true, report = true
            )
            ExitKind.ANR -> event(
                head, LogcatLevel.ERROR, "tidak merespons (ANR)", detail, abnormal = true, report = true
            )
            ExitKind.LOW_MEMORY -> event(
                head, LogcatLevel.WARN, "dimatikan sistem karena memori habis", detail, abnormal = true, report = true
            )
            ExitKind.SIGNALED -> event(
                head, LogcatLevel.WARN, "dihentikan paksa oleh sinyal sistem", detail, abnormal = true, report = true
            )
            ExitKind.RESOURCE -> event(
                head, LogcatLevel.WARN, "dihentikan karena memakai sumber daya berlebihan", detail, abnormal = true, report = true
            )
            ExitKind.NORMAL -> event(
                head, LogcatLevel.INFO, "ditutup normal oleh pengguna atau aplikasi sendiri", detail, abnormal = false, report = false
            )
            else -> if (previous.foreground) {
                event(
                    head,
                    LogcatLevel.WARN,
                    "berhenti mendadak saat sedang dipakai tanpa catatan crash; kemungkinan dimatikan sistem " +
                        "(memori habis) atau crash native",
                    detail,
                    abnormal = true,
                    report = true
                )
            } else {
                event(
                    head,
                    LogcatLevel.INFO,
                    "berhenti saat di latar belakang; ditutup pengguna atau dimatikan sistem",
                    detail,
                    abnormal = false,
                    report = false
                )
            }
        }
    }

    /** Baris log untuk [event]; [stamp] berbentuk "MM-DD HH:MM:SS.mmm". */
    fun toEntry(event: ProcessEvent, pid: Int, stamp: String): LogcatEntry = LogcatEntry(
        date = stamp.substringBefore(' '),
        time = stamp.substringAfter(' '),
        pid = pid,
        tid = pid,
        level = event.level,
        tag = TAG,
        message = event.message
    )

    private fun event(
        head: String,
        level: LogcatLevel,
        reason: String,
        detail: String?,
        abnormal: Boolean,
        report: Boolean
    ): ProcessEvent {
        val text = head + ": " + reason + (if (detail != null) " [$detail]" else "")
        return ProcessEvent(level, text, abnormal, report)
    }
}
