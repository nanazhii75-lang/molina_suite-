package com.molina.suite.feature.code

/** Pilihan tampilan editor yang bertahan selama proses hidup, termasuk saat view dibuat ulang. */
internal data class CodeViewOptions(
    val wordwrap: Boolean = true,
    val readOnly: Boolean = false
)

/**
 * Keadaan pencarian. [total] adalah jumlah kecocokan; [current] berbasis satu
 * dan bernilai 0 bila kursor belum berada di salah satu kecocokan.
 */
internal data class CodeSearchState(
    val query: String,
    val total: Int,
    val current: Int
) {
    companion object {
        val NONE = CodeSearchState(query = "", total = 0, current = 0)
    }
}
