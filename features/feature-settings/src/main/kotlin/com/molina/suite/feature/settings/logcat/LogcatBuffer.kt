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

    init {
        require(capacity > 0) { "capacity harus positif" }
    }

    val revision: Long get() = synchronized(lock) { counter }

    val size: Int get() = synchronized(lock) { entries.size }

    fun add(entry: LogcatEntry) {
        synchronized(lock) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(entry)
            counter++
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            counter++
        }
    }

    /** Salinan entri yang lolos [filter], urut dari tertua; aman dipakai di thread tampilan. */
    fun snapshot(filter: LogcatFilter = LogcatFilter()): List<LogcatEntry> =
        synchronized(lock) { entries.filter { filter.matches(it) } }

    companion object {
        const val DEFAULT_CAPACITY = 8000
    }
}
