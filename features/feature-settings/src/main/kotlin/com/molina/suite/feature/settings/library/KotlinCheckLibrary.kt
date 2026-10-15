package com.molina.suite.feature.settings.library

/**
 * Entri Library bawaan "Cek kompilasi Kotlin (project)": menjalankan task Gradle (default
 * compileDebugKotlin) lewat gradlew milik project di folder {DIR}; nama task diisi lewat {TASK}.
 * Skrip lama (kotlinc langsung) disimpan sebagai [legacyScript] hanya untuk pembaruan otomatis.
 *
 * Skrip memakai tanda [DOLLAR_MARK] sebagai pengganti tanda dolar supaya tidak dianggap
 * template Kotlin. {DIR} diisi lewat dialog Library dan di-quote otomatis. Isi skrip bisa
 * diubah dari editor entri (mis. ganti baris kotlinc untuk target lain).
 */
internal object KotlinCheckLibrary {

    const val ENTRY_ID = "builtin-kotlin-check"

    private const val DOLLAR_MARK = '§'

    /** Isi entri versi lama (kotlinc langsung); dipakai [BuiltinUpgrader] untuk memperbarui entri yang belum diubah. */
    val legacyScript: String = """
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

    /** Isi entri Gradle sebelumnya (tanpa batas RAM); dipakai [BuiltinUpgrader] untuk pembaruan otomatis. */
    val legacyGradleScript: String = """
# Kompilasi project lewat Gradle wrapper milik project. Ganti TASK sesuai kebutuhan
# (mis. compileDebugKotlin, assembleDebug, atau beberapa task dipisah spasi).
DIR={DIR}
TASK={TASK}
cd "§DIR" 2>/dev/null || { echo "GAGAL: folder tidak ditemukan: §DIR"; exit 1; }
[ -f gradlew ] || { echo "GAGAL: gradlew tidak ada di §DIR"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "GAGAL: Java belum terpasang. Pasang JDK 21 di Setting, menu Paket."; exit 1; }
if [ -z "§{ANDROID_HOME:-}§{ANDROID_SDK_ROOT:-}" ] && ! grep -qs "^sdk.dir" local.properties; then
  echo "Catatan: ANDROID_HOME dan sdk.dir di local.properties tidak ada; Gradle mungkin tidak menemukan Android SDK."
fi
echo "Menjalankan: ./gradlew §TASK di §DIR"
bash ./gradlew §TASK --console=plain --no-daemon 2>&1
RC=§?
if [ "§RC" -eq 0 ]; then echo "SELESAI: Gradle berhasil."; else echo "SELESAI: Gradle gagal (kode §RC)."; fi
exit §RC
""".trimIndent().replace(DOLLAR_MARK, '$')

    private val script: String = """
# Cek kompilasi Kotlin lewat Gradle wrapper milik project. Bukan build penuh: hanya task compile.
# Ganti TASK sesuai kebutuhan (mis. compileDebugKotlin atau :modul:compileDebugKotlin).
# Batas RAM: heap Gradle 1050 MB (ubah dengan GRADLE_MEM_MB), Kotlin dikompilasi di proses yang sama.
DIR={DIR}
TASK={TASK}
MEM="§{GRADLE_MEM_MB:-1050}"
cd "§DIR" 2>/dev/null || { echo "GAGAL: folder tidak ditemukan: §DIR"; exit 1; }
[ -f gradlew ] || { echo "GAGAL: gradlew tidak ada di §DIR"; exit 1; }
command -v java >/dev/null 2>&1 || { echo "GAGAL: Java belum terpasang. Pasang JDK 21 di Setting, menu Paket."; exit 1; }
case "§MEM" in ""|*[!0-9]*) echo "GAGAL: GRADLE_MEM_MB harus angka (MB): §MEM"; exit 1;; esac
if [ -z "§{MOLINA_ALLOW_FULL_BUILD:-}" ]; then
  for T in §TASK; do
    case "§T" in
      *assemble*|*bundle*|*install*|*package*|*lint*|*connected*|build|*:build)
        echo "GAGAL: task §T termasuk build penuh (butuh aapt2). Pakai task compile saja, mis. compileDebugKotlin."
        exit 1;;
    esac
  done
fi
if [ -z "§{ANDROID_HOME:-}§{ANDROID_SDK_ROOT:-}" ] && ! grep -qs "^sdk.dir" local.properties; then
  echo "Catatan: ANDROID_HOME dan sdk.dir di local.properties tidak ada; project Android mungkin gagal menemukan SDK."
fi
echo "Menjalankan: bash gradlew §TASK di §DIR (heap §{MEM} MB)"
bash gradlew §TASK --console=plain --no-daemon --no-parallel --max-workers=1 \
  "-Dorg.gradle.jvmargs=-Xmx§{MEM}m -XX:MaxMetaspaceSize=256m" \
  -Pkotlin.compiler.execution.strategy=in-process 2>&1
RC=§?
if [ "§RC" -eq 0 ]; then echo "SELESAI: Gradle berhasil."; else echo "SELESAI: Gradle gagal (kode §RC)."; fi
exit §RC
""".trimIndent().replace(DOLLAR_MARK, '$')

    val entry: LibraryEntry = LibraryEntry(
        id = ENTRY_ID,
        name = "Cek kompilasi Kotlin (project)",
        content = script,
        note = "Isi DIR dengan folder project yang punya gradlew, dan TASK compile (default " +
            "compileDebugKotlin, atau :modul:compileDebugKotlin). Task build penuh seperti assembleDebug " +
            "ditolak karena butuh aapt2. Heap Gradle dibatasi 1050 MB. Butuh JDK 21 dari menu Paket.",
        runIn = RunMode.DEBIAN,
        builtin = true
    )
}
