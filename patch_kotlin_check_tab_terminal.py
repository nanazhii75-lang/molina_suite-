#!/usr/bin/env python3
"""Patch: (1) entri Library "Cek kompilasi Kotlin (project)" dengan placeholder {DIR};
(2) perbaikan tab Terminal dari editor Code: sesi tidak lagi dipaksa balik / dikunci saat
pengguna berpindah tab. Idempotent. Jalankan dari akar repo (molina-suite)."""
import os
import sys

ROOT = os.getcwd()
S = "features/feature-settings/src/main"
KT = S + "/kotlin/com/molina/suite/feature/settings"
TERM = "engines/termux/app/src/main/java/com/molina/suite/terminal/app"

changed = []


def path(rel):
    p = os.path.join(ROOT, rel)
    if not os.path.isfile(p) and not rel.endswith("KotlinCheckLibrary.kt"):
        sys.exit("GAGAL: berkas tidak ditemukan: " + rel + " (jalankan dari akar repo, git pull dulu)")
    return p


def edit(rel, marker, old, new):
    p = path(rel)
    with open(p, encoding="utf-8") as f:
        text = f.read()
    if marker in text:
        print("lewati (sudah ada):", rel, "->", marker)
        return
    if text.count(old) != 1:
        sys.exit("GAGAL: pola tidak unik/tidak ketemu di " + rel + ":\n" + old)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text.replace(old, new))
    changed.append(rel)
    print("diubah:", rel)


# ------------------------------------------------------------------ 1. entri Library
KOTLIN_LIB = '''package com.molina.suite.feature.settings.library

/**
 * Entri Library bawaan "Cek kompilasi Kotlin (project)". Memeriksa semua berkas .kt di satu
 * folder project dengan kotlinc langsung (tanpa Gradle), karena assemble/compileDebugKotlin
 * butuh aapt2 yang tidak jalan di perangkat ini. Hasil kompilasi dibuang ke folder sementara.
 *
 * Skrip memakai tanda [DOLLAR_MARK] sebagai pengganti tanda dolar supaya tidak dianggap
 * template Kotlin. {DIR} diisi lewat dialog Library dan di-quote otomatis. Isi skrip bisa
 * diubah dari editor entri (mis. ganti baris kotlinc untuk target lain).
 */
internal object KotlinCheckLibrary {

    const val ENTRY_ID = "builtin-kotlin-check"

    private const val DOLLAR_MARK = '\\u00A7'

    private val script: String = """
# Cek kompilasi Kotlin satu project tanpa Gradle. Hasil kompilasi dibuang.
DIR={DIR}
cd "\u00A7DIR" 2>/dev/null || { echo "GAGAL: folder tidak ditemukan: \u00A7DIR"; exit 1; }
command -v kotlinc >/dev/null 2>&1 || { echo "GAGAL: kotlinc belum terpasang. Pasang di Setting, menu Paket."; exit 1; }
T="\u00A7(mktemp -d)" || { echo "GAGAL: tidak bisa membuat folder sementara"; exit 1; }
trap 'rm -rf "\u00A7T"' EXIT
find . -type f -name '*.kt' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.gradle/*' | sort | sed 's/.*/"&"/' > "\u00A7T/args"
N="\u00A7(wc -l < "\u00A7T/args")"
[ "\u00A7N" -gt 0 ] || { echo "GAGAL: tidak ada berkas .kt di \u00A7DIR"; exit 1; }
CP=""
for J in "\u00A7{ANDROID_JAR:-}" /sdcard/Molina/android.jar; do
  [ -n "\u00A7J" ] && [ -f "\u00A7J" ] && { CP="\u00A7J"; break; }
done
echo "Memeriksa \u00A7N berkas Kotlin di \u00A7DIR"
if [ -n "\u00A7CP" ]; then
  echo "Classpath: \u00A7CP"
else
  echo "Catatan: android.jar tidak ditemukan; referensi android.* dan R dilaporkan unresolved."
  echo "Letakkan android.jar di /sdcard/Molina/android.jar untuk project Android."
fi
export JAVA_OPTS="\u00A7{JAVA_OPTS:--Xmx768m -Xss4m}"
if [ -n "\u00A7CP" ]; then
  kotlinc -classpath "\u00A7CP" -d "\u00A7T/out" @"\u00A7T/args" 2>&1
else
  kotlinc -d "\u00A7T/out" @"\u00A7T/args" 2>&1
fi
RC=\u00A7?
if [ "\u00A7RC" -eq 0 ]; then echo "SELESAI: tidak ada error kompilasi."; else echo "SELESAI: ada error kompilasi (kode \u00A7RC)."; fi
exit \u00A7RC
""".trimIndent().replace(DOLLAR_MARK, '$')

    val entry: LibraryEntry = LibraryEntry(
        id = ENTRY_ID,
        name = "Cek kompilasi Kotlin (project)",
        content = script,
        note = "Isi DIR dengan folder project (mis. /sdcard/molina-suite). Semua berkas .kt di dalamnya " +
            "diperiksa dengan kotlinc, tanpa Gradle/aapt2. Butuh Kotlin dari menu Paket. Untuk project " +
            "Android letakkan android.jar di /sdcard/Molina/android.jar.",
        runIn = RunMode.DEBIAN,
        builtin = true
    )
}
'''.replace("\\u00A7", "\u00A7")

