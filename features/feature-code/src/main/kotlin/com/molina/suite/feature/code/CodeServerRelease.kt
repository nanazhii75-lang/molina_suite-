package com.molina.suite.feature.code

/** Rilis code-server resmi (coder/code-server) yang dipasang di dalam Debian. */
internal object CodeServerRelease {
    const val VERSION = "4.141.0"
    const val ASSET_NAME = "code-server-$VERSION-linux-arm64.tar.gz"
    const val DOWNLOAD_URL =
        "https://github.com/coder/code-server/releases/download/v$VERSION/$ASSET_NAME"

    /** Ukuran dan SHA-256 arsip menurut digest GitHub untuk rilis ini. */
    const val SIZE_BYTES = 234587826L
    const val SHA256 = "e5cc78da1d631c6cf6162f9b18eeaa8ee9d3239e1a88bb99d0602cdc5fa1852d"
}
