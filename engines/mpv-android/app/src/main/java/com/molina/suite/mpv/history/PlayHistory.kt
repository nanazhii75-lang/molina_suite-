package com.molina.suite.mpv.history

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Satu baris riwayat putar. [path] adalah URL http(s) atau jalur berkas yang bisa diputar ulang. */
internal data class PlayHistoryEntry(
    val path: String,
    val title: String,
    val positionSeconds: Long,
    val durationSeconds: Long,
    val finished: Boolean,
    val updatedAtMillis: Long
)

/** Riwayat putar, disimpan lokal di storage aplikasi (tanpa akun, tanpa jaringan). */
internal object PlayHistory {

    private const val TAG = "PlayHistory"
    private const val PREFS = "molina_play_history"
    private const val KEY = "entries"
    const val MAX_ENTRIES = 30

    @Synchronized
    fun load(context: Context): List<PlayHistoryEntry> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { parse(array.optJSONObject(it)) }
        } catch (e: JSONException) {
            Log.w(TAG, "Riwayat rusak, diabaikan", e)
            emptyList()
        }
    }

    /** Menambah [entry] di paling atas; entri lama dengan jalur yang sama diganti. */
    @Synchronized
    fun put(context: Context, entry: PlayHistoryEntry) {
        val rest = load(context).filterNot { it.path == entry.path }
        save(context, (listOf(entry) + rest).take(MAX_ENTRIES))
    }

    @Synchronized
    fun remove(context: Context, path: String) {
        save(context, load(context).filterNot { it.path == path })
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun save(context: Context, entries: List<PlayHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(toJson(it)) }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(e: PlayHistoryEntry): JSONObject = JSONObject()
        .put("path", e.path)
        .put("title", e.title)
        .put("pos", e.positionSeconds)
        .put("dur", e.durationSeconds)
        .put("done", e.finished)
        .put("at", e.updatedAtMillis)

    private fun parse(o: JSONObject?): PlayHistoryEntry? {
        if (o == null) return null
        val path = o.optString("path")
        if (path.isEmpty()) return null
        return PlayHistoryEntry(
            path = path,
            title = o.optString("title", path),
            positionSeconds = o.optLong("pos"),
            durationSeconds = o.optLong("dur"),
            finished = o.optBoolean("done"),
            updatedAtMillis = o.optLong("at")
        )
    }
}
