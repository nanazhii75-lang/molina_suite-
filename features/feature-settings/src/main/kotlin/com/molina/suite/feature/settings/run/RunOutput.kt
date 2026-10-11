package com.molina.suite.feature.settings.run

/** Keterangan kartu "Hasil" (format dan folder) yang dibaca dari berkas status proses. */
internal object RunOutput {

    private const val SHARED_PREFIX = "/sdcard/"

    /** Mis. "MP3  •  Molina/Downloads"; null bila format dan folder belum diketahui. */
    fun detail(status: RunStatus): String? {
        val folder = status.value("out_path")
            ?.removePrefix(SHARED_PREFIX)
            ?.substringBeforeLast('/', "")
            ?.takeIf { it.isNotEmpty() }
        return listOfNotNull(status.value("fmt"), folder)
            .takeIf { it.isNotEmpty() }?.joinToString("  •  ")
    }
}
