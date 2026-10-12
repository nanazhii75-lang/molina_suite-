import os, sys

GRADLE = os.path.join("features", "feature-code", "build.gradle.kts")
RENDERER = os.path.join(
    "features", "feature-code", "src", "main", "kotlin", "com", "molina", "suite",
    "feature", "code", "CodePreviewRenderer.kt",
)

OLD_DEPS = (
    '    implementation("org.commonmark:commonmark:0.22.0")\n'
    '    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")\n'
    '    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")\n'
)
NEW_DEPS = (
    '    implementation("com.atlassian.commonmark:commonmark:0.13.0")\n'
    '    implementation("com.atlassian.commonmark:commonmark-ext-gfm-tables:0.13.0")\n'
    '    implementation("com.atlassian.commonmark:commonmark-ext-gfm-strikethrough:0.13.0")\n'
)
OLD_SANITIZE = "        .sanitizeUrls(true)\n"
OLD_DOC = (
    " * Menyusun halaman HTML untuk Tinjauan. Markdown dirender dengan CommonMark: HTML mentah di\n"
    " * dalam Markdown di-escape dan URL berbahaya dibersihkan. Berkas HTML dipakai apa adanya;\n"
)
NEW_DOC = (
    " * Menyusun halaman HTML untuk Tinjauan. Markdown dirender dengan CommonMark 0.13.0 (versi yang\n"
    " * sama dengan Markwon di engine Terminal): HTML mentah di dalam Markdown di-escape. Tautan\n"
    " * tidak berbahaya karena JavaScript mati di Markdown dan navigasi diblokir. Berkas HTML dipakai apa adanya;\n"
)


def die(msg):
    print("GAGAL: " + msg + " (tidak ada file yang diubah)")
    sys.exit(1)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)


for p in (GRADLE, RENDERER):
    if not os.path.exists(p):
        die("tidak ada " + p + "; jalankan dari /storage/emulated/0/molina-suite setelah git pull")

changed = []

gradle = read(GRADLE)
if OLD_DEPS in gradle:
    write(GRADLE, gradle.replace(OLD_DEPS, NEW_DEPS, 1))
    changed.append(GRADLE)
elif "com.atlassian.commonmark:commonmark:0.13.0" not in gradle:
    die("baris dependensi commonmark di build.gradle.kts tidak dikenali")

renderer = read(RENDERER)
updated = renderer
if OLD_SANITIZE in updated:
    updated = updated.replace(OLD_SANITIZE, "", 1)
if OLD_DOC in updated:
    updated = updated.replace(OLD_DOC, NEW_DOC, 1)
if "sanitizeUrls" in updated:
    die("sanitizeUrls masih ada di CodePreviewRenderer.kt; periksa manual")
if updated != renderer:
    write(RENDERER, updated)
    changed.append(RENDERER)

print("selesai: %d berkas ditulis" % len(changed))
for p in changed:
    print("  " + p)
