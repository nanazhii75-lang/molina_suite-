package com.molina.suite.feature.settings.run

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

/**
 * Jenis proses yang punya layar progres. [scriptAsset] adalah skrip di assets yang menulis berkas
 * status, [scriptId] nama berkas skrip di folder skrip, dan [prefix] awalan nama berkas status.
 */
internal enum class RunKind(
    @StringRes val title: Int,
    val scriptAsset: String,
    val scriptId: String,
    val prefix: String
) {
    PROBE(R.string.run_probe_title, "molina-run/probe-info.sh", "run-probe-info", "probe"),
    CONVERT(R.string.run_convert_title, "molina-run/convert-media.sh", "run-convert-media", "convert"),
    DOWNLOAD(R.string.run_download_title, "molina-run/download-media.sh", "run-download-media", "download");

    val presenter: RunPresenter
        get() = when (this) {
            PROBE -> ProbeRunPresenter
            CONVERT -> ConvertRunPresenter
            DOWNLOAD -> DownloadRunPresenter
        }
}
