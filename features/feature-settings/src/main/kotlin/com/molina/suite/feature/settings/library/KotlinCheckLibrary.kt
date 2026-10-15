package com.molina.suite.feature.settings.library

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

    private const val DOLLAR_MARK = '§'

    private val script: String = """
# Cek kompilasi Kotlin satu project tanpa Gradle. Hasil kompilasi dibuang.
DIR={DIR}
cd "§DIR" 2>/dev/null || { echo "GAGAL: folder tidak ditemukan: §DIR"; exit 1; }
command -v kotlinc >/dev/null 2>&1 || { echo "GAGAL: kotlinc belum terpasang. Pasang di Setting, menu Paket."; exit 1; }
T="§(mktemp -d)" || { echo "GAGAL: tidak bisa membuat folder sementara"; exit 1; }
trap 'rm -rf "§T"' EXIT
find . -type f -name '*.kt' -not -path '*/build/*' -not -path '*/.git/*' -not -path '*/.gradle/*' | sort | sed 's/.*/"&"/' > "§T/args"
N="§(wc -l < "§T/args")"
[ "§N" -gt 0 ] || { echo "GAGAL: tidak ada berkas .kt di §DIR"; exit 1; }
CP=""
for J in "§{ANDROID_JAR:-}" /sdcard/Molina/android.jar; do
  [ -n "§J" ] && [ -f "§J" ] && { CP="§J"; break; }
done
echo "Memeriksa §N berkas Kotlin di §DIR"
if [ -n "§CP" ]; then
  echo "Classpath: §CP"
else
  echo "Catatan: android.jar tidak ditemukan; referensi android.* dan R dilaporkan unresolved."
  echo "Letakkan android.jar di /sdcard/Molina/android.jar untuk project Android."
fi
export JAVA_OPTS="§{JAVA_OPTS:--Xmx768m -Xss4m}"
if [ -n "§CP" ]; then
  kotlinc -classpath "§CP" -d "§T/out" @"§T/args" 2>&1
else
  kotlinc -d "§T/out" @"§T/args" 2>&1
fi
RC=§?
if [ "§RC" -eq 0 ]; then echo "SELESAI: tidak ada error kompilasi."; else echo "SELESAI: ada error kompilasi (kode §RC)."; fi
exit §RC
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
