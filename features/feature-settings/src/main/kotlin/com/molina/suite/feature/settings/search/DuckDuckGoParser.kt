package com.molina.suite.feature.settings.search

/** Hasil penguraian satu halaman HTML DuckDuckGo. */
class ParsedPage(
    val results: List<SearchResult>,
    /** Iklan dan pelacak yang dibuang. */
    val blocked: Int,
    /** True bila halaman memuat penanda hasil (walau semuanya dibuang). */
    val sawResultMarkup: Boolean
)

/**
 * Mengurai halaman hasil html.duckduckgo.com tanpa pustaka HTML (ringan dan tanpa JavaScript).
 * Iklan dibuang berdasarkan kelas kontainer maupun tautan, tautan dibersihkan oleh [UrlSanitizer],
 * dan hasil ganda digabung.
 */
object DuckDuckGoParser {

    const val MAX_RESULTS = 40

    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val titleAnchor = Regex(
        "<a\\b[^>]*\\bclass\\s*=\\s*\"[^\"]*\\bresult__a\\b[^\"]*\"[^>]*>(.*?)</a>", options
    )
    private val snippetBlock = Regex(
        "<(a|div|td|span)\\b[^>]*\\bclass\\s*=\\s*\"[^\"]*\\bresult__snippet\\b[^\"]*\"[^>]*>(.*?)</\\1>", options
    )
    private val divClass = Regex("<div\\b[^>]*\\bclass\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val hrefAttr = Regex("\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")

    fun parse(html: String): ParsedPage {
        val anchors = titleAnchor.findAll(html).toList()
        val seen = HashSet<String>()
        val results = ArrayList<SearchResult>()
        var blocked = 0
        for ((index, anchor) in anchors.withIndex()) {
            if (results.size >= MAX_RESULTS) break
            val headerStart = if (index == 0) 0 else anchors[index - 1].range.last + 1
            val header = html.substring(headerStart, anchor.range.first)
            val opening = anchor.value.substring(0, anchor.value.indexOf('>') + 1)
            val href = hrefOf(opening)
            if (href == null) continue
            if (isAdContainer(header) || isAdHref(href)) {
                blocked++
                continue
            }
            val url = UrlSanitizer.clean(href)
            if (url == null) {
                blocked++
                continue
            }
            if (!seen.add(UrlSanitizer.dedupeKey(url))) continue
            val host = UrlSanitizer.displayHost(url)
            val title = HtmlText.plain(anchor.groupValues[1]).ifEmpty { host }
            val tailEnd = if (index + 1 < anchors.size) anchors[index + 1].range.first else html.length
            val snippet = snippetOf(html.substring(anchor.range.last + 1, tailEnd))
            results += SearchResult(title.take(200), url, host, snippet.take(400))
        }
        return ParsedPage(results, blocked, anchors.isNotEmpty())
    }

    /** Halaman verifikasi anti-bot (tantangan) menggantikan hasil. */
    fun isChallenge(html: String): Boolean =
        html.contains("anomaly-modal") ||
            html.contains("anomaly.js") ||
            html.contains("bots use DuckDuckGo too")

    fun isNoResultsPage(html: String): Boolean =
        html.contains("no-results") || html.contains("No results.")

    private fun hrefOf(openingTag: String): String? {
        val m = hrefAttr.find(openingTag) ?: return null
        val raw = m.groups[1]?.value ?: m.groups[2]?.value ?: return null
        return HtmlText.decode(raw).trim().ifEmpty { null }
    }

    private fun isAdContainer(header: String): Boolean {
        var last: List<String>? = null
        for (m in divClass.findAll(header)) {
            val tokens = whitespace.split(m.groupValues[1].trim())
            if ("result" in tokens) last = tokens
        }
        return last?.any { it.startsWith("result--ad") } == true
    }

    private fun isAdHref(href: String): Boolean =
        href.contains("ad_domain=") || href.contains("ad_provider=") || href.contains("/y.js")

    private fun snippetOf(tail: String): String {
        val m = snippetBlock.find(tail) ?: return ""
        return HtmlText.plain(m.groupValues[2])
    }
}
