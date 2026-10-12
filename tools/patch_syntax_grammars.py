import os, sys, json, re, time, urllib.request, urllib.error

TAG = "1.90.0"
RAW = "https://raw.githubusercontent.com/microsoft/vscode/%s/" % TAG
MAIN = os.path.join("features", "feature-code", "src", "main")
ASSETS = os.path.join(MAIN, "assets", "textmate")
LANG_JSON = os.path.join(ASSETS, "languages.json")
CT = os.path.join(MAIN, "kotlin", "com", "molina", "suite", "feature", "code", "CodeTextMate.kt")
ATTR = os.path.join("docs", "ATTRIBUTION.md")
LICENSE_OUT = os.path.join(ASSETS, "LICENSE-vscode-grammars.txt")

def die(msg):
    print("GAGAL: " + msg + " (tidak ada file yang diubah)")
    sys.exit(1)

def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()

def write(p, s):
    d = os.path.dirname(p)
    if d:
        os.makedirs(d, exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

for p in (LANG_JSON, CT):
    if not os.path.exists(p):
        die("jalankan dari /storage/emulated/0/molina-suite; tidak ada " + p)

GRAMMARS = [
    ("json", "source.json", "json/syntaxes/JSON.tmLanguage.json", "json/language-configuration.json"),
    ("yaml", "source.yaml", "yaml/syntaxes/yaml.tmLanguage.json", "yaml/language-configuration.json"),
    ("shell", "source.shell", "shellscript/syntaxes/shell-unix-bash.tmLanguage.json", "shellscript/language-configuration.json"),
    ("css", "source.css", "css/syntaxes/css.tmLanguage.json", "css/language-configuration.json"),
    ("c", "source.c", "cpp/syntaxes/c.tmLanguage.json", "cpp/language-configuration.json"),
    ("cpp", "source.cpp", "cpp/syntaxes/cpp.tmLanguage.json", "cpp/language-configuration.json"),
    ("sql", "source.sql", "sql/syntaxes/sql.tmLanguage.json", "sql/language-configuration.json"),
    ("typescript", "source.ts", "typescript-basics/syntaxes/TypeScript.tmLanguage.json", "typescript-basics/language-configuration.json"),
    ("ini", "source.ini", "ini/syntaxes/ini.tmLanguage.json", "ini/ini.language-configuration.json"),
    ("go", "source.go", "go/syntaxes/go.tmLanguage.json", "go/language-configuration.json"),
    ("rust", "source.rust", "rust/syntaxes/rust.tmLanguage.json", "rust/language-configuration.json"),
]

EXT_MAP = [
    ("json", ["json", "geojson", "webmanifest"]),
    ("yaml", ["yaml", "yml"]),
    ("shell", ["sh", "bash", "zsh", "ksh", "bashrc", "bash_profile", "profile", "zshrc"]),
    ("css", ["css"]),
    ("c", ["c", "h"]),
    ("cpp", ["cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino"]),
    ("sql", ["sql"]),
    ("typescript", ["ts", "mts", "cts"]),
    ("ini", ["ini", "properties", "cfg"]),
    ("go", ["go"]),
    ("rust", ["rs"]),
]

SAFE_CONFIG_KEYS = ("comments", "brackets", "autoClosingPairs", "surroundingPairs",
                    "autoCloseBefore", "wordPattern", "folding")

def jsonc_to_obj(text):
    out, i, n, in_str = [], 0, len(text), False
    while i < n:
        c = text[i]
        if in_str:
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(text[i + 1]); i += 2; continue
            if c == '"':
                in_str = False
            i += 1; continue
        if c == '"':
            in_str = True; out.append(c); i += 1; continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(c); i += 1
    s = "".join(out)
    res, in_str, i, n = [], False, 0, len(s)
    while i < n:
        c = s[i]
        if in_str:
            res.append(c)
            if c == "\\" and i + 1 < n:
                res.append(s[i + 1]); i += 2; continue
            if c == '"':
                in_str = False
            i += 1; continue
        if c == '"':
            in_str = True; res.append(c); i += 1; continue
        if c == ",":
            j = i + 1
            while j < n and s[j] in " \t\r\n":
                j += 1
            if j < n and s[j] in "}]":
                i += 1; continue
        res.append(c); i += 1
    return json.loads("".join(res))

cache = {}
def fetch(path):
    url = RAW + path
    if url in cache:
        return cache[url]
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "molina-suite-patch"})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read().decode("utf-8")
            cache[url] = data
            return data
        except (urllib.error.URLError, OSError) as e:
            last = e
            time.sleep(2)
    die("unduhan gagal: %s (%s). Cek koneksi; di Termux: pkg install python ca-certificates" % (url, last))

raw_lang = json.loads(read(LANG_JSON))
if isinstance(raw_lang, list):
    entries = raw_lang
elif isinstance(raw_lang, dict) and isinstance(raw_lang.get("languages"), list):
    entries = raw_lang["languages"]
else:
    die("format languages.json tidak dikenali")
if not entries or not isinstance(entries[0], dict):
    die("languages.json kosong; tidak ada contoh entri")

