package com.molina.suite.feature.settings.logcat

import com.molina.suite.core.common.ActivityLine
import com.molina.suite.core.common.ActivityLog
import com.molina.suite.core.common.ActivitySource

/** Kategori tampilan Logcat; [OTHER] menampung log yang bukan aktivitas (sistem, library, editor). */
internal enum class LogCategory(val label: String) {
    ALL("Semua"),
    MPV("mpv"),
    TERMINAL("Terminal"),
    CODE("Code"),
    SETTING("Setting"),
    OTHER("Lainnya");

    companion object {
        fun of(source: ActivitySource): LogCategory = when (source) {
            ActivitySource.MPV -> MPV
            ActivitySource.TERMINAL -> TERMINAL
            ActivitySource.CODE -> CODE
            ActivitySource.SETTING -> SETTING
            ActivitySource.APP -> OTHER
        }
    }
}

/** Hasil akhir sebuah aktivitas untuk warna dan label judulnya. */
internal enum class GroupStatus { OK, WARN, FAILED, OPEN }

/** Jumlah entri sebuah kategori dan berapa yang bermasalah (galat, peringatan, atau berakhir gagal). */
internal data class CategoryCount(val items: Int, val problems: Int)

/** Satu baris daftar Logcat: judul aktivitas atau satu baris log. */
internal sealed interface LogRow {

    /** Judul satu aktivitas; [entries] adalah seluruh baris aktivitas itu, urut waktu, untuk disalin. */
    data class Header(
        val id: String,
        val category: LogCategory,
        val title: String,
        val time: String,
        val durationMillis: Long?,
        val status: GroupStatus,
        val errors: Int,
        val warns: Int,
        val lines: Int,
        val expanded: Boolean,
        val entries: List<LogcatEntry>
    ) : LogRow

    /** Satu baris log; [indented] true bila baris itu milik aktivitas di atasnya. */
    data class Line(val entry: LogcatEntry, val indented: Boolean) : LogRow
}

/**
 * Hasil pengelompokan: [rows] untuk daftar (terbaru di atas), [entries] baris-baris yang sedang
 * tampil menurut urutan waktu dan tanpa penggabungan (untuk Simpan dan Salin), dan [counts]
 * jumlah per kategori sebelum kategori dipilih.
 */
internal class LogView(
    val rows: List<LogRow>,
    val entries: List<LogcatEntry>,
    val counts: Map<LogCategory, CategoryCount>
)

/**
 * Mengelompokkan baris logcat per aktivitas ([ActivityLog]) dan per kategori. Baris yang bukan
 * aktivitas tetap berdiri sendiri di kategori [LogCategory.OTHER]. Murni data: tanpa Android dan
 * tanpa efek samping, jadi bisa diuji di JVM.
 */
internal object LogGrouping {

    private const val UNKNOWN_TITLE = "Aktivitas lanjutan"
    private const val TIME_CHARS = 8

    private class Indexed(val index: Int, val entry: LogcatEntry, val line: ActivityLine?)

    private class Group(val id: String) {
        var source: ActivitySource? = null
        var title: String? = null
        var end: ActivityLine.End? = null
        val lines = ArrayList<Indexed>()
    }

    /** Rentang baris sebuah aktivitas yang pemiliknya jelas; [end] terbesar bila aktivitas belum selesai. */
    private class Window(val category: LogCategory, val start: Int, val end: Int)

    private class Item(
        val first: Int,
        val category: LogCategory,
        val problem: Boolean,
        val shown: List<Indexed>,
        val rows: (Map<String, Boolean>) -> List<LogRow>
    )

    /**
     * [full] seluruh isi buffer apa adanya; [firstVisible] indeks pertama yang boleh tampil
     * (bagian sebelumnya hanya dipakai untuk mengenali awal sebuah aktivitas). [expanded] berisi
     * pilihan buka/tutup dari pengguna; aktivitas tanpa pilihan terbuka bila bermasalah.
     */
    fun build(
        full: List<LogcatEntry>,
        firstVisible: Int,
        filter: LogcatFilter,
        category: LogCategory,
        expanded: Map<String, Boolean>
    ): LogView {
        val groups = LinkedHashMap<String, Group>()
        val loose = ArrayList<Indexed>()
        full.forEachIndexed { index, entry ->
            val line = if (entry.tag == ActivityLog.LOG_TAG) ActivityLine.parse(entry.message) else null
            if (line == null) {
                loose.add(Indexed(index, entry, null))
                return@forEachIndexed
            }
            val group = groups.getOrPut(line.id) { Group(line.id) }
            when (line) {
                is ActivityLine.Begin -> {
                    group.source = line.source
                    group.title = line.title
                }
                is ActivityLine.End -> group.end = line
                is ActivityLine.Note -> Unit
            }
            group.lines.add(Indexed(index, entry, line))
        }

        val items = ArrayList<Item>()
        for (group in groups.values) groupItem(group, firstVisible, filter)?.let { items.add(it) }
        looseItems(loose, firstVisible, filter, ownerWindows(groups.values), items)

        val counts = HashMap<LogCategory, CategoryCount>()
        for (cat in LogCategory.values()) {
            val own = if (cat == LogCategory.ALL) items else items.filter { it.category == cat }
            counts[cat] = CategoryCount(own.size, own.count { it.problem })
        }

        val picked = (if (category == LogCategory.ALL) items else items.filter { it.category == category })
            .sortedByDescending { it.first }
        val rows = ArrayList<LogRow>()
        for (item in picked) rows.addAll(item.rows(expanded))
        val entries = picked.flatMap { it.shown }.sortedBy { it.index }.map { it.entry }
        return LogView(rows, entries, counts)
    }

