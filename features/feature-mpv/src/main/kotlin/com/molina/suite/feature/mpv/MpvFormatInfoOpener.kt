package com.molina.suite.feature.mpv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostIntents
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.TerminalInputResult
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.mpv.FormatInfoOpener
import com.molina.suite.mpv.MpvSessionControl

/**
 * Menjembatani "Info format" di mpv ke tab Terminal lewat kontrak [TerminalInputSender]: shell
 * dibawa ke tab Terminal, lalu baris `molina-ytdlp -F <url>` diketik begitu prompt siap. Selama
 * sesi masih login atau menjalankan program, pengiriman diulang sebentar, lalu menyerah dengan
 * toast agar tidak mengetik ke program yang sedang berjalan. Hanya dipakai dari main thread.
 */
internal class MpvFormatInfoOpener(private val terminal: TerminalInputSender) : FormatInfoOpener {

    private val main = Handler(Looper.getMainLooper())
    private var generation = 0

    override fun open(host: Activity, url: String): Boolean {
        val line = FormatInfoCommand.build(url) ?: return false
        if (!showTerminal(host)) return false
        generation += 1
        deliver(host.applicationContext, line, generation, 0)
        return true
    }

    private fun showTerminal(host: Activity): Boolean {
        if (host is HostTabSwitcher) {
            host.showEngine(EngineId.TERMINAL)
            return true
        }
        val extras = Bundle().apply { putString(HostIntents.EXTRA_SHOW_ENGINE, EngineId.TERMINAL.name) }
        return MpvSessionControl.showSuite(host, extras)
    }

    private fun deliver(context: Context, line: String, gen: Int, attempt: Int) {
        if (gen != generation) return // permintaan yang lebih baru menggantikan
        when (val result = terminal.sendLineWhenReady(line)) {
            TerminalInputResult.Sent -> Unit
            is TerminalInputResult.Rejected -> {
                if (attempt >= MAX_ATTEMPTS) {
                    Log.w(TAG, "Info format tidak terkirim: " + result.reason)
                    Toast.makeText(context, R.string.molina_format_info_busy, Toast.LENGTH_LONG).show()
                } else {
                    main.postDelayed({ deliver(context, line, gen, attempt + 1) }, RETRY_MS)
                }
            }
        }
    }

    private companion object {
        const val TAG = "MpvFormatInfo"
        const val RETRY_MS = 400L
        const val MAX_ATTEMPTS = 30
    }
}