sample = entries[0]
g0 = str(sample.get("grammar", ""))
prefix = "textmate/" if g0.startswith("textmate/") else ""
registered = {e.get("scopeName") for e in entries if isinstance(e, dict)}
names = {e.get("name") for e in entries if isinstance(e, dict)}

def make_entry(name, scope, grammar_rel, config_rel):
    e = {}
    for k in sample.keys():
        if k == "name":
            e[k] = name
        elif k == "scopeName":
            e[k] = scope
        elif k == "grammar":
            e[k] = prefix + grammar_rel
        elif k == "languageConfiguration":
            e[k] = prefix + config_rel
    for k, v in (("name", name), ("scopeName", scope), ("grammar", prefix + grammar_rel),
                 ("languageConfiguration", prefix + config_rel)):
        e.setdefault(k, v)
    return e

outputs = {}
new_entries = []

for name, scope, gsrc, csrc in GRAMMARS:
    if scope in registered or name in names:
        continue
    gtext = fetch("extensions/" + gsrc)
    try:
        gobj = json.loads(gtext)
    except ValueError as e:
        die("grammar %s bukan JSON valid: %s" % (name, e))
    if gobj.get("scopeName") != scope:
        die("scopeName %s tidak cocok: %r" % (name, gobj.get("scopeName")))
    try:
        cobj = jsonc_to_obj(fetch("extensions/" + csrc))
    except ValueError as e:
        die("konfigurasi %s tidak terbaca: %s" % (name, e))
    cobj = {k: v for k, v in cobj.items() if k in SAFE_CONFIG_KEYS}
    gfile = "%s/syntaxes/%s.tmLanguage.json" % (name, name)
    cfile = "%s/language-configuration.json" % name
    outputs[os.path.join(ASSETS, *gfile.split("/"))] = gtext
    outputs[os.path.join(ASSETS, *cfile.split("/"))] = json.dumps(cobj, indent=2, ensure_ascii=False) + "\n"
    new_entries.append(make_entry(name, scope, gfile, cfile))

lua_dir = os.path.join(ASSETS, "lua", "syntaxes")
if "lua" not in names and os.path.isdir(lua_dir):
    for fn in sorted(os.listdir(lua_dir)):
        if fn.endswith(".json"):
            try:
                scope = json.loads(read(os.path.join(lua_dir, fn))).get("scopeName")
            except ValueError:
                continue
            if scope and scope not in registered:
                cfg = "lua/language-configuration.json"
                if not os.path.exists(os.path.join(ASSETS, "lua", "language-configuration.json")):
                    die("lua/language-configuration.json tidak ada")
                new_entries.append(make_entry("lua", scope, "lua/syntaxes/" + fn, cfg))
                break

if not new_entries:
    print("Tidak ada bahasa baru; patch ini sepertinya sudah pernah dijalankan.")
    sys.exit(0)

entries.extend(new_entries)
outputs[LANG_JSON] = json.dumps(raw_lang, indent=2, ensure_ascii=False) + "\n"

ct = read(CT)
m = re.search(r"val EXTENSION_LANGUAGE = mapOf\(.*?\n        \)", ct, re.S)
if not m or len(re.findall(r"val EXTENSION_LANGUAGE = mapOf\(", ct)) != 1:
    die("blok EXTENSION_LANGUAGE di CodeTextMate.kt tidak ditemukan/tidak unik")
existing = re.findall(r'"([^"]+)"\s+to\s+"([^"]+)"', m.group(0))
mapping = {}
for ext, lang in existing:
    mapping[ext] = lang
for lang, exts in EXT_MAP:
    for ext in exts:
        mapping.setdefault(ext, lang)
body = ",\n".join('            "%s" to "%s"' % (e, l) for e, l in mapping.items())
ct_new = ct[:m.start()] + "val EXTENSION_LANGUAGE = mapOf(\n" + body + "\n        )" + ct[m.end():]
outputs[CT] = ct_new

outputs[LICENSE_OUT] = ("Grammar TextMate dan language-configuration di folder ini (json, yaml, shell, css, c, cpp,\n"
                        "sql, typescript, ini, go, rust) berasal dari repositori resmi microsoft/vscode, tag %s.\n"
                        "Lisensi aslinya (MIT) disertakan di bawah ini tanpa perubahan.\n\n" % TAG
                        ) + fetch("LICENSE.txt")
if os.path.exists(ATTR):
    a = read(ATTR)
    if "microsoft/vscode" not in a:
        outputs[ATTR] = a.rstrip("\n") + (
            "\n\n## Grammar TextMate (microsoft/vscode)\n\n"
            "Grammar penyorotan sintaks di features/feature-code/src/main/assets/textmate (json, yaml, shell, css, c, cpp, "
            "sql, typescript, ini, go, rust) diambil dari https://github.com/microsoft/vscode tag %s, lisensi MIT. "
            "Teks lisensi ada di assets/textmate/LICENSE-vscode-grammars.txt.\n" % TAG)

for p, content in outputs.items():
    write(p, content)
print("OK: %d bahasa baru didaftarkan (%s)" % (len(new_entries), ", ".join(e["name"] for e in new_entries)))
print("OK: %d berkas ditulis" % len(outputs))
