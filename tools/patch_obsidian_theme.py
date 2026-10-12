import os, sys, json, re

MAIN = os.path.join("features", "feature-code", "src", "main")
ASSETS = os.path.join(MAIN, "assets", "textmate")
KT = os.path.join(MAIN, "kotlin", "com", "molina", "suite", "feature", "code")
STRINGS = os.path.join(MAIN, "res", "values", "strings.xml")
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

def k(name):
    return os.path.join(KT, name)

need = ["CodeTextMate.kt", "CodeEditorSession.kt", "CodeAppearance.kt", "CodeAppearanceDialog.kt", "CodeEditorFactory.kt"]
for n in need:
    if not os.path.exists(k(n)):
        die("tidak ada " + n + "; jalankan dari /storage/emulated/0/molina-suite")
for p in (os.path.join(ASSETS, "darcula.json"), STRINGS):
    if not os.path.exists(p):
        die("tidak ada " + p)
if "CodeThemeState" in read(k("CodeAppearance.kt")):
    die("patch tema sudah pernah dijalankan")

# 1. obsidian.json diturunkan dari darcula.json (struktur aturan sama, palet diganti)
COLORS = {"#242424": "#293134", "#2b2b2b": "#2F393C", "#214283": "#3B5560",
          "#cccccc": "#E0E2E4", "#707070": "#7D8C93", "#cc8242": "#93C763",
          "#6a8759": "#EC7600", "#7a9ec2": "#678CB1", "#ffc66d": "#E8E2B7",
          "#9e7bb0": "#A082BD", "#a5c261": "#B3D98A"}
theme = json.loads(read(os.path.join(ASSETS, "darcula.json")))
rules = theme.get("settings")
if not isinstance(rules, list):
    die("format darcula.json tidak dikenali")
for rule in rules:
    st = rule.get("settings", {})
    scope = rule.get("scope", "")
    text = scope if isinstance(scope, str) else ",".join(scope)
    for key in ("background", "foreground", "lineHighlight", "selection"):
        if key in st:
            st[key] = COLORS.get(str(st[key]).lower(), st[key])
    if "constant.numeric" in text:
        st["foreground"] = "#FFCD22"
    if "comment.block.documentation" in text:
        st["foreground"] = "#8A9BA2"
theme["name"] = "obsidian"
outputs = {os.path.join(ASSETS, "obsidian.json"): json.dumps(theme, indent=2, ensure_ascii=False) + "\n"}

# 2. berkas Kotlin yang ditulis ulang penuh (dibuat oleh patch 2A)
outputs[k("CodeAppearance.kt")] = PKG + r'''import android.content.Context

/** Gaya tampilan editor yang disimpan permanen: ukuran font, nomor baris, dan tema warna. */
internal data class CodeAppearance(
    val textSizeSp: Int = DEFAULT_SP,
    val lineNumbers: Boolean = true,
    val theme: String = THEME_DARCULA
) {
    companion object {
        const val MIN_SP = 10
        const val MAX_SP = 28
        const val DEFAULT_SP = 14
        const val THEME_DARCULA = "darcula"
        const val THEME_OBSIDIAN = "obsidian"
        val THEMES = listOf(THEME_DARCULA, THEME_OBSIDIAN)
    }
}

/** Tema warna yang sedang aktif; dibaca CodeTextMate saat membuat skema warna. */
internal object CodeThemeState {
    @Volatile
    var name: String = CodeAppearance.THEME_DARCULA
}

internal class CodeAppearanceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): CodeAppearance {
        val saved = prefs.getString(KEY_THEME, CodeAppearance.THEME_DARCULA)
        return CodeAppearance(
            textSizeSp = prefs.getInt(KEY_SIZE, CodeAppearance.DEFAULT_SP)
                .coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP),
            lineNumbers = prefs.getBoolean(KEY_LINES, true),
            theme = if (saved != null && saved in CodeAppearance.THEMES) saved else CodeAppearance.THEME_DARCULA
        )
    }

    fun save(value: CodeAppearance) {
        prefs.edit()
            .putInt(KEY_SIZE, value.textSizeSp.coerceIn(CodeAppearance.MIN_SP, CodeAppearance.MAX_SP))
            .putBoolean(KEY_LINES, value.lineNumbers)
            .putString(KEY_THEME, value.theme)
            .apply()
    }

    private companion object {
        const val PREFS = "molina_code_appearance"
        const val KEY_SIZE = "text_size_sp"
        const val KEY_LINES = "line_numbers"
        const val KEY_THEME = "theme"
    }
}
'''

outputs[k("CodeEditorFactory.kt")] = PKG + r'''import android.content.Context
import android.graphics.Typeface
import io.github.rosemoe.sora.widget.CodeEditor

/** Pembuat [CodeEditor] dengan pengaturan dasar tab Code. */
internal object CodeEditorFactory {

    fun create(context: Context): CodeEditor {
        val appearance = CodeAppearanceStore(context).load()
        CodeThemeState.name = appearance.theme
        return CodeEditor(context).apply {
            setTypefaceText(Typeface.MONOSPACE)
            setTextSize(appearance.textSizeSp.toFloat())
            setLineNumberEnabled(appearance.lineNumbers)
            setWordwrap(true)
        }
    }
}
'''