    /** Rentang tiap aktivitas yang awalnya terlihat di buffer dan sumbernya modul (bukan aplikasi umum). */
    private fun ownerWindows(groups: Collection<Group>): List<Window> {
        val windows = ArrayList<Window>()
        for (group in groups) {
            val source = group.source ?: continue
            val category = LogCategory.of(source)
            if (category == LogCategory.OTHER) continue
            val start = group.lines.first().index
            val end = if (group.end == null) {
                Int.MAX_VALUE
            } else {
                group.lines.last { it.line is ActivityLine.End }.index
            }
            windows.add(Window(category, start, end))
        }
        return windows.sortedBy { it.start }
    }

    private fun groupItem(group: Group, firstVisible: Int, filter: LogcatFilter): Item? {
        val matching = group.lines.filter { it.index >= firstVisible && filter.matches(it.entry) }
        if (matching.isEmpty()) return null

        val category = group.source?.let { LogCategory.of(it) } ?: LogCategory.OTHER
        val errors = group.lines.count { it.entry.level >= LogcatLevel.ERROR }
        val warns = group.lines.count { it.entry.level == LogcatLevel.WARN }
        val end = group.end
        val status = when {
            errors > 0 -> GroupStatus.FAILED
            end == null -> GroupStatus.OPEN
            !end.ok || warns > 0 -> GroupStatus.WARN
            else -> GroupStatus.OK
        }
        val children = matching.filter { it.line !is ActivityLine.Begin }.map { it.entry }.collapseRepeats()
        val all = group.lines.map { it.entry }
        val title = group.title ?: UNKNOWN_TITLE
        val time = group.lines.first().entry.time.take(TIME_CHARS)
        val defaultOpen = status == GroupStatus.FAILED || status == GroupStatus.WARN

        return Item(
            first = group.lines.first().index,
            category = category,
            problem = status == GroupStatus.FAILED || status == GroupStatus.WARN,
            shown = matching
        ) { overrides ->
            val open = overrides[group.id] ?: defaultOpen
            val header = LogRow.Header(
                id = group.id,
                category = category,
                title = title,
                time = time,
                durationMillis = end?.millis,
                status = status,
                errors = errors,
                warns = warns,
                lines = children.size,
                expanded = open,
                entries = all
            )
            if (open) listOf<LogRow>(header) + children.map { LogRow.Line(it, true) } else listOf(header)
        }
    }

    /** Baris non-aktivitas yang lolos filter; baris identik berurutan digabung seperti sebelumnya. */
    private fun looseItems(
        loose: List<Indexed>,
        firstVisible: Int,
        filter: LogcatFilter,
        windows: List<Window>,
        out: MutableList<Item>
    ) {
        val visible = loose.filter { it.index >= firstVisible && filter.matches(it.entry) }
        /** Proses anak dimiliki aktivitas terbaru yang sedang berjalan saat barisnya muncul; selain itu menurut tag. */
        fun ownerOf(item: Indexed): LogCategory {
            val base = LogSignals.category(item.entry)
            if (!LogSignals.isSubprocess(item.entry)) return base
            return windows.lastOrNull { it.start <= item.index && item.index <= it.end }?.category ?: base
        }
        var run = ArrayList<Indexed>()
        fun flush() {
            if (run.isEmpty()) return
            val head = run.first()
            val entry = head.entry.copy(repeat = run.sumOf { it.entry.repeat })
            val shown = run
            out.add(
                Item(
                    first = head.index,
                    category = ownerOf(head),
                    problem = entry.severity >= LogcatLevel.ERROR,
                    shown = shown
                ) { listOf(LogRow.Line(entry, false)) }
            )
            run = ArrayList()
        }
        for (item in visible) {
            val last = run.lastOrNull()
            if (last != null && (!same(last.entry, item.entry) || ownerOf(last) != ownerOf(item))) flush()
            run.add(item)
        }
        flush()
    }

    private fun same(a: LogcatEntry, b: LogcatEntry): Boolean =
        a.pid == b.pid && a.level == b.level && a.tag == b.tag && a.message == b.message
}
