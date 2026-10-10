package com.molina.suite.feature.code

/** Alamat server code-server lokal. Hanya terikat ke loopback. */
internal object CodeServerEndpoint {
    const val HOST = "127.0.0.1"
    const val PORT = 8080
    const val BIND_ADDRESS = "$HOST:$PORT"
    const val BASE_URL = "http://$HOST:$PORT/"
    const val HEALTH_URL = "http://$HOST:$PORT/healthz"
}
