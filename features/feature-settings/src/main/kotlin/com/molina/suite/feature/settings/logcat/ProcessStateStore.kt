package com.molina.suite.feature.settings.logcat

import java.io.File

/** Menyimpan dan membaca satu [ProcessRecord] di [file]; kegagalan baca/tulis tidak melempar. */
internal class ProcessStateStore(private val file: File) {

    /** Catatan terakhir yang tersimpan; null bila belum ada atau rusak. */
    fun read(): ProcessRecord? = StateFile.read(file)?.let { ProcessRecord.decode(it) }

    /** False bila catatan tidak bisa ditulis. */
    fun write(record: ProcessRecord): Boolean = StateFile.write(file, record.encode())
}
