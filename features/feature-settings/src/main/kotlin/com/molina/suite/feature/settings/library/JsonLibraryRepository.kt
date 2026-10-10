package com.molina.suite.feature.settings.library

import com.molina.suite.core.storage.MolinaStorage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Menyimpan Library sebagai satu berkas JSON. Penulisan lewat berkas sementara
 * lalu rename, sehingga berkas lama tidak rusak bila proses terhenti di tengah.
 * Berkas yang tidak terbaca tidak pernah ditimpa diam-diam: operasi tulis gagal
 * dengan alasan yang jelas supaya data pengguna tidak hilang.
 */
class JsonLibraryRepository(private val file: File) : LibraryRepository {

    private val lock = Any()

    override fun isInitialized(): Boolean = file.exists()

    override fun list(): LibraryResult<List<LibraryEntry>> = synchronized(lock) { read() }

    override fun upsert(entry: LibraryEntry): LibraryResult<Unit> = synchronized(lock) {
        val clean = entry.copy(
            name = entry.name.trim(),
            content = entry.content.trim(),
            note = entry.note.trim()
        )
        val problem = validate(clean)
        if (problem != null) return@synchronized LibraryResult.Failure(problem)
        when (val current = read()) {
            is LibraryResult.Failure -> current
            is LibraryResult.Ok -> {
                val updated = current.value.toMutableList()
                val index = updated.indexOfFirst { it.id == clean.id }
                if (index >= 0) updated[index] = clean else updated.add(clean)
                write(updated)
            }
        }
    }

    override fun remove(id: String): LibraryResult<Unit> = synchronized(lock) {
        when (val current = read()) {
            is LibraryResult.Failure -> current
            is LibraryResult.Ok -> {
                val updated = current.value.filterNot { it.id == id }
                if (updated.size == current.value.size) {
                    LibraryResult.Failure("Entri tidak ditemukan")
                } else {
                    write(updated)
                }
            }
        }
    }

    override fun addMissing(entries: List<LibraryEntry>): LibraryResult<Int> = synchronized(lock) {
        when (val current = read()) {
            is LibraryResult.Failure -> current
            is LibraryResult.Ok -> {
                val known = current.value.map { it.id }.toHashSet()
                val additions = entries.filter { known.add(it.id) }
                if (additions.isEmpty()) return@synchronized LibraryResult.Ok(0)
                when (val written = write(current.value + additions)) {
                    is LibraryResult.Failure -> written
                    is LibraryResult.Ok -> LibraryResult.Ok(additions.size)
                }
            }
        }
    }

    private fun validate(entry: LibraryEntry): String? = when {
        entry.id.isBlank() -> "ID entri tidak boleh kosong"
        entry.name.isEmpty() -> "Nama entri tidak boleh kosong"
        entry.content.isEmpty() -> "Isi entri tidak boleh kosong"
        else -> null
    }

    private fun read(): LibraryResult<List<LibraryEntry>> {
        if (!file.exists()) return LibraryResult.Ok(emptyList())
        return try {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return LibraryResult.Ok(emptyList())
            val array = JSONObject(text).getJSONArray(KEY_ENTRIES)
            val entries = ArrayList<LibraryEntry>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                entries.add(
                    LibraryEntry(
                        id = item.getString(KEY_ID),
                        name = item.getString(KEY_NAME),
                        content = item.getString(KEY_CONTENT),
                        note = item.optString(KEY_NOTE, ""),
                        runIn = RunMode.fromKey(item.optString(KEY_RUN_IN, RunMode.HOST.key)),
                        builtin = item.optBoolean(KEY_BUILTIN, false)
                    )
                )
            }
            LibraryResult.Ok(entries)
        } catch (e: JSONException) {
            LibraryResult.Failure("Berkas library rusak (${file.path}): ${e.message}")
        } catch (e: IOException) {
            LibraryResult.Failure("Gagal membaca ${file.path}: ${e.message}")
        } catch (e: SecurityException) {
            LibraryResult.Failure("Izin membaca ${file.path} ditolak: ${e.message}")
        }
    }

    private fun write(entries: List<LibraryEntry>): LibraryResult<Unit> {
        val parent = file.absoluteFile.parentFile
        val temp = File(parent, file.name + ".tmp")
        return try {
            if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                return LibraryResult.Failure(
                    "Gagal membuat folder ${parent.path}; izin penyimpanan belum diberikan"
                )
            }
            val array = JSONArray()
            for (entry in entries) {
                array.put(
                    JSONObject()
                        .put(KEY_ID, entry.id)
                        .put(KEY_NAME, entry.name)
                        .put(KEY_CONTENT, entry.content)
                        .put(KEY_NOTE, entry.note)
                        .put(KEY_RUN_IN, entry.runIn.key)
                        .put(KEY_BUILTIN, entry.builtin)
                )
            }
            val root = JSONObject().put(KEY_VERSION, SCHEMA_VERSION).put(KEY_ENTRIES, array)
            temp.writeText(root.toString(2), Charsets.UTF_8)
            if (temp.renameTo(file)) {
                LibraryResult.Ok(Unit)
            } else {
                temp.delete()
                LibraryResult.Failure("Gagal mengganti berkas ${file.path}")
            }
        } catch (e: IOException) {
            temp.delete()
            LibraryResult.Failure("Gagal menulis ${file.path}: ${e.message}")
        } catch (e: JSONException) {
            temp.delete()
            LibraryResult.Failure("Gagal menyusun data library: ${e.message}")
        } catch (e: SecurityException) {
            temp.delete()
            LibraryResult.Failure("Izin menulis ${file.path} ditolak: ${e.message}")
        }
    }

    companion object {
        const val LIBRARY_FILE_NAME = "library.json"
        private const val SCHEMA_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_CONTENT = "content"
        private const val KEY_NOTE = "note"
        private const val KEY_RUN_IN = "runIn"
        private const val KEY_BUILTIN = "builtin"

        /** Lokasi standar: library.json di folder bersama molina-suite. */
        fun createDefault(): JsonLibraryRepository =
            JsonLibraryRepository(File(MolinaStorage.sharedRoot(), LIBRARY_FILE_NAME))
    }
}
