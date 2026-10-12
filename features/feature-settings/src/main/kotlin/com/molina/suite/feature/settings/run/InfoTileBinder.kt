package com.molina.suite.feature.settings.run

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.molina.suite.feature.settings.R

/** Mengisi satu kartu view_run_info.xml sesuai [InfoTile]; dipakai Info Video dan Konverter Media. */
internal object InfoTileBinder {

    fun label(tile: View, @StringRes text: Int) {
        tile.findViewById<TextView>(R.id.info_label).setText(text)
    }

    /**
     * [absentText] tampil bila bagian itu tidak ada; [readingText] tampil saat sedang dibaca atau
     * dihitung.
     */
    fun bind(tile: View, state: InfoTile, @StringRes absentText: Int, @StringRes readingText: Int) {
        val ctx = tile.context
        val value = tile.findViewById<TextView>(R.id.info_value)
        val detail = tile.findViewById<TextView>(R.id.info_detail)
        val dot = tile.findViewById<View>(R.id.info_dot)

        @ColorRes val valueColor: Int
        @ColorRes val dotColor: Int
        val detailText: String?
        when (state) {
            is InfoTile.Pending -> {
                value.text = "—"
                detailText = ctx.getString(R.string.run_metric_pending)
                valueColor = R.color.ms_text_secondary
                dotColor = R.color.ms_status_off
            }
            is InfoTile.Reading -> {
                value.text = "…"
                detailText = ctx.getString(readingText)
                valueColor = R.color.ms_amber
                dotColor = R.color.ms_amber
            }
            is InfoTile.Absent -> {
                value.text = "—"
                detailText = ctx.getString(absentText)
                valueColor = R.color.ms_text_secondary
                dotColor = R.color.ms_status_off
            }
            is InfoTile.Failed -> {
                value.text = "—"
                detailText = ctx.getString(R.string.run_metric_failed)
                valueColor = R.color.ms_status_error
                dotColor = R.color.ms_status_error
            }
            is InfoTile.Value -> {
                value.text = state.main
                detailText = state.detail
                valueColor = R.color.ms_text_primary
                dotColor = R.color.ms_status_on
            }
        }
        value.setTextColor(ContextCompat.getColor(ctx, valueColor))
        dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, dotColor))
        if (detailText == null) {
            detail.visibility = View.GONE
        } else {
            detail.visibility = View.VISIBLE
            detail.text = detailText
        }
    }
}
