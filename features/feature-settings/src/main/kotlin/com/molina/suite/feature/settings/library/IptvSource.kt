package com.molina.suite.feature.settings.library

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sumber playlist IPTV: bundel bawaan (assets) atau URL milik pengguna.
 *
 * Isi dari jaringan tidak tepercaya: selalu melewati [IptvPlaylist.sanitize], disimpan di folder
 * privat aplikasi, dan dibuka langsung oleh mpv lewat Intent. Tidak pernah ditulis atau dijalankan
 * lewat shell. Semua fungsi kecuali [play] memblokir I/O: panggil dari Dispatchers.IO.
 */
internal object IptvSource {

    class Status(val url: String, val custom: Boolean, val channels: Int, val updatedAt: Long)

    private const val PREFS = "molina_iptv"
    private const val KEY_URL = "source_url"
    private const val KEY_UPDATED = "updated_at"
    private const val KEY_BUNDLE = "bundle_id"
    private const val ASSET = "iptv/indonesia.m3u"
    private const val DIR_NAME = "iptv"
    private const val FILE_NAME = "indonesia.m3u"
    private const val MPV_ACTIVITY = "com.molina.suite.mpv.MPVActivity"
    /** Kontrak dengan engine mpv lewat ekstra Intent; nilainya harus sama dengan MolinaReferences. */
    private const val EXTRA_LOCK_REFERENCES = "molina_lock_references"
    private const val USER_AGENT = "MolinaSuite/1.0 (Android)"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 3

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dir(context: Context) = File(context.filesDir, DIR_NAME)

    fun activeFile(context: Context) = File(dir(context), FILE_NAME)

    fun status(context: Context): Status {
        val p = prefs(context)
        val updated = p.getLong(KEY_UPDATED, 0L)
        return Status(
            url = p.getString(KEY_URL, "").orEmpty(),
            custom = updated > 0L,
            channels = readParsed(activeFile(context))?.channels ?: 0,
            updatedAt = updated
        )
    }

    /** Memastikan ada playlist aktif yang valid; bila hilang atau rusak, dipulihkan dari bundel. */
    fun ensureActive(context: Context): LibraryResult<File> {
        val file = activeFile(context)
        val parsed = readParsed(file)
        if (parsed != null) {
            if (bundleStale(context)) return seed(context)
            if (parsed.dropped == 0) return LibraryResult.Ok(file)
            return writeAtomic(context, parsed.text)
        }
        return seed(context)
    }

    /** Mengunduh [rawUrl], menyaring, lalu mengganti playlist aktif. Gagal berarti playlist lama tetap. */
    fun update(context: Context, rawUrl: String): LibraryResult<Int> {
        val text = rawUrl.trim()
        val uri = IptvPlaylist.parseUri(text)
            ?: return LibraryResult.Failure("URL tidak valid: harus http:// atau https:// ke host publik")
        prefs(context).edit().putString(KEY_URL, text).apply()
        val url = try {
            uri.toURL()
        } catch (e: IOException) {
            return LibraryResult.Failure("URL tidak valid: ${e.message}")
        }
        val body = when (val r = download(url)) {
            is LibraryResult.Failure -> return r
            is LibraryResult.Ok -> r.value
        }
        val parsed = when (val r = IptvPlaylist.sanitize(body)) {
            is LibraryResult.Failure -> return r
            is LibraryResult.Ok -> r.value
        }
        when (val w = writeAtomic(context, parsed.text)) {
            is LibraryResult.Failure -> return w
            is LibraryResult.Ok -> Unit
        }
        prefs(context).edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
        return LibraryResult.Ok(parsed.channels)
    }

    /** Kembali ke bundel bawaan dan melupakan URL. */
    fun reset(context: Context): LibraryResult<Int> {
        prefs(context).edit().remove(KEY_URL).remove(KEY_UPDATED).apply()
        try {
            activeFile(context).delete()
        } catch (e: SecurityException) {
            return LibraryResult.Failure("Gagal menghapus playlist: ${e.message}")
        }
        return when (val r = ensureActive(context)) {
            is LibraryResult.Failure -> r
            is LibraryResult.Ok -> LibraryResult.Ok(readParsed(r.value)?.channels ?: 0)
        }
    }

