package com.molina.suite.feature.mpv

import android.content.Context
import android.content.Intent
import com.molina.suite.mpv.MPVActivity
import com.molina.suite.mpv.MpvOpenKind

/** Intent ke pemutar untuk satu [PlayTarget]; dipakai perintah Terminal dan pembuka dari modul lain. */
internal object MpvPlayIntent {

    fun create(context: Context, target: PlayTarget): Intent {
        val kind = if (target.source == PlaySource.URL) MpvOpenKind.URL else MpvOpenKind.LOCAL
        return Intent()
            .putExtra("filepath", target.value)
            .putExtra(MpvOpenKind.EXTRA, kind.name)
            .setClass(context, MPVActivity::class.java)
    }
}
