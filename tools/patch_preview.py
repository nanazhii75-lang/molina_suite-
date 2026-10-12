import os, sys

MAIN = os.path.join("features", "feature-code", "src", "main")
KT = os.path.join(MAIN, "kotlin", "com", "molina", "suite", "feature", "code")
MANIFEST = os.path.join(MAIN, "AndroidManifest.xml")
STRINGS = os.path.join(MAIN, "res", "values", "strings.xml")
GRADLE = os.path.join("features", "feature-code", "build.gradle.kts")
FRAGMENT = os.path.join(KT, "CodeTabFragment.kt")
PKG = "package com.molina.suite.feature.code\n\n"


def die(msg):
    print("GAGAL: " + msg + " (tidak ada file yang diubah)")
    sys.exit(1)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)


for p in (MANIFEST, STRINGS, GRADLE, FRAGMENT):
    if not os.path.exists(p):
        die("tidak ada " + p + "; jalankan dari /storage/emulated/0/molina-suite")

NEW_FILES = {}

NEW_FILES["CodePreviewKind.kt"] = PKG + r'''import java.util.Locale

/** Jenis berkas yang bisa ditinjau di halaman Tinjauan, dikenali dari ekstensi nama berkas. */
internal enum class CodePreviewKind(val sourceExtension: String) {
    MARKDOWN("md"),
    HTML("html"),
    SVG("svg");

    companion object {
        private val BY_EXTENSION = mapOf(
            "md" to MARKDOWN,
            "markdown" to MARKDOWN,
            "mdx" to MARKDOWN,
            "html" to HTML,
            "htm" to HTML,
            "xhtml" to HTML,
            "svg" to SVG
        )

        /** Jenis Tinjauan untuk [fileName]; null bila ekstensinya tidak bisa ditinjau. */
        fun of(fileName: String): CodePreviewKind? {
            val extension = CodeExtensions.extensionOf(fileName) ?: return null
            return BY_EXTENSION[extension.lowercase(Locale.ROOT)]
        }

        fun fromName(name: String?): CodePreviewKind? = values().firstOrNull { it.name == name }
    }
}
'''

NEW_FILES["CodePreviewRenderer.kt"] = PKG + r'''import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Menyusun halaman HTML untuk Tinjauan. Markdown dirender dengan CommonMark: HTML mentah di
 * dalam Markdown di-escape dan URL berbahaya dibersihkan. Berkas HTML dipakai apa adanya;
 * bila [baseUrl] ada, tag base disisipkan supaya gambar dan CSS relatif ikut terbaca.
 */
internal object CodePreviewRenderer {

    private val EXTENSIONS: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create()
    )

    private val parser: Parser = Parser.builder().extensions(EXTENSIONS).build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(EXTENSIONS)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .build()

    private val HEAD_TAG = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val HTML_TAG = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
    private val BASE_TAG = Regex("<base\\s", RegexOption.IGNORE_CASE)

    private const val STYLE = """
        :root { color-scheme: dark; }
        body { background: #2B2B2B; color: #A9B7C6; margin: 0; padding: 16px;
               font-family: sans-serif; line-height: 1.55; word-wrap: break-word; }
        h1, h2, h3, h4, h5, h6 { color: #FFC66D; line-height: 1.25; }
        h1, h2 { border-bottom: 1px solid #4A4A4A; padding-bottom: 0.3em; }
        a { color: #7FB8E8; }
        code { background: #313335; color: #CC8242; padding: 0.1em 0.35em; border-radius: 4px;
               font-family: monospace; font-size: 0.92em; }
        pre { background: #313335; padding: 12px; border-radius: 6px; overflow-x: auto; }
        pre code { background: none; color: #A9B7C6; padding: 0; }
        blockquote { margin: 0; padding: 0 1em; color: #808080; border-left: 4px solid #8D5A2B; }
        table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; }
        th, td { border: 1px solid #4A4A4A; padding: 6px 10px; }
        th { background: #313335; }
        img { max-width: 100%; height: auto; }
        hr { border: 0; border-top: 1px solid #4A4A4A; }
    """

    fun markdown(source: String, baseUrl: String?): String {
        val body = renderer.render(parser.parse(source))
        val base = if (baseUrl != null) "<base href=\"" + escapeAttribute(baseUrl) + "\">" else ""
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            base + "<style>" + STYLE + "</style></head><body>" + body + "</body></html>"
    }

    fun html(source: String, baseUrl: String?): String {
        if (baseUrl == null || BASE_TAG.containsMatchIn(source)) return source
        val tag = "<base href=\"" + escapeAttribute(baseUrl) + "\">"
        val head = HEAD_TAG.find(source)
        if (head != null) return source.substring(0, head.range.last + 1) + tag + source.substring(head.range.last + 1)
        val root = HTML_TAG.find(source)
        if (root != null) {
            return source.substring(0, root.range.last + 1) + "<head>" + tag + "</head>" +
                source.substring(root.range.last + 1)
        }
        return tag + source
    }

    private fun escapeAttribute(value: String): String =
        value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
'''

