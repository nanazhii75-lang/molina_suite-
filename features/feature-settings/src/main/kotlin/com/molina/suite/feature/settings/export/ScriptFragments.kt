package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ScriptRunLine

/**
 * Potongan skrip bash yang sama bagi semua pembangun skrip konversi: pembuka (variabel, jebakan
 * pembersih, pindah folder) dan penutup (memindahkan hasil ke tempat akhirnya). Hasil ditulis ke
 * berkas sementara `P` lalu dipindah, supaya konversi yang gagal atau dihentikan tidak
 * meninggalkan berkas rusak atau menimpa berkas lama.
 */
internal object ScriptFragments {

    /** Tanda dolar untuk dipakai di dalam templat skrip Kotlin tanpa dianggap templat string. */
    const val D = "$"

    /** Kode keluar `timeout` bila perintah dihentikan karena melewati batas waktu. */
    const val TIMEOUT_EXIT_CODE = 124

    /** Pembuka: membuat folder hasil lalu pindah ke folder sumber. `X` dipakai skrip sebagai berkas sementara. */
    fun header(partial: String, outputDir: String, inputDir: String): String = """
P=${ScriptRunLine.quote(partial)}
X=
trap 'rm -f "${D}P" "${D}X"' EXIT
fail() { echo "${D}1" >&2; exit 2; }
mkdir -p ${ScriptRunLine.quote(outputDir)} || fail "Tidak bisa membuat folder hasil."
cd ${ScriptRunLine.quote(inputDir)} || fail "Tidak bisa membuka folder sumber."
""".trimStart()

    /** Penutup: hasil harus tidak kosong, lalu dipindah ke [output]. */
    fun footer(output: String): String = """
[ -s "${D}P" ] || fail "Hasil kosong."
mv -f "${D}P" ${ScriptRunLine.quote(output)}
""".trimStart()
}
