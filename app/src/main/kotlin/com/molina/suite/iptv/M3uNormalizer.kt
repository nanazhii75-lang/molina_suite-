package com.molina.suite.iptv

import java.util.Locale

/**
 * Merapikan playlist M3U: nama saluran menjadi HURUF KAPITAL, group-title diganti kategori
 * baku, dan saluran diurutkan per kategori lalu nama. Baris lain milik saluran (#EXTVLCOPT,
 * #KODIPROP, #EXTHTTP, URL) dibawa utuh bersama saluran masing-masing. Fungsi ini murni
 * (tanpa I/O) sehingga mudah diuji.
 */
object M3uNormalizer {

    private const val UNGROUPED = "LAINNYA"
    private const val LOCAL = "LOKAL"

    /** Urutan tampil kategori; kategori yang tidak dikenal selalu di paling bawah. */
    private val groupOrder = listOf(
        "NASIONAL", "BERITA", "OLAHRAGA", "RELIGI", "ANAK-ANAK", "MUSIK", LOCAL, UNGROUPED
    )

    private fun words(vararg alternatives: String): Regex =
        Regex("\\b(" + alternatives.joinToString("|") + ")\\b")

    /** Aturan pertama yang cocok dengan nama saluran menang; ubah di sini untuk menyetel kategori. */
    private val rules: List<Pair<String, Regex>> = listOf(
        "BERITA" to words(
            "news", "berita", "metro ?tv", "kompas ?tv", "tv ?one", "cnn", "cnbc", "inews", "rri"
        ),
        "OLAHRAGA" to words("sport", "sports", "olahraga", "soccer", "bola"),
        "RELIGI" to words(
            "ahsan", "madani", "salam", "dmi", "daai", "quran", "islam", "ashiil", "rodja",
            "kristen", "church"
        ),
        "ANAK-ANAK" to words("kids", "anak", "kartun", "cartoon"),
        "MUSIK" to words("music", "musik", "mtv"),
        "NASIONAL" to words(
            "sctv", "antv", "rcti", "mnc ?tv", "gtv", "indosiar", "moji", "trans ?tv", "trans ?7",
            "rtv", "tvri", "garuda"
        ),
        LOCAL to words(
            "jtv", "jawa ?pos", "stara", "bandung", "balikpapan", "banten", "banyumas", "banjar",
            "jogja", "kawanua", "timor", "kediri", "malang", "sumedang", "cianjur", "bojonegoro",
            "madiun", "bungo", "atambua"
        )
    )

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val extinfRegex = Regex("""^#EXTINF:\s*(-?\d+(?:\.\d+)?)(.*)$""")
    private val spaces = Regex("""\s+""")

    private class Entry(val rank: Int, val name: String, val lines: List<String>)

    fun normalizeBytes(bytes: ByteArray): ByteArray =
        normalize(String(bytes, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)

    fun normalize(text: String): String {
        val source = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val header = ArrayList<String>()
        val entries = ArrayList<Entry>()
        var block: MutableList<String>? = null
        var rank = 0
        var name = ""

        for (raw in source.split('\n')) {
            val line = raw.trimEnd()
            val match = extinfRegex.find(line)
            if (match != null) {
                block?.let { entries += Entry(rank, name, it) }
                val parsed = parseExtinf(match.groupValues[1], match.groupValues[2])
                rank = groupOrder.indexOf(parsed.group)
                name = parsed.name
                block = mutableListOf(parsed.line)
            } else if (line.isEmpty() || line.startsWith("#EXTGRP", ignoreCase = true)) {
                continue
            } else {
                val current = block
                if (current == null) header += line else current += line
            }
        }
        block?.let { entries += Entry(rank, name, it) }

        val out = StringBuilder()
        if (header.isEmpty()) out.append("#EXTM3U\n") else header.forEach { out.append(it).append('\n') }
        entries.sortedWith(compareBy<Entry>({ it.rank }, { it.name }))
            .forEach { entry -> entry.lines.forEach { out.append(it).append('\n') } }
        return out.toString()
    }

    private class Parsed(val group: String, val name: String, val line: String)

    private fun parseExtinf(duration: String, rest: String): Parsed {
        val attrs = attrRegex.findAll(rest).toList()
        val values = LinkedHashMap<String, String>()
        for (attr in attrs) values[attr.groupValues[1]] = attr.groupValues[2]

        val afterAttrs = attrs.lastOrNull()?.range?.last?.plus(1) ?: 0
        val comma = rest.indexOf(',', afterAttrs)
        var title = if (comma >= 0) rest.substring(comma + 1) else ""
        title = title.replace(spaces, " ").trim()
        if (title.isEmpty()) title = values["tvg-name"].orEmpty().replace(spaces, " ").trim()

        val name = title.uppercase(Locale.ROOT).ifEmpty { "TANPA NAMA" }
        val group = classify(title, values["group-title"].orEmpty())
        values["group-title"] = group

        val line = StringBuilder("#EXTINF:").append(duration)
        for ((key, value) in values) line.append(' ').append(key).append("=\"").append(value).append('"')
        line.append(',').append(name)
        return Parsed(group, name, line.toString())
    }

    private fun classify(title: String, originalGroup: String): String {
        val key = title.lowercase(Locale.ROOT)
        for ((group, regex) in rules) if (regex.containsMatchIn(key)) return group
        val given = originalGroup.trim().uppercase(Locale.ROOT)
        return when {
            given == "LOCAL" -> LOCAL
            given in groupOrder -> given
            else -> UNGROUPED
        }
    }
}
