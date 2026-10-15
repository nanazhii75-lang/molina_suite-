package com.molina.suite.feature.settings.logcat

import android.content.Context
import java.io.File

/**
 * Menyampaikan laporan crash yang baru ditulis: salinan ke /sdcard/Molina/crash lalu notifikasi.
 * Tiap langkah berdiri sendiri, jadi kegagalan satu tidak membatalkan yang lain, dan tidak pernah
 * melempar karena dipanggil dari penangan crash.
 */
internal object CrashDelivery {

    fun deliver(context: Context, report: File) {
        val app = context.applicationContext
        try {
            CrashExporter.exportToShared(report)
        } catch (ignored: Throwable) {
            // Salinan bersama hanya tambahan; laporan privat sudah tersimpan.
        }
        try {
            CrashNotifier.notifyReport(app, report)
        } catch (ignored: Throwable) {
            // Notifikasi gagal tidak boleh menutupi crash aslinya.
        }
    }
}
