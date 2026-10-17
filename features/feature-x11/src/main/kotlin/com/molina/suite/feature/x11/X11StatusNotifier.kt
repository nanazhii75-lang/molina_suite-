package com.molina.suite.feature.x11

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.molina.suite.core.common.EngineLauncher
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.core.common.X11Controller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Notifikasi tetap selama server X11 berjalan: menampilkan memori yang dipakai dan dua tombol,
 * "Buka X11" dan "Hentikan" (menghentikan desktop dan server sampai tuntas).
 */
internal class X11StatusNotifier(
    private val application: Application,
    private val launcher: EngineLauncher,
    private val controller: X11Controller
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = application.getSystemService(NotificationManager::class.java)

    fun start() {
        scope.launch {
            launcher.status.collectLatest { status ->
                if (status != EngineStatus.RUNNING) {
                    manager?.cancel(NOTIFICATION_ID)
                    return@collectLatest
                }
                ensureChannel()
                while (true) {
                    show(readMemoryKb())
                    delay(REFRESH_MS)
                }
            }
        }
    }

    private suspend fun readMemoryKb(): Long = try {
        controller.memoryKb()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RuntimeException) {
        0L
    }

    private fun ensureChannel() {
        val nm = manager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            application.getString(R.string.x11_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = application.getString(R.string.x11_notif_channel_desc)
        nm.createNotificationChannel(channel)
    }

    private fun show(memoryKb: Long) {
        val nm = manager ?: return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            application,
            REQUEST_OPEN,
            Intent()
                .setClassName(application.packageName, X11ServerCommand.WINDOW_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags
        )
        val stop = PendingIntent.getBroadcast(
            application,
            REQUEST_STOP,
            Intent(application, X11StopReceiver::class.java).setAction(ACTION_STOP_ALL),
            flags
        )
        val text = if (memoryKb > 0L) {
            application.getString(R.string.x11_notif_text, formatKb(memoryKb))
        } else {
            application.getString(R.string.x11_notif_text_unknown)
        }
        val notification = NotificationCompat.Builder(application, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(application.getString(R.string.x11_notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, application.getString(R.string.x11_notif_action_open), open)
            .addAction(0, application.getString(R.string.x11_notif_action_stop), stop)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    internal companion object {
        const val ACTION_STOP_ALL = "com.molina.suite.feature.x11.ACTION_STOP_ALL"
        const val NOTIFICATION_ID = 7411
        private const val CHANNEL_ID = "molina_x11_status"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2
        private const val REFRESH_MS = 15_000L

        /** "512 KB" atau "128 MB"; tanpa pengaruh locale. */
        fun formatKb(kb: Long): String = if (kb >= 1024L) "${kb / 1024L} MB" else "$kb KB"
    }
}
