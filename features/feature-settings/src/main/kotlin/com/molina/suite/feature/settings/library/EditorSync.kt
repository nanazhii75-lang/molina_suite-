package com.molina.suite.feature.settings.library

import java.io.File
import java.io.IOException

/**
 * Sinkron balik dari code editor ke Library. Entri yang pernah dikirim ke editor punya salinan di
 * [ScriptStager.editFile]. Bila salinan lebih baru dari [LibraryEntry.updatedAt] dan isinya berbeda,
 * isinya diimpor ke entri. Salinan yang tidak valid (kosong, terlalu panjang, terlalu besar) ditolak
 * dengan alasan, dan entri lama tetap aman. Panggil dari Dispatchers.IO.
 */
internal object EditorSync {

    private const val MAX_BYTES = 1_000_000L

    /** Hasil satu putaran sinkron: pasangan (nama entri, jumlah baris) dan (nama entri, alasan ditolak). */
    class Report(val imported: List<Pair<String, Int>>, val rejected: List<Pair<String, String>>) {
        companion object {
            val EMPTY = Report(emptyList(), emptyList())
        }
    }

    /** Waktu ubah salinan yang sudah ditolak, supaya penolakan yang sama tidak diumumkan berulang. */
    private val rejectedAt = HashMap<String, Long>()

    fun apply(repository: LibraryRepository, entries: List<LibraryEntry>): Report = synchronized(rejectedAt) {
        val imported = ArrayList<Pair<String, Int>>()
        val rejected = ArrayList<Pair<String, String>>()
        for (entry in entries) {
            val file = ScriptStager.editFile(entry.id) ?: continue
            val modified = modifiedOf(file)
            if (modified <= entry.updatedAt || rejectedAt[entry.id] == modified) continue
            val text = when (val read = readNormalized(file)) {
                is LibraryResult.Ok -> read.value
                is LibraryResult.Failure -> {
                    rejectedAt[entry.id] = modified
                    rejected.add(entry.name to read.reason)
                    continue
                }
            }
            if (text == entry.content) continue
            when (val saved = repository.upsert(entry.copy(content = text))) {
                is LibraryResult.Ok -> {
                    rejectedAt.remove(entry.id)
                    imported.add(entry.name to LibraryLimits.lineCount(text))
                }
                is LibraryResult.Failure -> {
                    rejectedAt[entry.id] = modified
                    rejected.add(entry.name to saved.reason)
                }
            }
        }
        if (imported.isEmpty() && rejected.isEmpty()) Report.EMPTY else Report(imported, rejected)
    }

    private fun modifiedOf(file: File): Long = try {
        if (file.isFile) file.lastModified() else 0L
    } catch (e: SecurityException) {
        0L
    }

    /** Isi berkas dengan baris baru seragam dan tanpa spasi di ujung, atau alasan penolakan. */
    private fun readNormalized(file: File): LibraryResult<String> {
        return try {
            if (file.length() > MAX_BYTES) return LibraryResult.Failure("berkas lebih dari 1 MB")
            val text = file.readText(Charsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').trim()
            val lines = LibraryLimits.lineCount(text)
            when {
                text.isEmpty() -> LibraryResult.Failure("isi berkas kosong")
                lines > LibraryLimits.MAX_SCRIPT_LINES ->
                    LibraryResult.Failure("melebihi ${LibraryLimits.MAX_SCRIPT_LINES} baris ($lines baris)")
                else -> LibraryResult.Ok(text)
            }
        } catch (e: IOException) {
            LibraryResult.Failure("gagal membaca berkas: ${e.message}")
        } catch (e: SecurityException) {
            LibraryResult.Failure("izin membaca berkas ditolak: ${e.message}")
        }
    }
}
