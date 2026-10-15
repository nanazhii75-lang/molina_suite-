package com.molina.suite.feature.settings.logcat

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.molina.suite.feature.settings.R
import java.io.File

/**
 * Menampilkan crash di bilah notifikasi Android: judul, pesan exception, cuplikan stack trace, dan
 * tombol Salin dan Bagikan. Mengetuk notifikasi membuka [CrashReportActivity] yang berdiri sendiri,
 * tidak bergantung pada layar utama, jadi laporan tetap terbaca walaupun aplikasi tidak bisa dibuka.
 * Satu notifikasi untuk laporan terbaru; tidak pernah melempar.
 */
internal object CrashNotifier {

    private const val CHANNEL_ID = "molina_crash_reports"
    private const val NOTIFICATION_ID = 7401
    private const val REQUEST_OPEN = 7411
    private const val REQUEST_COPY = 7412
    private const val REQUEST_SHARE = 7413
    private const val NOTIFIED_FILE_NAME = "crash-notified"
    private const val PREVIEW_LINES = 12
    private const val PREVIEW_CHARS = 700
    private const val ACCENT_COLOR = 0xFFE53935.toInt()
    private const val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private val lock = Any()

    /** Menampilkan notifikasi untuk [report]; false bila notifikasi dimatikan atau gagal dikirim. */
    @SuppressLint("MissingPermission")
    fun notifyReport(context: Context, report: File): Boolean = synchronized(lock) {
        try {
            val app = context.applicationContext
            val text = CrashLogStore.read(report) ?: return false
            val manager = NotificationManagerCompat.from(app)
            if (!manager.areNotificationsEnabled()) return false
            ensureChannel(app)

            val kind = CrashSummary.kind(text)
            val title = app.getString(
                if (kind == CrashKind.CRASH) R.string.crash_notif_title_crash else R.string.crash_notif_title_death
            )
            val headline = CrashSummary.headline(text).ifEmpty { app.getString(R.string.crash_notif_no_detail) }
            val preview = CrashSummary.preview(text, PREVIEW_LINES, PREVIEW_CHARS).ifEmpty { headline }

            val open = PendingIntent.getActivity(
                app, REQUEST_OPEN, CrashReportActivity.intent(app, report, null), PENDING_FLAGS
            )
            val copy = PendingIntent.getBroadcast(
                app, REQUEST_COPY, CrashActionReceiver.copyIntent(app, report), PENDING_FLAGS
            )
            val share = PendingIntent.getActivity(
                app, REQUEST_SHARE, CrashReportActivity.intent(app, report, CrashReportActivity.COMMAND_SHARE), PENDING_FLAGS
            )
            val notification = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(headline)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(preview + "\n\n" + app.getString(R.string.crash_notif_hint))
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setColor(ACCENT_COLOR)
                .setWhen(report.lastModified())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, app.getString(R.string.crash_action_copy), copy)
                .addAction(0, app.getString(R.string.crash_action_share), share)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            StateFile.write(markerFile(app), report.name)
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /**
     * Dipanggil saat aplikasi dimulai: bila ada laporan yang belum dibuka dan belum pernah
     * diberitahukan (mis. proses mati sebelum notifikasi sempat terkirim, atau crash native),
     * tampilkan notifikasinya sekarang.
     */
    fun notifyPending(context: Context) {
        try {
            val app = context.applicationContext
            val latest = CrashLogStore.latest(CrashRecorder.crashDir(app)) ?: return
            if (ProcessTracking.crashNotice(app).unseen() == 0) return
            if (StateFile.read(markerFile(app))?.trim() == latest.name) return
            notifyReport(app, latest)
        } catch (e: RuntimeException) {
            // Pemberitahuan susulan hanya pelengkap; layar Logcat tetap menyimpan laporannya.
        }
    }

    private fun markerFile(context: Context): File = File(ProcessTracking.stateDir(context), NOTIFIED_FILE_NAME)

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.crash_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = context.getString(R.string.crash_channel_desc)
        manager.createNotificationChannel(channel)
    }
}
