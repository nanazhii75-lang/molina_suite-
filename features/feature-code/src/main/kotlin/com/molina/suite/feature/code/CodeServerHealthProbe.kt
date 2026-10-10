package com.molina.suite.feature.code

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Memeriksa apakah server code-server menjawab di loopback (/healthz tanpa autentikasi). */
internal class CodeServerHealthProbe {

    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(CodeServerEndpoint.HEALTH_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
            }
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: IOException) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 1_000
        const val READ_TIMEOUT_MS = 1_500
    }
}
