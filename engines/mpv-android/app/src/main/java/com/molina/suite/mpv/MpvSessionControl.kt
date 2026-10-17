package com.molina.suite.mpv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log

/**
 * Kendali sesi pemutar dari luar [MPVActivity]: membuka kembali pemutar, membawa shell aplikasi ke
 * depan, dan meminta pemutar menutup. Engine tidak mengenal kelas shell; yang dipakai hanya
 * activity peluncur paket ini.
 */
object MpvSessionControl {

    private const val TAG = "mpv"

    @Volatile
    private var closer: Runnable? = null

    internal fun attach(newCloser: Runnable) {
        closer = newCloser
    }

    internal fun detach(oldCloser: Runnable) {
        if (closer === oldCloser) closer = null
    }

    /** Meminta pemutar yang aktif menutup diri. False bila tidak ada pemutar. */
    fun requestClose(): Boolean {
        val target = closer ?: return false
        target.run()
        return true
    }

    /** Membawa [MPVActivity] yang sedang berjalan kembali ke depan. */
    fun bringToFront(host: Activity): Boolean = try {
        host.startActivity(Intent(host, MPVActivity::class.java))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "MpvSessionControl: pemutar tidak bisa dibuka", e)
        false
    }

    /** Membawa shell (activity peluncur aplikasi) ke depan tanpa membuat instans baru. */
    fun showSuite(host: Activity): Boolean {
        val launcher = host.packageManager.getLaunchIntentForPackage(host.packageName)?.component
            ?: return false
        val intent = Intent()
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return try {
            host.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "MpvSessionControl: shell tidak bisa dibuka", e)
            false
        }
    }
}
