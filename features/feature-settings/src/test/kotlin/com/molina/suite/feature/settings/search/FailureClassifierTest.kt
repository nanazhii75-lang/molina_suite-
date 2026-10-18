package com.molina.suite.feature.settings.search

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureClassifierTest {

    @Test
    fun pinOrHostnameFailureIsCertificateMismatch() {
        assertEquals(
            SearchFailure.CERTIFICATE_MISMATCH,
            FailureClassifier.classify(SSLPeerUnverifiedException("Certificate pinning failure!"))
        )
    }

    @Test
    fun untrustedChainWrappedInHandshakeIsCertificateMismatch() {
        val wrapped = SSLHandshakeException("x").apply { initCause(CertPathValidatorException("Trust anchor")) }
        assertEquals(SearchFailure.CERTIFICATE_MISMATCH, FailureClassifier.classify(wrapped))
        val expired = SSLHandshakeException("x").apply { initCause(CertificateException("kedaluwarsa")) }
        assertEquals(SearchFailure.CERTIFICATE_MISMATCH, FailureClassifier.classify(expired))
    }

    @Test
    fun plainHandshakeFailureIsJustNetwork() {
        assertEquals(SearchFailure.NETWORK, FailureClassifier.classify(SSLHandshakeException("reset")))
    }

    @Test
    fun unknownHostIsDnsFailure() {
        assertEquals(SearchFailure.DNS_FAILED, FailureClassifier.classify(UnknownHostException("x")))
    }

    @Test
    fun timeoutsAreTimeouts() {
        assertEquals(SearchFailure.TIMEOUT, FailureClassifier.classify(SocketTimeoutException("x")))
        assertEquals(SearchFailure.TIMEOUT, FailureClassifier.classify(InterruptedIOException("timeout")))
    }

    @Test
    fun anythingElseIsNetwork() {
        assertEquals(SearchFailure.NETWORK, FailureClassifier.classify(IOException("putus")))
    }
}
