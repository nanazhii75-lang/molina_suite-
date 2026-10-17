package com.molina.suite.feature.settings.x11

import android.content.Context
import com.molina.suite.core.common.EngineLauncher
import com.molina.suite.core.common.X11Controller
import com.molina.suite.core.common.X11StopResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pelaksana [X11Controller] di atas [X11Control]. Peluncur dicari saat dipakai karena modul X11
 * mendaftarkannya setelah objek ini dibuat.
 */
internal class SettingsX11Controller(
    private val context: Context,
    private val launcher: () -> EngineLauncher?
) : X11Controller {

    override suspend fun memoryKb(): Long =
        withContext(Dispatchers.IO) { X11Control.usage(context).totalKb }

    override suspend fun stopAll(): X11StopResult {
        val before = withContext(Dispatchers.IO) { X11Control.usage(context) }
        val desktop = X11Control.stopDesktop(context)
        launcher()?.stop()
        val server = X11Control.stopServer()
        val remaining = desktop.remaining + server.remaining
        return X11StopResult(
            stopped = desktop.stopped + server.stopped,
            remaining = remaining,
            freedKb = if (remaining.isEmpty()) before.totalKb else 0L
        )
    }
}
