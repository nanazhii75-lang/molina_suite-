package com.molina.suite.feature.settings.logcat

/**
 * Pelacak proses aplikasi yang berjalan: saat [begin] ia membaca catatan proses sebelumnya
 * ([takePrevious]) lalu menulis catatan untuk proses ini, dan sesudahnya menjaga catatan itu
 * tetap mutakhir ([setForeground], [markJavaCrash]). Setiap perubahan langsung ditulis ke disk
 * karena proses bisa mati kapan saja. Aman dipakai dari beberapa thread.
 */
internal class ProcessTracker(
    private val store: ProcessStateStore,
    val pid: Int,
    clock: () -> Long = System::currentTimeMillis
) {

    private val lock = Any()
    private var begun = false
    private var previous: ProcessRecord? = null
    private var current = ProcessRecord(pid, clock(), foreground = false, javaCrash = false)

    /** Membaca catatan proses sebelumnya dan menulis catatan proses ini; panggilan berikutnya diabaikan. */
    fun begin() {
        synchronized(lock) {
            if (begun) return
            begun = true
            previous = store.read()
            store.write(current)
        }
    }

    /** Catatan proses sebelumnya, sekali saja; null bila sudah diambil atau belum pernah ada. */
    fun takePrevious(): ProcessRecord? = synchronized(lock) {
        val record = previous
        previous = null
        record
    }

    fun setForeground(foreground: Boolean) {
        synchronized(lock) {
            if (current.foreground == foreground) return
            current = current.copy(foreground = foreground)
            store.write(current)
        }
    }

    /** Menandai bahwa proses ini mati karena crash Java/Kotlin; dipanggil dari penangan crash. */
    fun markJavaCrash() {
        synchronized(lock) {
            if (current.javaCrash) return
            current = current.copy(javaCrash = true)
            store.write(current)
        }
    }
}
