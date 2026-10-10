package com.molina.suite.feature.mpv

import android.util.Log

/**
 * Memeriksa apakah library native mpv bisa dimuat, dengan menginisialisasi kelas
 * JNI MPVLib (initializer-nya memuat .so). Hasil di-cache setelah pemeriksaan pertama.
 */
internal object MpvNativeProbe {

    private const val TAG = "MpvNativeProbe"
    private const val MPV_LIB_CLASS = "com.molina.suite.mpv.MPVLib"

    @Volatile
    private var cached: Boolean? = null

    fun isReady(): Boolean =
        cached ?: synchronized(this) {
            cached ?: probe().also { cached = it }
        }

    private fun probe(): Boolean = try {
        Class.forName(MPV_LIB_CLASS, true, MpvNativeProbe::class.java.classLoader)
        true
    } catch (e: ClassNotFoundException) {
        Log.w(TAG, "Kelas $MPV_LIB_CLASS tidak ditemukan", e)
        false
    } catch (e: LinkageError) {
        Log.w(TAG, "Library native mpv belum tersedia", e)
        false
    }
}
