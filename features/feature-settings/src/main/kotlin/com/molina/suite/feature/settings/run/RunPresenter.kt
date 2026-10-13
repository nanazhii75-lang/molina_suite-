package com.molina.suite.feature.settings.run

import android.view.View
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes

/** Mengisi bagian isi layar progres (kartu metrik) untuk satu jenis proses. */
internal interface RunPresenter {

    @get:LayoutRes
    val layout: Int

    /** Teks keberhasilan di bawah progress bar. */
    @get:StringRes
    val doneMessage: Int

    /** Isi layar dibuat sekali setelah [layout] di-inflate. */
    fun onInflated(root: View)

    /** Teks tahap yang sedang berjalan, atau null bila tidak ada. */
    @StringRes
    fun stageLabel(stage: String): Int?

    fun bind(root: View, snapshot: RunSnapshot)
}
