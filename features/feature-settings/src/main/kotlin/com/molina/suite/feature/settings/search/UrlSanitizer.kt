package com.molina.suite.feature.settings.search

import java.net.URLDecoder
import java.util.Locale

/**
 * Membersihkan tautan hasil pencarian: membuka pembungkus pengalihan pelacak, menolak skema selain
 * http/https, menolak tautan ber-userinfo (penyamaran alamat), menolak host iklan/pelacak, dan membuang
 * parameter pelacakan. Murni (tanpa I/O dan tanpa android.net.Uri) sehingga mudah diuji.
 */
object UrlSanitizer {

    private const val MAX_URL_LENGTH = 2048
    private const val MAX_UNWRAP_DEPTH = 3

    private val urlPattern = Regex(
        "^(https?)://([^/?#]*)([^?#]*)(?:\\?([^#]*))?(?:#(.*))?$",
        RegexOption.IGNORE_CASE
    )
    private val hostPattern = Regex("^[\\p{L}\\p{N}.-]+$")

    private val trackingKeys = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "li_fat_id", "igshid", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "__hssc", "__hstc", "__hsfp",
        "hsctatracking", "mkt_tok", "vero_id", "oly_anon_id", "oly_enc_id", "wickedid", "ef_id",
        "s_kwcid", "srsltid", "ref_src", "ref_url", "_ga", "_gl", "spm"
    )
    private val trackingPrefixes = listOf("utm_", "hsa_", "pk_", "mtm_", "matomo_", "vero_")
    private val videoHostKeys = setOf("si", "feature", "pp")

    private class Parts(
        val scheme: String,
        val host: String,
        val port: String,
        val path: String,
        val query: String?,
        val fragment: String?
    )

    /** Hasil tautan bersih, atau null bila tautan tidak aman, bukan http(s), atau milik iklan/pelacak. */
    fun clean(raw: String): String? {
        var current = normalizeScheme(raw.trim()) ?: return null
        repeat(MAX_UNWRAP_DEPTH + 1) {
            if (current.length > MAX_URL_LENGTH) return null
            val parts = parse(current) ?: return null
            val target = unwrapTarget(parts) ?: return finish(parts)
            current = normalizeScheme(target) ?: return null
        }
        return null
    }

    /** Nama host untuk ditampilkan (tanpa "www."). */
    fun displayHost(url: String): String {
        val parts = parse(url) ?: return ""
        return parts.host.removePrefix("www.")
    }

    /** Kunci untuk membuang hasil ganda. */
    fun dedupeKey(url: String): String {
        val parts = parse(url) ?: return url
        return parts.host.removePrefix("www.") + parts.path.trimEnd('/') + (parts.query?.let { "?$it" } ?: "")
    }

    private fun normalizeScheme(value: String): String? {
        val candidate = if (value.startsWith("//")) "https:$value" else value
        return if (urlPattern.matches(candidate)) candidate else null
    }

    private fun parse(url: String): Parts? {
        val m = urlPattern.matchEntire(url) ?: return null
        val authority = m.groupValues[2]
        if (authority.isEmpty() || authority.contains('@') || authority.startsWith("[")) return null
        val colon = authority.lastIndexOf(':')
        val hostPart = if (colon >= 0) authority.substring(0, colon) else authority
        val port = if (colon >= 0) authority.substring(colon + 1) else ""
        if (port.isNotEmpty() && (port.length > 5 || !port.all { it in '0'..'9' })) return null
        val host = hostPart.trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty() || !hostPattern.matches(host)) return null
        return Parts(
            scheme = m.groupValues[1].lowercase(Locale.ROOT),
            host = host,
            port = port,
            path = m.groupValues[3],
            query = m.groups[4]?.value,
            fragment = m.groups[5]?.value
        )
    }

    private fun finish(parts: Parts): String? {
        if (TrackerHosts.isBlocked(parts.host) || TrackerHosts.isAdPath(parts.host, parts.path)) return null
        val query = stripTracking(parts.host, parts.query)
        return buildString {
            append(parts.scheme).append("://").append(parts.host)
            if (parts.port.isNotEmpty()) append(':').append(parts.port)
            append(parts.path)
            if (query != null) append('?').append(query)
            if (!parts.fragment.isNullOrEmpty()) append('#').append(parts.fragment)
        }
    }

    private fun stripTracking(host: String, query: String?): String? {
        if (query.isNullOrEmpty()) return null
        val kept = query.split('&').filter { pair ->
            pair.isNotEmpty() && !isTrackingKey(host, decodeKey(pair.substringBefore('=')))
        }
        return kept.joinToString("&").ifEmpty { null }
    }

    private fun decodeKey(raw: String): String = try {
        URLDecoder.decode(raw, "UTF-8").lowercase(Locale.ROOT)
    } catch (e: IllegalArgumentException) {
        raw.lowercase(Locale.ROOT)
    }

    private fun isTrackingKey(host: String, key: String): Boolean {
        if (key in trackingKeys || trackingPrefixes.any { key.startsWith(it) }) return true
        val video = TrackerHosts.hostIs(host, "youtube.com") || host == "youtu.be"
        return video && key in videoHostKeys
    }

    /** Alamat tujuan sebenarnya bila [parts] adalah pembungkus pengalihan yang dikenal. */
    private fun unwrapTarget(parts: Parts): String? {
        val host = parts.host
        val param = when {
            TrackerHosts.hostIs(host, "duckduckgo.com") && parts.path.startsWith("/l/") -> "uddg"
            (host.startsWith("www.google.") || host.startsWith("google.")) && parts.path == "/url" -> "q"
            (host == "l.facebook.com" || host == "lm.facebook.com") ||
                (TrackerHosts.hostIs(host, "facebook.com") && parts.path == "/l.php") -> "u"
            TrackerHosts.hostIs(host, "youtube.com") && parts.path == "/redirect" -> "q"
            else -> return null
        }
        val value = queryParam(parts.query, param)
        if (value == null && param == "q") {
            return queryParam(parts.query, "url")
        }
        return value
    }

    private fun queryParam(query: String?, name: String): String? {
        if (query == null) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            val key = if (eq >= 0) pair.substring(0, eq) else pair
            if (key == name) {
                val value = if (eq >= 0) pair.substring(eq + 1) else ""
                return try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
        return null
    }
}
