package com.molina.suite.feature.code

import android.content.res.AssetManager
import android.util.Log
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.util.Locale

/** Skema warna dan bahasa untuk satu editor; null berarti teks polos. */
internal class CodeHighlighting(val scheme: EditorColorScheme?, val language: Language?)

/**
 * Inisialisasi TextMate sekali per proses (grammar dan tema dari assets/textmate)
 * dan pemetaan ekstensi berkas ke bahasa. Bila inisialisasi gagal, editor tetap
 * jalan sebagai teks polos.
 */
internal class CodeTextMate(private val assets: AssetManager) {

    private val lock = Any()

    @Volatile
    private var initialized = false

    @Volatile
    private var failure: Throwable? = null

    @Volatile
    private var scopes: Map<String, String> = emptyMap()

    /** Boleh dipanggil dari thread latar belakang. */
    fun warmUp() {
        ensureReady()
    }

    /** Buat skema dan bahasa baru untuk satu editor; panggil dari thread utama. */
    fun prepare(fileName: String?): CodeHighlighting {
        if (!ensureReady()) return CodeHighlighting(null, null)
        applyTheme()
        val scheme = try {
            TextMateColorScheme.create(ThemeRegistry.getInstance())
        } catch (e: Throwable) {
            Log.w(TAG, "Skema warna TextMate gagal dibuat", e)
            return CodeHighlighting(null, null)
        }
        val scope = fileName?.let { scopeFor(it) }
        Log.i(TAG, "prepare file=$fileName scope=$scope")
        val language = scope?.let {
            try {
                TextMateLanguage.create(it, false)
            } catch (e: Throwable) {
                Log.w(TAG, "Bahasa $it gagal dimuat", e)
                null
            }
        }
        return CodeHighlighting(scheme, language)
    }

    private fun ensureReady(): Boolean {
        if (initialized) return true
        if (failure != null) return false
        synchronized(lock) {
            if (initialized) return true
            if (failure != null) return false
            try {
                FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
                GrammarRegistry.getInstance().loadGrammars(LANGUAGES_ASSET)
                loadTheme()
                scopes = readScopeNames()
                Log.i(TAG, "TextMate siap: ${scopes.size} bahasa")
                initialized = true
            } catch (e: Throwable) {
                failure = e
                Log.e(TAG, "Inisialisasi TextMate gagal; editor tanpa penyorotan sintaks", e)
            }
            return initialized
        }
    }

    private fun loadTheme() {
        loadThemeAsset(THEME_NAME, required = true)
        loadThemeAsset(CodeAppearance.THEME_OBSIDIAN, required = false)
        ThemeRegistry.getInstance().setTheme(THEME_NAME)
    }

    private fun loadThemeAsset(name: String, required: Boolean) {
        val path = "$ASSET_DIR/$name.json"
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
        if (stream == null) {
            if (required) throw IOException("Aset tema tidak ditemukan: $path")
            Log.w(TAG, "Aset tema opsional tidak ditemukan: $path")
            return
        }
        ThemeRegistry.getInstance().loadTheme(
            ThemeModel(IThemeSource.fromInputStream(stream, path, null), name).apply {
                isDark = true
            }
        )
    }

    /** Pakai tema pilihan Gaya Visual; kembali ke tema bawaan bila gagal. Panggil dari thread utama. */
    fun applyTheme() {
        if (!ensureReady()) return
        val registry = ThemeRegistry.getInstance()
        try {
            registry.setTheme(CodeThemeState.name)
        } catch (e: Throwable) {
            Log.w(TAG, "Tema ${CodeThemeState.name} gagal dipakai", e)
            try {
                registry.setTheme(THEME_NAME)
            } catch (fallback: Throwable) {
                Log.w(TAG, "Tema bawaan gagal dipakai", fallback)
            }
        }
    }

    private fun readScopeNames(): Map<String, String> {
        val raw = assets.open(LANGUAGES_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array: JSONArray = when (val root = JSONTokener(raw).nextValue()) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("languages")
            else -> null
        } ?: throw IOException("Format $LANGUAGES_ASSET tidak dikenali")
        val result = HashMap<String, String>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val name = entry.optString("name")
            val scope = entry.optString("scopeName")
            if (name.isNotEmpty() && scope.isNotEmpty()) result[name] = scope
        }
        return result
    }

    /** Nama bahasa yang tersedia; kosong bila TextMate gagal diinisialisasi. */
    fun languageNames(): List<String> {
        if (!ensureReady()) return emptyList()
        return scopes.keys.sorted()
    }

    /** Buat bahasa berdasarkan nama; panggil dari thread utama. Null bila gagal. */
    fun prepareByName(name: String): Language? {
        if (!ensureReady()) return null
        val scope = scopes[name] ?: return null
        return try {
            TextMateLanguage.create(scope, false)
        } catch (e: Throwable) {
            Log.w(TAG, "Bahasa $scope gagal dimuat", e)
            null
        }
    }

    private fun scopeFor(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val language = EXTENSION_LANGUAGE[extension] ?: return null
        return scopes[language]
    }

    private companion object {
        const val TAG = "CodeTextMate"
        const val ASSET_DIR = "textmate"
        const val LANGUAGES_ASSET = "textmate/languages.json"
        const val THEME_NAME = "darcula"

        val EXTENSION_LANGUAGE = mapOf(
            "java" to "java",
            "kt" to "kotlin",
            "kts" to "kotlin",
            "py" to "python",
            "js" to "javascript",
            "mjs" to "javascript",
            "cjs" to "javascript",
            "html" to "html",
            "htm" to "html",
            "xml" to "xml",
            "svg" to "xml",
            "md" to "markdown",
            "markdown" to "markdown",
            "lua" to "lua",
            "json" to "json",
            "geojson" to "json",
            "webmanifest" to "json",
            "yaml" to "yaml",
            "yml" to "yaml",
            "sh" to "shell",
            "bash" to "shell",
            "zsh" to "shell",
            "ksh" to "shell",
            "bashrc" to "shell",
            "bash_profile" to "shell",
            "profile" to "shell",
            "zshrc" to "shell",
            "css" to "css",
            "c" to "c",
            "h" to "c",
            "cpp" to "cpp",
            "cc" to "cpp",
            "cxx" to "cpp",
            "hpp" to "cpp",
            "hh" to "cpp",
            "hxx" to "cpp",
            "ino" to "cpp",
            "sql" to "sql",
            "ts" to "typescript",
            "mts" to "typescript",
            "cts" to "typescript",
            "ini" to "ini",
            "properties" to "ini",
            "cfg" to "ini",
            "go" to "go",
            "rs" to "rust",
            "toml" to "ini",
            "editorconfig" to "ini",
            "gitconfig" to "ini",
            "conf" to "ini",
            "jsonc" to "json"
        )
    }
}
