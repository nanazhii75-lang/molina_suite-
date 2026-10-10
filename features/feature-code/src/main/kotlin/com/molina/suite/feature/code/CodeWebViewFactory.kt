package com.molina.suite.feature.code

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Membuat WebView yang hanya boleh memuat server code-server lokal. Tautan
 * http/https lain dibuka di peramban luar; akses berkas dimatikan.
 * Catatan: lalu lintas http ke loopback diizinkan karena targetSdk 26; bila
 * targetSdk dinaikkan ke 28 atau lebih, dibutuhkan network security config.
 */
internal object CodeWebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, onMainFrameError: (String) -> Unit): WebView {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setGeolocationEnabled(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (isLocalServer(uri)) return false
                openExternally(view.context, uri)
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    onMainFrameError(error.description?.toString().orEmpty())
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame && errorResponse.statusCode >= SERVER_ERROR_START) {
                    onMainFrameError("HTTP ${errorResponse.statusCode}")
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        return webView
    }

    private fun isLocalServer(uri: Uri): Boolean =
        uri.scheme == "http" &&
            uri.host == CodeServerEndpoint.HOST &&
            uri.port == CodeServerEndpoint.PORT

    private fun openExternally(context: Context, uri: Uri) {
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https") return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Tidak ada peramban yang bisa membuka tautan ini; tautan diabaikan.
        }
    }

    private const val SERVER_ERROR_START = 500
}
