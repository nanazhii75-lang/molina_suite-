package com.molina.suite.core.common

import java.util.Locale

/**
 * Catatan koneksi keluar selama sesi ini: host mana yang dihubungi, lewat HTTPS atau tidak, dari
 * modul mana, berapa kali, dan kapan terakhir.
 *
 * Yang disimpan hanya host dan port. Jalur, query (bisa memuat ID video atau token), serta
 * user:password dibuang sebelum disimpan. Catatan hanya hidup di memori: hilang saat proses
 * berakhir, tidak ditulis ke disk, dan tidak dikirim ke mana pun.
 */
class ConnectionLedger(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Satu host yang pernah dihubungi; [port] null berarti port bawaan skema. */
    data class Entry(
        val host: String,
        val port: Int?,
        val secure: Boolean,
        val source: ActivitySource,
        val count: Long,
        val lastAtMillis: Long
    )

    private data class Key(val host: String, val port: Int?, val secure: Boolean, val source: ActivitySource)

    private class Target(val host: String, val port: Int?, val secure: Boolean)

    private val lock = Any()
    private val entries = LinkedHashMap<Key, Entry>()

    /** Mencatat satu koneksi ke [url]. URL yang bukan http(s) atau tidak terbaca diabaikan. */
    fun record(source: ActivitySource, url: String) {
        val target = parse(url) ?: return
        val now = clock()
        val key = Key(target.host, target.port, target.secure, source)
        synchronized(lock) {
            val old = entries[key]
            entries[key] = if (old == null) {
                Entry(target.host, target.port, target.secure, source, 1L, now)
            } else {
                old.copy(count = old.count + 1L, lastAtMillis = now)
            }
            if (entries.size > capacity) {
                val oldest = entries.minByOrNull { it.value.lastAtMillis }?.key
                if (oldest != null) entries.remove(oldest)
            }
        }
    }

    /** Salinan catatan, yang terbaru di depan. */
    fun snapshot(): List<Entry> = synchronized(lock) {
        entries.values.sortedByDescending { it.lastAtMillis }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private fun parse(url: String): Target? {
        val text = url.trim()
        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0) return null
        val secure = when (text.substring(0, schemeEnd).lowercase(Locale.ROOT)) {
            "https" -> true
            "http" -> false
            else -> return null
        }
        val authority = text.substring(schemeEnd + 3)
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        val host: String
        val rest: String
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            rest = authority.substring(close + 1)
        } else {
            host = authority.substringBefore(':')
            rest = authority.substring(host.length)
        }
        if (host.isEmpty()) return null
        val port: Int? = when {
            rest.isEmpty() -> null
            rest.startsWith(":") -> (rest.substring(1).toIntOrNull()?.takeIf { it in 1..65535 }) ?: return null
            else -> return null
        }
        return Target(host.lowercase(Locale.ROOT), port, secure)
    }

    companion object {
        const val DEFAULT_CAPACITY = 200

        /** Catatan bersama seluruh aplikasi; modul mengisi, layar Setting membaca. */
        val shared: ConnectionLedger by lazy { ConnectionLedger() }
    }
}
