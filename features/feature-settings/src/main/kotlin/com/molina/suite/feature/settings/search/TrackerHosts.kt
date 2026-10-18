package com.molina.suite.feature.settings.search

/** Daftar host iklan dan pelacak yang tidak pernah ditampilkan sebagai hasil. */
object TrackerHosts {

    private val blockedDomains = setOf(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com", "googletagmanager.com",
        "googletagservices.com", "google-analytics.com", "adservice.google.com", "app-measurement.com",
        "facebook.net", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "adnxs.com",
        "scorecardresearch.com", "hotjar.com", "mixpanel.com", "segment.io", "segment.com",
        "amplitude.com", "amazon-adsystem.com", "adsrvr.org", "pubmatic.com", "rubiconproject.com",
        "openx.net", "quantserve.com", "moatads.com", "bat.bing.com", "ads.yahoo.com", "advertising.com",
        "adform.net", "smartadserver.com", "casalemedia.com", "yieldmo.com", "zedo.com", "bluekai.com",
        "demdex.net", "everesttech.net", "krxd.net", "serving-sys.com", "2mdn.net", "mathtag.com",
        "tiqcdn.com", "clicktale.net", "mouseflow.com", "fullstory.com", "crazyegg.com", "optimizely.com"
    )

    fun isBlocked(host: String): Boolean {
        var candidate = host.lowercase()
        while (true) {
            if (candidate in blockedDomains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    /** Jalur pengalihan iklan pada mesin pencari besar (klik iklan berbayar). */
    fun isAdPath(host: String, path: String): Boolean {
        val h = host.lowercase()
        return when {
            hostIs(h, "bing.com") -> path.startsWith("/aclick") || path.startsWith("/aclk")
            hostIs(h, "duckduckgo.com") -> path.startsWith("/y.js")
            h.startsWith("www.google.") || h.startsWith("google.") -> path.startsWith("/aclk")
            else -> false
        }
    }

    fun hostIs(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")
}
