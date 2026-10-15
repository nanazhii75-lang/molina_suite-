#!/usr/bin/env python3
"""Patch: entri Library "Cek kompilasi Kotlin (project)" memakai gradlew project dengan
task yang bisa diganti ({TASK}, default compileDebugKotlin), bukan kotlinc langsung.
Entri lama yang belum diubah pengguna diperbarui otomatis. Idempotent. Jalankan dari akar repo."""
import os
import sys

ROOT = os.getcwd()
KT = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/library"
RES = "features/feature-settings/src/main/res/values/strings.xml"
changed = []


def read(rel):
    p = os.path.join(ROOT, rel)
    if not os.path.isfile(p):
        sys.exit("GAGAL: berkas tidak ditemukan: " + rel + " (jalankan dari akar repo, git pull dulu)")
    with open(p, encoding="utf-8") as f:
        return p, f.read()


def save(p, rel, text):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    changed.append(rel)
    print("diubah:", rel)


def edit(rel, marker, old, new):
    p, text = read(rel)
    if marker in text:
        print("lewati (sudah ada):", rel, "->", marker)
        return
    if text.count(old) != 1:
        sys.exit("GAGAL: pola tidak unik/tidak ketemu di " + rel + ":\n" + old)
    save(p, rel, text.replace(old, new))


# 1. KotlinCheckLibrary.kt: skrip lama jadi legacyScript, skrip baru lewat gradlew.
rel = KT + "/KotlinCheckLibrary.kt"
p, text = read(rel)
if "legacyScript" in text:
    print("lewati (sudah ada):", rel)
else:
    parts = text.split('"""')
    if len(parts) < 3:
        sys.exit("GAGAL: skrip lama tidak ditemukan di KotlinCheckLibrary.kt")
    old_script = parts[1]
    D = "\u00A7"
    new_script = (
        "\n# Kompilasi project lewat Gradle wrapper milik project. Ganti TASK sesuai kebutuhan\n"
        "# (mis. compileDebugKotlin, assembleDebug, atau beberapa task dipisah spasi).\n"
        "DIR={DIR}\n"
        "TASK={TASK}\n"
        'cd "' + D + 'DIR" 2>/dev/null || { echo "GAGAL: folder tidak ditemukan: ' + D + 'DIR"; exit 1; }\n'
        '[ -f gradlew ] || { echo "GAGAL: gradlew tidak ada di ' + D + 'DIR"; exit 1; }\n'
        'command -v java >/dev/null 2>&1 || { echo "GAGAL: Java belum terpasang. Pasang JDK 17 di Setting, menu Paket."; exit 1; }\n'
        'if [ -z "' + D + '{ANDROID_HOME:-}' + D + '{ANDROID_SDK_ROOT:-}" ] && ! grep -qs "^sdk.dir" local.properties; then\n'
        '  echo "Catatan: ANDROID_HOME dan sdk.dir di local.properties tidak ada; Gradle mungkin tidak menemukan Android SDK."\n'
        "fi\n"
        'echo "Menjalankan: ./gradlew ' + D + 'TASK di ' + D + 'DIR"\n'
        "bash ./gradlew " + D + "TASK --console=plain --no-daemon 2>&1\n"
        "RC=" + D + "?\n"
        'if [ "' + D + 'RC" -eq 0 ]; then echo "SELESAI: Gradle berhasil."; else echo "SELESAI: Gradle gagal (kode ' + D + 'RC)."; fi\n'
        "exit " + D + "RC\n"
    )
    head = parts[0]
    start = head.index("    private val script: String =")
    prefix = head[:start]
    tail = parts[2]
    tail = tail.replace(
        'note = "Isi DIR dengan folder project (mis. /sdcard/molina-suite). Semua berkas .kt di dalamnya " +\n'
        '            "diperiksa dengan kotlinc, tanpa Gradle/aapt2. Butuh Kotlin dari menu Paket. Untuk project " +\n'
        '            "Android letakkan android.jar di /sdcard/Molina/android.jar.",',
        'note = "Isi DIR dengan folder project yang punya gradlew, dan TASK Gradle (default " +\n'
        '            "compileDebugKotlin; bisa diganti mis. assembleDebug). Butuh JDK 17 dari menu Paket dan " +\n'
        '            "Android SDK untuk project Android.",')
    if "compileDebugKotlin" not in tail:
        sys.exit("GAGAL: catatan entri tidak cocok; kirim KotlinCheckLibrary.kt ke Claude")
    new_text = (
        prefix
        + "    /** Isi entri versi lama (kotlinc langsung); dipakai [BuiltinUpgrader] untuk memperbarui entri yang belum diubah. */\n"
        + '    val legacyScript: String = """' + old_script + '""".trimIndent().replace(DOLLAR_MARK, \'$\')\n\n'
        + '    private val script: String = """' + new_script + '""".trimIndent().replace(DOLLAR_MARK, \'$\')'
        + tail.split(".replace(DOLLAR_MARK, '$')", 1)[1]
    )
    save(p, rel, new_text)

# 2. Syarat paket: JDK 17 (bukan Kotlin).
edit(KT + "/LibraryRequirements.kt", '"builtin-kotlin-check" to listOf("jdk17")',
     '"builtin-kotlin-check" to listOf("kotlin")', '"builtin-kotlin-check" to listOf("jdk17")')

# 3. Upgrader: entri lama yang belum diubah diganti otomatis.
edit(KT + "/BuiltinUpgrader.kt", "KotlinCheckLibrary.legacyScript",
     "    private val superseded: Map<String, List<String>> = mapOf(\n",
     "    private val superseded: Map<String, List<String>> = mapOf(\n"
     '        "builtin-kotlin-check" to listOf(KotlinCheckLibrary.legacyScript),\n')

# 4. Placeholder TASK.
edit(KT + "/LibraryFragment.kt", '"TASK" -> getString',
     '        "DIR" -> getString(R.string.placeholder_dir)\n',
     '        "DIR" -> getString(R.string.placeholder_dir)\n        "TASK" -> getString(R.string.placeholder_task)\n')
edit(KT + "/LibraryFragment.kt", '"TASK" -> "compileDebugKotlin"',
     '        "DIR" -> "/sdcard/"\n',
     '        "DIR" -> "/sdcard/"\n        "TASK" -> "compileDebugKotlin"\n')
edit(RES, "placeholder_task",
     '    <string name="placeholder_dir">Folder project (mis. /sdcard/molina-suite)</string>\n',
     '    <string name="placeholder_dir">Folder project (mis. /sdcard/molina-suite)</string>\n'
     '    <string name="placeholder_task">Task Gradle (mis. compileDebugKotlin, assembleDebug)</string>\n')

# 5. Dokumentasi kelas mengikuti perilaku baru.
edit(KT + "/KotlinCheckLibrary.kt", "menjalankan task Gradle",
     " * Entri Library bawaan \"Cek kompilasi Kotlin (project)\". Memeriksa semua berkas .kt di satu\n"
     " * folder project dengan kotlinc langsung (tanpa Gradle), karena assemble/compileDebugKotlin\n"
     " * butuh aapt2 yang tidak jalan di perangkat ini. Hasil kompilasi dibuang ke folder sementara.\n",
     " * Entri Library bawaan \"Cek kompilasi Kotlin (project)\": menjalankan task Gradle (default\n"
     " * compileDebugKotlin) lewat gradlew milik project di folder {DIR}; nama task diisi lewat {TASK}.\n"
     " * Skrip lama (kotlinc langsung) disimpan sebagai [legacyScript] hanya untuk pembaruan otomatis.\n")

print("\nSelesai. Berkas berubah:", len(changed))
