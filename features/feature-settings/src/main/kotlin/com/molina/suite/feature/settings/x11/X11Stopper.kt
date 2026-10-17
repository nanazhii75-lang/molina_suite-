package com.molina.suite.feature.settings.x11

/**
 * Menghentikan sekumpulan proses sampai tuntas: SIGTERM dulu, lalu SIGKILL, dan memeriksa ulang
 * sampai tidak ada yang tersisa. Proses yang sudah pernah terlihat tetap dipantau walau induknya
 * sudah mati (anak yatim), jadi tidak lolos hanya karena pohon prosesnya terputus.
 */
internal class X11Stopper(
    private val snapshot: () -> List<ProcEntry>,
    private val signal: (pid: Int, signal: Int) -> Unit,
    private val pause: suspend (millis: Long) -> Unit
) {

    data class Outcome(val stopped: Int, val remaining: Set<Int>)

    suspend fun stop(select: (List<ProcEntry>) -> Set<Int>): Outcome {
        val seen = HashSet<Int>()

        fun current(): Set<Int> {
            val table = snapshot()
            val live = table.filter { it.isAlive }.mapTo(HashSet()) { it.pid }
            val wanted = select(table).toMutableSet()
            for (pid in seen) if (pid in live) wanted += pid
            return wanted
        }

        var remaining = current()
        var round = 0
        while (remaining.isNotEmpty() && round < SIGNALS.size) {
            seen += remaining
            for (pid in remaining) signal(pid, SIGNALS[round])
            var waited = 0L
            remaining = current()
            while (remaining.isNotEmpty() && waited < WAIT_MS[round]) {
                pause(POLL_MS)
                waited += POLL_MS
                remaining = current()
            }
            round++
        }
        return Outcome((seen.size - remaining.size).coerceAtLeast(0), remaining)
    }

    private companion object {
        const val SIGTERM = 15
        const val SIGKILL = 9
        const val POLL_MS = 250L
        val SIGNALS = intArrayOf(SIGTERM, SIGKILL, SIGKILL)
        val WAIT_MS = longArrayOf(2_000L, 1_500L, 1_000L)
    }
}
