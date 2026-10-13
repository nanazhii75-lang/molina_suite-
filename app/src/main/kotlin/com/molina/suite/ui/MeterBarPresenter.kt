package com.molina.suite.ui

import android.content.Context
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.molina.suite.R
import com.molina.suite.meter.CpuScope
import com.molina.suite.meter.MeterReading
import java.util.Locale

/** Menampilkan [MeterReading] ke empat TextView bar meter di header. Tidak menyimpan state lain. */
class MeterBarPresenter(
    private val context: Context,
    private val down: TextView,
    private val up: TextView,
    private val cpu: TextView,
    private val ram: TextView
) {
    private val normal = ContextCompat.getColor(context, R.color.molina_text_secondary)
    private val warn = ContextCompat.getColor(context, R.color.molina_amber)
    private val critical = ContextCompat.getColor(context, R.color.molina_status_error)

    fun render(reading: MeterReading) {
        down.text = context.getString(R.string.meter_down, speed(reading.rxBytesPerSec))
        up.text = context.getString(R.string.meter_up, speed(reading.txBytesPerSec))

        val cpuRes = if (reading.cpuScope == CpuScope.SYSTEM) R.string.meter_cpu else R.string.meter_cpu_app
        cpu.text = context.getString(cpuRes, reading.cpuPercent)
        cpu.setTextColor(severity(reading.cpuPercent))

        ram.text = context.getString(
            R.string.meter_ram,
            size(reading.ramUsedBytes),
            size(reading.ramTotalBytes)
        )
        ram.setTextColor(severity(reading.ramPercent))
    }

    private fun severity(percent: Int): Int = when {
        percent >= 90 -> critical
        percent >= 70 -> warn
        else -> normal
    }

    private fun speed(bytesPerSec: Long): String {
        if (bytesPerSec < 0) return context.getString(R.string.meter_unavailable)
        val kb = bytesPerSec / 1024.0
        val locale = Locale.getDefault()
        return when {
            kb < 10.0 -> String.format(locale, "%.1f KB/s", kb)
            kb < 1000.0 -> String.format(locale, "%.0f KB/s", kb)
            else -> String.format(locale, "%.1f MB/s", kb / 1024.0)
        }
    }

    private fun size(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        val locale = Locale.getDefault()
        return if (gb >= 1.0) String.format(locale, "%.1f GB", gb)
        else String.format(locale, "%.0f MB", bytes / (1024.0 * 1024.0))
    }
}
