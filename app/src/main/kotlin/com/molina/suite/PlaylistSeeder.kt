package com.molina.suite

import android.content.Context
import android.util.Log
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.iptv.M3uNormalizer
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Menyalin playlist IPTV bawaan (aset molina-iptv) ke /sdcard/Molina/Playlists/ supaya bisa
 * dipilih lewat Open Local di mpv. Playlist dirapikan oleh [M3uNormalizer] (nama kapital,
 * grup baku). Berkas yang isinya sudah berbeda dari aset (diubah pengguna atau sudah
 * dirapikan) tidak pernah ditimpa. Lokasi folder berasal dari [MolinaStorage].
 */
internal object PlaylistSeeder {

    private const val TAG = "PlaylistSeeder"
    private const val ASSET_DIR = "molina-iptv"

    sealed interface Outcome {
        data class Done(val copied: List<String>, val kept: List<String>) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /** Menyalin di thread latar belakang; hasilnya dicatat ke logcat. */
    fun seedAsync(context: Context) {
        val app = context.applicationContext
        thread(name = "molina-playlist-seed", isDaemon = true) {
            when (val outcome = seed(app)) {
                is Outcome.Done ->
                    Log.i(TAG, "Playlist bawaan: disalin=${outcome.copied}, dibiarkan=${outcome.kept}")
                is Outcome.Failed ->
                    Log.w(TAG, "Playlist bawaan tidak disalin: ${outcome.reason}")
            }
        }
    }

    fun seed(context: Context): Outcome {
        val names = try {
            context.assets.list(ASSET_DIR).orEmpty()
                .filter { it.endsWith(".m3u", ignoreCase = true) || it.endsWith(".m3u8", ignoreCase = true) }
        } catch (e: IOException) {
            return Outcome.Failed("Aset $ASSET_DIR tidak terbaca: ${e.message}")
        }
        if (names.isEmpty()) return Outcome.Failed("Tidak ada playlist di aset $ASSET_DIR")

        when (val ensured = MolinaStorage.ensureDirectories()) {
            is MolinaStorage.EnsureResult.Failed -> return Outcome.Failed(ensured.reason)
            is MolinaStorage.EnsureResult.Ready -> Unit
        }

        val dir = MolinaStorage.playlistsDir()
        try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                return Outcome.Failed("Gagal membuat folder ${dir.path}")
            }
        } catch (e: SecurityException) {
            return Outcome.Failed("Izin ditolak saat membuat ${dir.path}: ${e.message}")
        }

        val copied = mutableListOf<String>()
        val kept = mutableListOf<String>()
        for (name in names) {
            val target = File(dir, name)
            val tmp = File(dir, ".$name.tmp")
            try {
                val raw = context.assets.open("$ASSET_DIR/$name").use { it.readBytes() }
                if (target.exists() && !target.readBytes().contentEquals(raw)) {
                    kept += name
                    continue
                }
                tmp.writeBytes(M3uNormalizer.normalizeBytes(raw))
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return Outcome.Failed("Gagal mengganti nama ${tmp.name} menjadi $name")
                }
                copied += name
            } catch (e: IOException) {
                tmp.delete()
                return Outcome.Failed("Gagal menyalin $name: ${e.message}")
            } catch (e: SecurityException) {
                tmp.delete()
                return Outcome.Failed("Izin ditolak saat menyalin $name: ${e.message}")
            }
        }
        return Outcome.Done(copied, kept)
    }
}
