package com.molina.suite.feature.settings.search

import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/** Memetakan galat jaringan ke [SearchFailure]. Murni; pesan galat asli tidak pernah diteruskan. */
object FailureClassifier {

    fun classify(error: Throwable): SearchFailure = when {
        isCertificateProblem(error) -> SearchFailure.CERTIFICATE_MISMATCH
        error is UnknownHostException -> SearchFailure.DNS_FAILED
        error is InterruptedIOException -> SearchFailure.TIMEOUT
        else -> SearchFailure.NETWORK
    }

    /** Pin tidak cocok, nama host salah, atau rantai tidak tepercaya; juga bila terbungkus galat lain. */
    private fun isCertificateProblem(error: Throwable): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is SSLPeerUnverifiedException ||
                current is CertificateException ||
                current is CertPathValidatorException
            ) return true
            current = current.cause
            depth++
        }
        return false
    }

    private const val MAX_CAUSE_DEPTH = 6
}