kp = path(KT + "/library/KotlinCheckLibrary.kt")
if os.path.isfile(kp):
    print("lewati (sudah ada): KotlinCheckLibrary.kt")
else:
    with open(kp, "w", encoding="utf-8", newline="\n") as f:
        f.write(KOTLIN_LIB)
    changed.append("KotlinCheckLibrary.kt")
    print("dibuat: KotlinCheckLibrary.kt")

edit(KT + "/library/BuiltinLibrary.kt", "KotlinCheckLibrary.entry",
     "        DocumentConvertLibrary.entry,\n",
     "        DocumentConvertLibrary.entry,\n        KotlinCheckLibrary.entry,\n")

edit(KT + "/library/LibraryRequirements.kt", "builtin-kotlin-check",
     '        "builtin-aria2-download" to listOf("aria2")\n    )',
     '        "builtin-aria2-download" to listOf("aria2"),\n        "builtin-kotlin-check" to listOf("kotlin")\n    )')

edit(KT + "/library/LibraryFragment.kt", '"DIR" -> getString',
     '        "OUT" -> getString(R.string.placeholder_out)\n',
     '        "OUT" -> getString(R.string.placeholder_out)\n        "DIR" -> getString(R.string.placeholder_dir)\n')

edit(KT + "/library/LibraryFragment.kt", '"DIR" -> "/sdcard/"',
     '        "FILE", "OUT" -> "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}/"\n        else -> null',
     '        "FILE", "OUT" -> "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}/"\n        "DIR" -> "/sdcard/"\n        else -> null')

edit(S + "/res/values/strings.xml", "placeholder_dir",
     '    <string name="placeholder_out">Path hasil</string>\n',
     '    <string name="placeholder_out">Path hasil</string>\n'
     '    <string name="placeholder_dir">Folder project (mis. /sdcard/molina-suite)</string>\n')

# ------------------------------------------------------------------ 2. tab Terminal
edit(TERM + "/terminal/TermuxTerminalSessionClient.java", "boolean displayedAlive",
     "        if (mHost.getTermuxService() != null) {\n"
     "            setCurrentSession(getCurrentStoredSessionOrLast());\n"
     "            termuxSessionListNotifyUpdated();\n"
     "        }\n",
     "        TermuxService service = mHost.getTermuxService();\n"
     "        if (service != null) {\n"
     "            // molina-suite: sesi yang sedang tampil dan masih ada di daftar dipertahankan (mis. tab\n"
     "            // baru dari editor Code); sesi tersimpan/terakhir hanya dipakai bila belum ada yang tampil.\n"
     "            TerminalSession displayed = mHost.getCurrentSession();\n"
     "            boolean displayedAlive = displayed != null && service.getIndexOfSession(displayed) >= 0;\n"
     "            setCurrentSession(displayedAlive ? displayed : getCurrentStoredSessionOrLast());\n"
     "            termuxSessionListNotifyUpdated();\n"
     "        }\n")

edit(TERM + "/TermuxFragment.java", "tidak memaksa pindah tab saat menunggu",
     "            // Folder sama: tetap di tab ini. Hentikan proses lama (Ctrl+C), lalu pemanggil mengulang.\n"
     "            mTermuxTerminalSessionClient.setCurrentSession(busy);\n"
     "            busy.write(new byte[]{3}, 0, 1);\n",
     "            // Folder sama: hentikan proses lama (Ctrl+C), lalu pemanggil mengulang. Tab tidak\n"
     "            // dipaksa tampil di sini (tidak memaksa pindah tab saat menunggu) supaya pengguna\n"
     "            // tetap bisa memilih tab lain; tab dipilih saat perintah benar-benar diketik.\n"
     "            busy.write(new byte[]{3}, 0, 1);\n")

edit(TERM + "/TermuxFragment.java", "Tab terminal baru belum siap\";\n        mTermuxTerminalSessionClient.setCurrentSession(target);",
     "        mTermuxTerminalSessionClient.setCurrentSession(target);\n"
     "        if (target.getPid() <= 0) return \"Tab terminal baru belum siap\";\n",
     "        if (target.getPid() <= 0) return \"Tab terminal baru belum siap\";\n"
     "        mTermuxTerminalSessionClient.setCurrentSession(target);\n")

print("\nSelesai. Berkas berubah/dibuat:", len(changed))
for c in changed:
    print(" -", c)
