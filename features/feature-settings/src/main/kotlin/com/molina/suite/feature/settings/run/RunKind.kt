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
    NETWORK(R.string.run_network_title, "molina-run/net-check.sh", "run-net-check", "net");

    val presenter: RunPresenter
        get() = when (this) {
            NETWORK -> NetworkRunPresenter
        }
}

/** Perkiraan pemakaian data maksimum Cek Jaringan dalam MB, untuk teks kartu. */
internal object NetworkRun {
    const val MAX_DATA_MB = 35
}