NEW_FILES["CodePreviewLauncher.kt"] = PKG + r'''import android.content.Context
import android.content.Intent
import java.io.File
import java.io.IOException

/**
 * Menyiapkan Tinjauan: isi editor disalin ke cache aplikasi (bukan lewat Intent, supaya aman untuk
 * berkas besar), lalu Intent untuk [CodePreviewActivity] disusun. Panggil dari Dispatchers.IO.
 */
internal object CodePreviewLauncher {

    const val EXTRA_SOURCE = "com.molina.suite.feature.code.preview.SOURCE"
    const val EXTRA_KIND = "com.molina.suite.feature.code.preview.KIND"
    const val EXTRA_DISPLAY_PATH = "com.molina.suite.feature.code.preview.DISPLAY_PATH"
    const val EXTRA_BASE_DIR = "com.molina.suite.feature.code.preview.BASE_DIR"

    private const val DIRECTORY_NAME = "code-preview"

    /** Folder cache Tinjauan; hanya berkas di dalamnya yang boleh dimuat oleh Activity. */
    fun directory(context: Context): File = File(context.cacheDir, DIRECTORY_NAME)

    /** Intent Tinjauan untuk [file] dengan isi [text]; null bila cache tidak bisa ditulis. */
    fun prepare(context: Context, kind: CodePreviewKind, file: File, text: String): Intent? {
        val dir = directory(context)
        return try {
            clear(dir)
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val source = File(dir, "source." + kind.sourceExtension)
            source.writeText(text, Charsets.UTF_8)
            Intent(context, CodePreviewActivity::class.java)
                .putExtra(EXTRA_SOURCE, source.absolutePath)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_DISPLAY_PATH, file.absolutePath)
                .putExtra(EXTRA_BASE_DIR, file.parentFile?.absolutePath)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Menghapus semua berkas sementara Tinjauan. */
    fun clear(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }
}
'''

NEW_FILES["CodePreviewActivity.kt"] = PKG + r'''import android.app.Activity
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
'''

MANIFEST_NEW = '''    <application>
        <activity
            android:name=".CodePreviewActivity"
            android:exported="false"
            android:label="@string/code_preview_title"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboard|keyboardHidden"
            android:theme="@android:style/Theme.Material.NoActionBar" />
    </application>
'''

STRINGS_NEW = '''    <string name="code_preview_title">Tinjauan</string>
    <string name="code_preview_back">Kembali</string>
    <string name="code_preview_unavailable">Tinjauan tidak bisa dibuka.</string>
    <string name="code_preview_failed">Gagal menyiapkan Tinjauan: %1$s</string>
    <string name="code_preview_too_large">Berkas terlalu besar untuk ditinjau (maksimal 8 MB).</string>
    <string name="code_preview_blocked">Tautan di Tinjauan tidak dibuka.</string>
    <string name="code_preview_renderer_gone">Tinjauan berhenti karena WebView bermasalah.</string>
    <string name="code_preview_cache_failed">Gagal menyiapkan berkas Tinjauan.</string>
'''

