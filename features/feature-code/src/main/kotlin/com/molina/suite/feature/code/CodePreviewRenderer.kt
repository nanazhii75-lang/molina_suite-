package com.molina.suite.feature.code

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Menyusun halaman HTML untuk Tinjauan. Markdown dirender dengan CommonMark 0.13.0 (versi yang
 * sama dengan Markwon di engine Terminal): HTML mentah di dalam Markdown di-escape. Tautan
 * tidak berbahaya karena JavaScript mati di Markdown dan navigasi diblokir. Berkas HTML dipakai apa adanya;
 * bila [baseUrl] ada, tag base disisipkan supaya gambar dan CSS relatif ikut terbaca.
 */
internal object CodePreviewRenderer {

    private val EXTENSIONS: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create()
    )

    private val parser: Parser = Parser.builder().extensions(EXTENSIONS).build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
        .escapeHtml(true)
        .build()

    private val HEAD_TAG = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val BASE_TAG = Regex("<base\\s", RegexOption.IGNORE_CASE)

    private const val STYLE = """
        :root { color-scheme: dark; }
        body { background: #2B2B2B; color: #A9B7C6; margin: 0; padding: 16px;
               font-family: sans-serif; line-height: 1.55; word-wrap: break-word; }
        h1, h2, h3, h4, h5, h6 { color: #FFC66D; line-height: 1.25; }
        h1, h2 { border-bottom: 1px solid #4A4A4A; padding-bottom: 0.3em; }
        a { color: #7FB8E8; }
        code { background: #313335; color: #CC8242; padding: 0.1em 0.35em; border-radius: 4px;
               font-family: monospace; font-size: 0.92em; }
        pre { background: #313335; padding: 12px; border-radius: 6px; overflow-x: auto; }
        pre code { background: none; color: #A9B7C6; padding: 0; }
        blockquote { margin: 0; padding: 0 1em; color: #808080; border-left: 4px solid #8D5A2B; }
        table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; }
        th, td { border: 1px solid #4A4A4A; padding: 6px 10px; }
        th { background: #313335; }
        img { max-width: 100%; height: auto; }
        hr { border: 0; border-top: 1px solid #4A4A4A; }
    """

    fun markdown(source: String, baseUrl: String?): String {
        val body = renderer.render(parser.parse(source))
        val base = if (baseUrl != null) "<base href=\"" + escapeAttribute(baseUrl) + "\">" else ""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            base + "<style>" + STYLE + "</style></head><body>" + body + "</body></html>"
    }

    fun html(source: String, baseUrl: String?): String {
        if (baseUrl == null || BASE_TAG.containsMatchIn(source)) return source
        val tag = "<base href=\"" + escapeAttribute(baseUrl) + "\">"
        val head = HEAD_TAG.find(source)
        if (head != null) return source.substring(0, head.range.last + 1) + tag + source.substring(head.range.last + 1)
        val root = HTML_TAG.find(source)
        if (root != null) {
            return source.substring(0, root.range.last + 1) + "<head>" + tag + "</head>" +
                source.substring(root.range.last + 1)
        }
        return tag + source
    }

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
