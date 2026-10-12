package com.molina.suite.feature.settings.tools

import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.library.ScriptStager
import java.io.File

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "wma")

/** Satu file media di folder bersama. [relativePath] relatif terhadap /sdcard/Molina. */
internal data class MediaFile(val file: File, val relativePath: String) {
    /** Path yang sama dari shell host maupun dari dalam Debian. */
    val terminalPath: String get() = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/$relativePath"
    val baseName: String get() = file.nameWithoutExtension

    /** Format untuk tampilan, mis. MP4. */
    val formatLabel: String get() = file.extension.uppercase()

    val isAudio: Boolean get() = file.extension.lowercase() in AUDIO_EXTENSIONS

    /** Folder induk relatif terhadap /sdcard/Molina; "Molina" bila file ada di akar. */
    val folderLabel: String
        get() = relativePath.substringBeforeLast('/', "").ifEmpty { MolinaStorage.SHARED_DIR_NAME }
}

/** Mencari file media di folder bersama dan menyiapkan path keluaran. Memblokir I/O. */
internal object MediaFiles {

    private val extensions = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "flv", "3gp", "m4v", "ts",
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "wma"
    )
    private const val MAX_DEPTH = 4
    private const val MAX_RESULTS = 200
    private const val MAX_SUFFIX = 999

    /** File media terbaru lebih dulu; folder skrip Library dilewati. */
    fun list(): List<MediaFile> {
        val root = MolinaStorage.sharedRoot()
        if (!root.isDirectory) return emptyList()
        val found = ArrayList<File>()
        collect(root, 0, found)
        return found
            .sortedByDescending { it.lastModified() }
            .take(MAX_RESULTS)
            .map { MediaFile(it, it.relativeTo(root).path) }
    }

    private fun collect(dir: File, depth: Int, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (depth < MAX_DEPTH && child.name != ScriptStager.SCRIPTS_DIR_NAME) collect(child, depth + 1, out)
            } else if (child.extension.lowercase() in extensions) {
                out.add(child)
            }
        }
    }

    /**
     * Memastikan folder Downloads ada, lalu memilih nama keluaran yang belum dipakai
     * (nama.ext, nama (1).ext, ...). Mengembalikan path untuk terminal, atau null bila gagal.
     */
    fun prepareOutput(baseName: String, extension: String): String? {
        val dir = MolinaStorage.downloadsDir()
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return null
        var candidate = File(dir, "$baseName.$extension")
        var n = 1
        while (candidate.exists()) {
            if (n > MAX_SUFFIX) return null
            candidate = File(dir, "$baseName ($n).$extension")
            n++
        }
        return "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}/${candidate.name}"
    }
}
