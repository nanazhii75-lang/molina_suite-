package com.molina.suite.feature.x11

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Menangani tombol "Hentikan" di notifikasi X11: menghentikan tuntas lalu melaporkan hasilnya. */
class X11StopReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != X11StatusNotifier.ACTION_STOP_ALL) return
        val controller = X11StopHandler.controller ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val result = controller.stopAll()
                if (result.isClean) {
                    app.getSystemService(NotificationManager::class.java)
                        ?.cancel(X11StatusNotifier.NOTIFICATION_ID)
                    toast(
                        app,
                        app.getString(R.string.x11_notif_stopped, X11StatusNotifier.formatKb(result.freedKb))
                    )
                } else {
                    toast(app, app.getString(R.string.x11_notif_remaining, result.remaining.size))
                }
            } catch (e: RuntimeException) {
                toast(app, app.getString(R.string.x11_notif_stop_failed, e.message.orEmpty()))
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
