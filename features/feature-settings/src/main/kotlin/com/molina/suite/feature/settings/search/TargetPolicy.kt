package com.molina.suite.feature.settings.search

import java.util.Locale
import okhttp3.HttpUrl

/** Hanya HTTPS port 443 ke host yang diizinkan, tanpa user:password pada alamat. */
object TargetPolicy {

    fun allows(url: HttpUrl, allowedHosts: Set<String>): Boolean =
        url.isHttps &&
            url.port == 443 &&
            url.host.lowercase(Locale.ROOT) in allowedHosts &&
            url.username.isEmpty() &&
            url.password.isEmpty()
}