    /** Membuka [file] di mpv lewat Intent eksplisit ke paket sendiri. Mengembalikan alasan gagal, atau null. */
    fun play(context: Context, file: File): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.fromFile(file)).apply {
            component = ComponentName(context.packageName, MPV_ACTIVITY)
            putExtra(EXTRA_LOCK_REFERENCES, true)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            "Mesin mpv tidak ditemukan"
        } catch (e: SecurityException) {
            e.message ?: "Ditolak sistem"
        }
    }

    private fun readParsed(file: File): IptvPlaylist.Parsed? {
        return try {
            if (!file.isFile || file.length() > IptvPlaylist.MAX_BYTES) return null
            when (val r = IptvPlaylist.sanitize(file.readText(Charsets.UTF_8))) {
                is LibraryResult.Ok -> r.value
                is LibraryResult.Failure -> null
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Penanda isi bundel di APK; berubah bila daftar bawaan diganti lewat pembaruan aplikasi. */
    private fun bundleId(context: Context): String? = try {
        context.assets.open(ASSET).use { readLimited(it) }?.hashCode()?.toString()
    } catch (e: IOException) {
        null
    }

    /** Pengguna yang belum memakai sumber sendiri mendapat bundel baru saat bundel APK berubah. */
    private fun bundleStale(context: Context): Boolean {
        val p = prefs(context)
        if (p.getLong(KEY_UPDATED, 0L) > 0L) return false
        val id = bundleId(context) ?: return false
        return p.getString(KEY_BUNDLE, null) != id
    }

    private fun seed(context: Context): LibraryResult<File> {
        val text = try {
            context.assets.open(ASSET).use { readLimited(it) }
        } catch (e: IOException) {
            return LibraryResult.Failure("Bundel IPTV tidak terbaca: ${e.message}")
        } ?: return LibraryResult.Failure("Bundel IPTV terlalu besar")
        val parsed = when (val r = IptvPlaylist.sanitize(text)) {
            is LibraryResult.Failure -> return LibraryResult.Failure("Bundel IPTV rusak: ${r.reason}")
            is LibraryResult.Ok -> r.value
        }
        val written = writeAtomic(context, parsed.text)
        if (written is LibraryResult.Ok) {
            bundleId(context)?.let { prefs(context).edit().putString(KEY_BUNDLE, it).apply() }
        }
        return written
    }

    private fun writeAtomic(context: Context, text: String): LibraryResult<File> {
        val dir = dir(context)
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            return LibraryResult.Failure("Gagal membuat folder ${dir.path}")
        }
        val target = File(dir, FILE_NAME)
        val temp = File(dir, "$FILE_NAME.tmp")
        return try {
            temp.writeText(text, Charsets.UTF_8)
            if (temp.renameTo(target)) {
                LibraryResult.Ok(target)
            } else {
                temp.delete()
                LibraryResult.Failure("Gagal mengganti ${target.name}")
            }
        } catch (e: IOException) {
            temp.delete()
            LibraryResult.Failure("Gagal menulis playlist: ${e.message}")
        } catch (e: SecurityException) {
            temp.delete()
            LibraryResult.Failure("Izin menulis playlist ditolak: ${e.message}")
        }
    }

    /** Membaca paling banyak [IptvPlaylist.MAX_BYTES]; null bila isinya lebih besar. */
    private fun readLimited(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > IptvPlaylist.MAX_BYTES) return null
            out.write(buf, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Unduh dengan batas waktu dan ukuran. Pengalihan diikuti manual (maks [MAX_REDIRECTS]); tujuan
     * harus lolos pemeriksaan URL yang sama, dan peralihan dari HTTPS ke HTTP ditolak.
     */
    internal fun download(start: URL): LibraryResult<String> {
        var current = start
        var hops = 0
        while (true) {
            val conn = try {
                current.openConnection() as HttpURLConnection
            } catch (e: IOException) {
                return LibraryResult.Failure("Gagal membuka koneksi: ${e.message}")
            }
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                val code = conn.responseCode
                if (code in 300..399 && code != 304) {
                    val location = conn.getHeaderField("Location")
                        ?: return LibraryResult.Failure("Pengalihan tanpa tujuan")
                    if (++hops > MAX_REDIRECTS) return LibraryResult.Failure("Terlalu banyak pengalihan")
                    val next = URL(current, location)
                    if (current.protocol == "https" && next.protocol != "https") {
                        return LibraryResult.Failure("Pengalihan dari HTTPS ke HTTP ditolak")
                    }
                    if (IptvPlaylist.parseUri(next.toString()) == null) {
                        return LibraryResult.Failure("Tujuan pengalihan tidak diizinkan")
                    }
                    current = next
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return LibraryResult.Failure("Server membalas HTTP $code")
                if (conn.contentLengthLong > IptvPlaylist.MAX_BYTES) {
                    return LibraryResult.Failure("Playlist terlalu besar (maks 2 MB)")
                }
                val text = conn.inputStream.use { readLimited(it) }
                    ?: return LibraryResult.Failure("Playlist terlalu besar (maks 2 MB)")
                return LibraryResult.Ok(text)
            } catch (e: IOException) {
                return LibraryResult.Failure("Gagal mengunduh: ${e.message}")
            } finally {
                conn.disconnect()
            }
        }
    }
}
