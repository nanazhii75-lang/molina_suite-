package com.molina.suite.feature.settings.library

import java.net.URI
import java.net.URISyntaxException

/**
 * Penyaring playlist M3U dari sumber yang tidak tepercaya (URL milik pengguna).
 *
 * Daftar putih ketat: hanya #EXTM3U, #EXTINF, #EXTVLCOPT referrer/user-agent, dan URL http(s).
 * Hasil selalu dibentuk ulang dari bagian yang lolos, tidak pernah disalin mentah, sehingga
 * tidak ada isi dari jaringan yang bisa menjadi perintah, skema lain, atau opsi mpv sembarang.
 */
internal object IptvPlaylist {

    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_CHANNELS = 2000
    private const val MAX_URL_CHARS = 2048
    private const val MAX_TITLE_CHARS = 120
    private const val MAX_AGENT_CHARS = 200
    private const val MAX_TVG_CHARS = 64

    class Parsed(val text: String, val channels: Int, val dropped: Int)

    private val attrRegex = Regex("""([A-Za-z][\w-]*)="([^"]*)"""")
    private val tvgRegex = Regex("^[A-Za-z0-9._@-]{1,$MAX_TVG_CHARS}$")
    private val hostRegex = Regex("^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$")
    private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    private class Pending(val title: String, val tvg: String?) {
        var referrer: String? = null
        var agent: String? = null
    }

    /** Mengembalikan playlist yang sudah disaring, atau alasan penolakan bila bukan M3U yang layak. */
    fun sanitize(raw: String): LibraryResult<Parsed> {
        if (raw.length > MAX_BYTES) return LibraryResult.Failure("Playlist terlalu besar")
        val lines = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val first = lines.firstOrNull { it.isNotBlank() }?.trim()
        if (first == null || !first.startsWith("#EXTM3U")) {
            return LibraryResult.Failure("Bukan playlist M3U (tidak diawali #EXTM3U)")
        }

        val out = StringBuilder("#EXTM3U\n")
        val seen = HashSet<String>()
        var pending: Pending? = null
        var channels = 0
        var dropped = 0

        for (line in lines) {
            val l = line.trim()
            if (l.isEmpty() || l.startsWith("#EXTM3U")) continue
            when {
                l.startsWith("#EXTINF:") -> {
                    if (pending != null) dropped++
                    pending = parseExtinf(l)
                    if (pending == null) dropped++
                }
                l.startsWith("#EXTVLCOPT:") -> {
                    val p = pending
                    if (p == null || !applyOption(p, l.substring("#EXTVLCOPT:".length))) dropped++
                }
                l.startsWith("#") -> dropped++
                else -> {
                    val p = pending
                    pending = null
                    if (p == null || !isAllowedUrl(l) || !seen.add(l)) {
                        dropped++
                    } else if (channels < MAX_CHANNELS) {
                        append(out, p, l)
                        channels++
                    } else {
                        dropped++
                    }
                }
            }
        }
        if (pending != null) dropped++
        if (channels == 0) return LibraryResult.Failure("Tidak ada channel yang valid di playlist")
        return LibraryResult.Ok(Parsed(out.toString(), channels, dropped))
    }

    /** True bila [url] berupa http(s) dengan host publik/LAN; loopback dan link-local ditolak. */
    fun isAllowedUrl(url: String): Boolean = parseUri(url) != null

    /** Memeriksa URL dan mengembalikan URI-nya, atau null bila tidak boleh dipakai. */
    fun parseUri(url: String): URI? {
        if (url.isEmpty() || url.length > MAX_URL_CHARS || url.any { !isPlainChar(it) || it == ' ' }) return null
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null) return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return if (isBlockedHost(host)) null else uri
    }

    private fun isBlockedHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost")) return true
        if (host.startsWith("[")) return host == "[::1]" || host == "[::]" || host.startsWith("[fe80:")
        val m = ipv4Regex.matchEntire(host) ?: return false
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        return a == 0 || a == 127 || (a == 169 && b == 254)
    }

    /** Karakter yang aman ditampilkan dan ditulis: bukan kontrol, bukan pengarah teks tak terlihat. */
    private fun isPlainChar(c: Char): Boolean =
        !Character.isISOControl(c) &&
            c !in '\u200B'..'\u200F' && c !in '\u202A'..'\u202E' &&
            c !in '\u2066'..'\u2069' && c != '\uFEFF'

    private fun parseExtinf(line: String): Pending? {
        val body = line.substring("#EXTINF:".length)
        var inQuote = false
        var comma = -1
        for (i in body.indices) {
            val ch = body[i]
            if (ch == '"') inQuote = !inQuote else if (ch == ',' && !inQuote) { comma = i; break }
        }
        if (comma < 0) comma = body.lastIndexOf(',')
        if (comma < 0) return null
        val title = body.substring(comma + 1).trim()
        if (title.isEmpty() || title.length > MAX_TITLE_CHARS || !title.all(::isPlainChar)) return null
        val attrs = attrRegex.findAll(body.substring(0, comma)).associate { it.groupValues[1] to it.groupValues[2] }
        val tvg = attrs["tvg-id"]?.takeIf { tvgRegex.matches(it) }
        return Pending(title, tvg).also { p ->
            attrs["http-referrer"]?.let { setReferrer(p, it) }
            attrs["http-user-agent"]?.let { setAgent(p, it) }
        }
    }

    private fun applyOption(p: Pending, option: String): Boolean {
        val eq = option.indexOf('=')
        if (eq <= 0) return false
        val value = option.substring(eq + 1).trim()
        return when (option.substring(0, eq).trim().lowercase()) {
            "http-referrer" -> setReferrer(p, value)
            "http-user-agent" -> setAgent(p, value)
            else -> false
        }
    }

    /** Referrer berupa URL http(s), atau nama host saja (sebagian data iptv-org menulisnya begitu). */
    private fun setReferrer(p: Pending, value: String): Boolean {
        val bareHost = hostRegex.matches(value) && !isBlockedHost(value.lowercase())
        if (!bareHost && parseUri(value) == null) return false
        p.referrer = value
        return true
    }

    private fun setAgent(p: Pending, value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_AGENT_CHARS || !value.all(::isPlainChar)) return false
        p.agent = value
        return true
    }

    private fun append(out: StringBuilder, p: Pending, url: String) {
        out.append("#EXTINF:-1")
        p.tvg?.let { out.append(" tvg-id=\"").append(it).append('"') }
        out.append(',').append(p.title).append('\n')
        p.referrer?.let { out.append("#EXTVLCOPT:http-referrer=").append(it).append('\n') }
        p.agent?.let { out.append("#EXTVLCOPT:http-user-agent=").append(it).append('\n') }
        out.append(url).append('\n')
    }
}