DEP_ANCHOR = '    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")\n'
DEP_NEW = (
    '    implementation("org.commonmark:commonmark:0.22.0")\n'
    '    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")\n'
    '    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")\n'
)

OLD_RUN = '''    /** Jalankan: simpan dulu bila perlu, lalu jalankan di Debian lewat tab Terminal. */
    private fun runCurrent() {
        val doc = session.document.value
        val file = doc.file
        when {
            file == null -> requestSaveAs {
                session.document.value.file?.let { runFile(it, ScriptRunTarget.DEBIAN) }
            }
            doc.dirty -> viewLifecycleOwner.lifecycleScope.launch {
'''
NEW_RUN = '''    /**
     * Jalankan: berkas md, html, dan svg dibuka di Tinjauan (isi editor, tanpa menyimpan); selain
     * itu simpan dulu bila perlu, lalu jalankan di Debian lewat tab Terminal.
     */
    private fun runCurrent() {
        val doc = session.document.value
        val file = doc.file
        when {
            file == null -> requestSaveAs {
                session.document.value.file?.let { dispatchRun(it) }
            }
            CodePreviewKind.of(file.name) != null -> openPreview(file)
            doc.dirty -> viewLifecycleOwner.lifecycleScope.launch {
'''
OLD_RUNFILE = '''    private fun runFile(file: File, target: ScriptRunTarget) {
'''
NEW_RUNFILE = '''    private fun dispatchRun(file: File) {
        if (CodePreviewKind.of(file.name) != null) openPreview(file) else runFile(file, ScriptRunTarget.DEBIAN)
    }

    private fun openPreview(file: File) {
        val kind = CodePreviewKind.of(file.name) ?: return
        val context = requireContext()
        val text = session.currentText()
        viewLifecycleOwner.lifecycleScope.launch {
            val intent = withContext(Dispatchers.IO) { CodePreviewLauncher.prepare(context, kind, file, text) }
            if (intent == null) toast(getString(R.string.code_preview_cache_failed)) else startActivity(intent)
        }
    }

    private fun runFile(file: File, target: ScriptRunTarget) {
'''

changed = []


def put(path, content):
    if os.path.exists(path) and read(path) == content:
        return
    write(path, content)
    changed.append(path)


for name, content in NEW_FILES.items():
    put(os.path.join(KT, name), content)

manifest = read(MANIFEST)
if "CodePreviewActivity" not in manifest:
    if "<application" in manifest:
        die("AndroidManifest feature-code sudah punya <application>; sesuaikan manual")
    if "</manifest>" not in manifest:
        die("format AndroidManifest feature-code tidak dikenali")
    put(MANIFEST, manifest.replace("</manifest>", MANIFEST_NEW + "</manifest>", 1))

strings = read(STRINGS)
if "code_preview_title" not in strings:
    if "</resources>" not in strings:
        die("format strings.xml tidak dikenali")
    put(STRINGS, strings.replace("</resources>", STRINGS_NEW + "</resources>", 1))

gradle = read(GRADLE)
if "org.commonmark" not in gradle:
    if DEP_ANCHOR not in gradle:
        die("jangkar dependensi build.gradle.kts feature-code tidak ditemukan")
    put(GRADLE, gradle.replace(DEP_ANCHOR, DEP_ANCHOR + DEP_NEW, 1))

fragment = read(FRAGMENT)
if "openPreview" not in fragment:
    if OLD_RUN not in fragment or OLD_RUNFILE not in fragment:
        die("blok runCurrent di CodeTabFragment.kt tidak cocok; jalankan git pull dulu")
    fragment = fragment.replace(OLD_RUN, NEW_RUN, 1).replace(OLD_RUNFILE, NEW_RUNFILE, 1)
    put(FRAGMENT, fragment)

print("selesai: %d berkas ditulis" % len(changed))
for p in changed:
    print("  " + p)
