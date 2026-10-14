package com.molina.suite.feature.settings.logcat

/**
 * Penampung entri berukuran tetap: bila penuh, entri tertua dibuang. Aman dipakai dari beberapa
 * thread (pembaca menulis, tampilan membaca). [revision] naik setiap ada perubahan sehingga
 * tampilan tahu kapan perlu menggambar ulang tanpa membandingkan isi.
 */
internal class LogcatBuffer(val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val entries = ArrayDeque<LogcatEntry>()
    private var counter = 0L
    private var added = 0L

    init {
        require(capacity > 0) { "capacity harus positif" }
    }

    val revision: Long get() = synchronized(lock) { counter }

    val size: Int get() = synchronized(lock) { entries.size }

    /** Jumlah entri yang pernah masuk sejak proses dimulai; tidak berkurang saat [clear] atau buang tertua. */
    val total: Long get() = synchronized(lock) { added }

    fun add(entry: LogcatEntry) {
        synchronized(lock) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(entry)
            added++
            counter++
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            counter++
        }
    }

    /**
     * Salinan entri yang lolos [filter], urut dari tertua; aman dipakai di thread tampilan.
     * Hanya entri bernomor urut [since] ke atas yang ikut ([since] dari [total]); 0 berarti semua.
     */
    fun snapshot(filter: LogcatFilter = LogcatFilter(), since: Long = 0L): List<LogcatEntry> =
        synchronized(lock) {
            val skip = (since - (added - entries.size)).coerceIn(0L, entries.size.toLong()).toInt()
            entries.drop(skip).filter { filter.matches(it) }
        }

    companion object {
        const val DEFAULT_CAPACITY = 8000
    }
}
