package com.molina.suite.feature.settings.run

internal enum class RunState { RUNNING, DONE, FAILED }

/**
 * Isi berkas status yang ditulis skrip proses: baris key=value. Kunci umum: state, stage,
 * progress (0-100), error. Kunci lain milik alat masing-masing dan tersedia lewat [value].
 */
internal data class RunStatus(
    val state: RunState,
    val stage: String,
    val progress: Int,
    val error: String,
    private val values: Map<String, String>
) {
    /** Nilai kunci [key], atau null bila kosong atau "-" (artinya belum ada atau gagal). */
    fun value(key: String): String? = values[key]?.takeIf { it.isNotEmpty() && it != "-" }

    companion object {
        /** Null bila teks bukan status yang valid (mis. state tidak dikenal). */
        fun parse(text: String): RunStatus? {
            val map = LinkedHashMap<String, String>()
            for (line in text.lineSequence()) {
                val eq = line.indexOf('=')
                if (eq <= 0) continue
                map[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
            val state = when (map["state"]) {
                "running" -> RunState.RUNNING
                "done" -> RunState.DONE
                "failed" -> RunState.FAILED
                else -> return null
            }
            val progress = (map["progress"]?.toIntOrNull() ?: 0).coerceIn(0, 100)
            return RunStatus(state, map["stage"].orEmpty(), progress, map["error"].orEmpty(), map)
        }
    }
}