outputs[k("CodeAppearanceDialog.kt")] = PKG + r'''import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

/** Dialog Gaya Visual: ukuran font, nomor baris, dan tema warna. */
internal object CodeAppearanceDialog {

    fun show(context: Context, current: CodeAppearance, onApply: (CodeAppearance) -> Unit) {
        val pad = CodeUi.dp(context, 20)
        val min = CodeAppearance.MIN_SP
        val label = TextView(context)
        val seek = SeekBar(context).apply {
            max = CodeAppearance.MAX_SP - min
            progress = (current.textSizeSp - min).coerceIn(0, CodeAppearance.MAX_SP - min)
        }
        fun render() {
            label.text = context.getString(R.string.code_appearance_size, min + seek.progress)
        }
        render()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = render()
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        val lines = CheckBox(context).apply {
            setText(R.string.code_appearance_line_numbers)
            isChecked = current.lineNumbers
        }
        val themeLabel = TextView(context).apply {
            setText(R.string.code_appearance_theme)
            setPadding(0, pad / 2, 0, 0)
        }
        val choices = listOf(
            CodeAppearance.THEME_DARCULA to context.getString(R.string.code_theme_darcula),
            CodeAppearance.THEME_OBSIDIAN to context.getString(R.string.code_theme_obsidian)
        )
        val group = RadioGroup(context)
        val buttons = choices.map { (id, title) ->
            RadioButton(context).apply {
                text = title
                tag = id
                setId(View.generateViewId())
                isChecked = id == current.theme
            }.also { group.addView(it) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(seek)
            addView(lines)
            addView(themeLabel)
            addView(group)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_menu_appearance)
            .setView(content)
            .setPositiveButton(R.string.code_appearance_apply) { _, _ ->
                val chosen = buttons.firstOrNull { it.isChecked }?.tag as? String ?: current.theme
                onApply(
                    CodeAppearance(
                        textSizeSp = min + seek.progress,
                        lineNumbers = lines.isChecked,
                        theme = chosen
                    )
                )
            }
            .setNegativeButton(R.string.code_picker_cancel, null)
            .show()
    }
}
'''

# 3. CodeTextMate.kt: muat tema kedua, pilih tema aktif, tambah ekstensi
ct = read(k("CodeTextMate.kt"))
m = re.search(r"    private fun loadTheme\(\) \{.*?\n    \}\n", ct, re.S)
if not m or ct.count("private fun loadTheme()") != 1:
    die("fungsi loadTheme di CodeTextMate.kt tidak ditemukan/tidak unik")
new_load = r'''    private fun loadTheme() {
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
        } catch (e: Exception) {
            Log.w(TAG, "Tema ${CodeThemeState.name} gagal dipakai", e)
            try {
                registry.setTheme(THEME_NAME)
            } catch (fallback: Exception) {
                Log.w(TAG, "Tema bawaan gagal dipakai", fallback)
            }
        }
    }
'''
ct = ct[:m.start()] + new_load + ct[m.end():]
a = "        val scheme = try {\n            TextMateColorScheme.create(ThemeRegistry.getInstance())"
if ct.count(a) != 1:
    die("jangkar pembuatan skema warna di CodeTextMate.kt tidak unik")
ct = ct.replace(a, "        applyTheme()\n" + a, 1)
mm = re.search(r"val EXTENSION_LANGUAGE = mapOf\(.*?\n        \)", ct, re.S)
if not mm or ct.count("val EXTENSION_LANGUAGE = mapOf(") != 1:
    die("blok EXTENSION_LANGUAGE tidak ditemukan/tidak unik")
mapping = dict(re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', mm.group(0)))
for ext, lang in (("toml", "ini"), ("editorconfig", "ini"), ("gitconfig", "ini"), ("conf", "ini"), ("jsonc", "json")):
    mapping.setdefault(ext, lang)
body = ",\n".join('            "%s" to "%s"' % (e, l) for e, l in mapping.items())
ct = ct[:mm.start()] + "val EXTENSION_LANGUAGE = mapOf(\n" + body + "\n        )" + ct[mm.end():]
outputs[k("CodeTextMate.kt")] = ct

# 4. CodeEditorSession.kt: terapkan tema saat Gaya Visual diubah
ses = read(k("CodeEditorSession.kt"))
b = "target.setLineNumberEnabled(appearance.lineNumbers)"
if ses.count(b) != 1:
    die("jangkar applyAppearance di CodeEditorSession.kt tidak unik")
outputs[k("CodeEditorSession.kt")] = ses.replace(
    b, b + "\n        CodeThemeState.name = appearance.theme\n        textMate.applyTheme()", 1)

# 5. strings
st = read(STRINGS)
if "code_theme_obsidian" in st or st.rfind("</resources>") < 0:
    die("strings.xml tidak sesuai harapan")
idx = st.rfind("</resources>")
block = '''
    <string name="code_appearance_theme">Tema warna</string>
    <string name="code_theme_darcula">Bawaan (Darcula)</string>
    <string name="code_theme_obsidian">Obsidian</string>
'''
outputs[STRINGS] = st[:idx].rstrip("\n") + "\n" + block + st[idx:]

for p, content in outputs.items():
    write(p, content)
print("OK: tema Obsidian ditambahkan (%d berkas ditulis)" % len(outputs))
