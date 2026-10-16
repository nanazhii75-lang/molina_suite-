package com.molina.suite.mpv

import android.util.Log

/**
 * Mode audio saja: trek video dilepas (vid=no) sehingga tidak didekode. Pada stream yang video
 * dan audionya terpisah (hasil yt-dlp), trek video yang tidak dipilih juga tidak diunduh lagi.
 * Mode tetap aktif untuk berkas berikutnya di playlist selama sesi pemutar yang sama.
 */
internal class AudioOnlyMode : MPVLib.EventObserver {

    @Volatile
    var isActive: Boolean = false
        private set

    /** Mode hanya boleh dinyalakan bila ada trek audio yang sedang dipilih. */
    fun hasAudio(): Boolean = (MPVLib.getPropertyInt("aid") ?: -1) > 0

    /** Mengatur mode; false bila tidak bisa dinyalakan karena tidak ada trek audio. */
    fun set(enabled: Boolean): Boolean {
        if (enabled && !hasAudio()) return false
        isActive = enabled
        apply()
        return true
    }

    private fun apply() {
        try {
            MPVLib.setPropertyString("vid", if (isActive) "no" else "auto")
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal mengatur trek video", e)
        }
    }

    // vid kembali ke bawaan di setiap berkas baru, jadi pilihan dipasang ulang setelah dimuat.
    override fun event(eventId: Int) {
        if (isActive && eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) apply()
    }

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String, value: Double) {}

    private companion object {
        const val TAG = "AudioOnlyMode"
    }
}
