package com.molina.suite

import android.content.Context
import android.widget.Toast
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Menampilkan galat peluncur X11 sebagai toast. Dikumpulkan di level aplikasi karena
 * galat bisa muncul saat jendela X11 sedang di depan dan MainActivity tidak aktif.
 */
object X11ErrorToaster {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context, engines: EngineRegistry) {
        val launcher = engines.findLauncher(EngineId.X11) ?: return
        val app = context.applicationContext
        scope.launch {
            var shown: String? = null
            launcher.lastError.collect { message ->
                if (message.isNullOrBlank()) {
                    shown = null
                    return@collect
                }
                if (message == shown) return@collect
                shown = message
                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
