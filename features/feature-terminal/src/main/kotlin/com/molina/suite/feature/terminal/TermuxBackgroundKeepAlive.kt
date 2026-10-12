package com.molina.suite.feature.terminal

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.terminal.app.TermuxService
import com.molina.suite.terminal.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementasi [BackgroundKeepAlive] lewat aksi wake lock milik TermuxService
 * (tidak diekspor), dengan gaya pengiriman intent yang sama seperti
 * [TermuxShellCommandRunner].
 */
class TermuxBackgroundKeepAlive(private val application: Application) : BackgroundKeepAlive {

    private val held = AtomicBoolean(false)

    override fun acquire() {
        if (held.compareAndSet(false, true) && !send(TERMUX_SERVICE.ACTION_WAKE_LOCK)) {
            held.set(false)
        }
    }

    override fun release() {
        if (held.compareAndSet(true, false) && !send(TERMUX_SERVICE.ACTION_WAKE_UNLOCK)) {
            held.set(true)
        }
    }

    private fun send(action: String): Boolean {
        val intent = Intent(action).setClass(application, TermuxService::class.java)
        return try {
            ContextCompat.startForegroundService(application, intent)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal mengirim $action ke engine terminal", e)
            false
        }
    }

    private companion object {
        const val TAG = "MolinaKeepAlive"
    }
}
