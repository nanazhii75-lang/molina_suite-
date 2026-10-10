package com.molina.suite.mpv.search

import android.content.Context
import com.molina.suite.mpv.R
import java.util.Locale

/** Pemformat teks untuk item hasil: jumlah tayangan, durasi, dan umur video. */
internal object ResultFormat {

    private val ID: Locale = Locale.Builder().setLanguage("id").setRegion("ID").build()

    fun views(context: Context, count: Long): String =
        context.getString(R.string.molina_views, compact(count))

    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun age(context: Context, publishedMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val seconds = ((nowMillis - publishedMillis) / 1000L).coerceAtLeast(0L)
        val minutes = seconds / 60
        val hours = seconds / 3600
        val days = seconds / 86400
        return when {
            minutes < 1 -> context.getString(R.string.molina_time_now)
            minutes < 60 -> context.getString(R.string.molina_time_minutes, minutes.toInt())
            hours < 24 -> context.getString(R.string.molina_time_hours, hours.toInt())
            days < 7 -> context.getString(R.string.molina_time_days, days.toInt())
            days < 30 -> context.getString(R.string.molina_time_weeks, (days / 7).toInt())
            days < 365 -> context.getString(R.string.molina_time_months, (days / 30).toInt())
            else -> context.getString(R.string.molina_time_years, (days / 365).toInt())
        }
    }

    private fun compact(count: Long): String = when {
        count < 1_000L -> count.toString()
        count < 1_000_000L -> one(count / 1_000.0) + " rb"
        count < 1_000_000_000L -> one(count / 1_000_000.0) + " jt"
        else -> one(count / 1_000_000_000.0) + " M"
    }

    private fun one(value: Double): String {
        val text = String.format(ID, "%.1f", value)
        return if (text.endsWith(",0")) text.dropLast(2) else text
    }
}
