package com.molina.suite.feature.code

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Halaman Tinjauan: menampilkan hasil render Markdown, HTML, atau SVG dari isi editor. WebView
 * dikunci: tidak ada akses jaringan, JavaScript hanya menyala untuk HTML, dan perpindahan
 * halaman diblokir. Hanya berkas di folder cache Tinjauan yang bisa dimuat.
 */
internal class CodePreviewActivity : Activity() {

    private var webView: WebView? = null
    private var executor: ExecutorService? = null
    private var pageUrl: String? = null
    private var baseUrl: String? = null
    private var scriptsEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = CodePreviewKind.fromName(intent.getStringExtra(CodePreviewLauncher.EXTRA_KIND))
        val source = intent.getStringExtra(CodePreviewLauncher.EXTRA_SOURCE)?.let { File(it) }
        if (kind == null || source == null || !isInsidePreviewDirectory(source) || !source.isFile) {
            Toast.makeText(this, R.string.code_preview_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val displayPath = intent.getStringExtra(CodePreviewLauncher.EXTRA_DISPLAY_PATH).orEmpty()
        val baseDir = intent.getStringExtra(CodePreviewLauncher.EXTRA_BASE_DIR)
        baseUrl = baseDir?.let { directoryUrl(File(it)) }
        scriptsEnabled = kind == CodePreviewKind.HTML

        window.statusBarColor = CodePalette.PANEL
        val view = createWebView(kind)
        webView = view
        setContentView(buildContent(displayPath, view))

        val worker = Executors.newSingleThreadExecutor()
        executor = worker
        worker.execute { render(kind, source) }
    }

    override fun onDestroy() {
        executor?.shutdownNow()
        executor = null
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        if (isFinishing) CodePreviewLauncher.clear(CodePreviewLauncher.directory(this))
        super.onDestroy()
    }

    private fun isInsidePreviewDirectory(file: File): Boolean = try {
        file.canonicalFile.parentFile == CodePreviewLauncher.directory(this).canonicalFile
    } catch (e: IOException) {
        false
    }

    private fun directoryUrl(dir: File): String {
        val url = Uri.fromFile(dir).toString()
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun buildContent(displayPath: String, content: WebView): LinearLayout {
        val back = CodeUi.iconButton(this, R.drawable.ic_code_arrow_back, R.string.code_preview_back) { finish() }
        val title = TextView(this).apply {
            setText(R.string.code_preview_title)
            setTextColor(CodePalette.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            isSingleLine = true
        }
        val path = TextView(this).apply {
            text = displayPath
            setTextColor(CodePalette.TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(path)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(CodeUi.dp(this@CodePreviewActivity, 4), 0, CodeUi.dp(this@CodePreviewActivity, 12), 0)
            addView(back, LinearLayout.LayoutParams(CodeUi.dp(this@CodePreviewActivity, 48), CodeUi.dp(this@CodePreviewActivity, 56)))
            addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.BACKGROUND)
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    @Suppress("DEPRECATION")
    private fun createWebView(kind: CodePreviewKind): WebView = WebView(this).apply {
        setBackgroundColor(if (kind == CodePreviewKind.MARKDOWN) CodePalette.BACKGROUND else Color.WHITE)
        settings.apply {
            javaScriptEnabled = scriptsEnabled
            allowFileAccess = true
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webViewClient = PreviewClient()
    }

    /** Berjalan di thread latar: menyiapkan halaman, lalu memuatnya di thread utama. */
    private fun render(kind: CodePreviewKind, source: File) {
        val page = try {
            when (kind) {
                CodePreviewKind.SVG -> source
                else -> writePage(kind, source)
            }
        } catch (e: IOException) {
            fail(e.message ?: e.javaClass.simpleName)
            return
        } catch (e: RuntimeException) {
            fail(e.message ?: e.javaClass.simpleName)
            return
        } catch (e: OutOfMemoryError) {
            fail(getString(R.string.code_preview_too_large))
            return
        }
        val url = Uri.fromFile(page).toString()
        runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            pageUrl = url
            webView?.loadUrl(url)
        }
    }

    private fun writePage(kind: CodePreviewKind, source: File): File {
        if (source.length() > MAX_TEXT_BYTES) throw IOException(getString(R.string.code_preview_too_large))
        val text = source.readText(Charsets.UTF_8)
        val html = if (kind == CodePreviewKind.MARKDOWN) {
            CodePreviewRenderer.markdown(text, baseUrl)
        } else {
            CodePreviewRenderer.html(text, baseUrl)
        }
        val page = File(source.parentFile, PAGE_NAME)
        page.writeText(html, Charsets.UTF_8)
        return page
    }

    private fun fail(reason: String) {
        runOnUiThread {
            if (isDestroyed || isFinishing) return@runOnUiThread
            Toast.makeText(this, getString(R.string.code_preview_failed, reason), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private inner class PreviewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            val withoutFragment = target.substringBefore('#')
            val fragment = request.url.fragment
            when {
                withoutFragment == pageUrl -> return false
                fragment != null && withoutFragment == baseUrl && scriptsEnabled ->
                    view.evaluateJavascript("location.hash = " + JSONObject.quote(fragment) + ";", null)
                else -> Toast.makeText(this@CodePreviewActivity, R.string.code_preview_blocked, Toast.LENGTH_SHORT).show()
            }
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Toast.makeText(this@CodePreviewActivity, R.string.code_preview_renderer_gone, Toast.LENGTH_LONG).show()
            finish()
            return true
        }
    }

    private companion object {
        const val PAGE_NAME = "page.html"
        const val MAX_TEXT_BYTES = 8L * 1024 * 1024
    }
}
