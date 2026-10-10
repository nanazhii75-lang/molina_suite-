package com.molina.suite.feature.code

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal sealed interface CodeFileLoad {
    data class Loaded(val text: String, val charset: Charset, val hasBom: Boolean) : CodeFileLoad
    data class TooLarge(val sizeBytes: Long, val limitBytes: Long) : CodeFileLoad
    data object Binary : CodeFileLoad
    data class Failed(val reason: String) : CodeFileLoad
}

internal sealed interface CodeFileSave {
    data object Saved : CodeFileSave
    data class Failed(val reason: String) : CodeFileSave
}

/** Satu-satunya tempat baca dan tulis berkas editor; tidak mengenal UI maupun Sora. */
internal class CodeFileRepository {

    private val maxBytes = MAX_FILE_BYTES

    suspend fun load(file: File): CodeFileLoad = withContext(Dispatchers.IO) {
        try {
            when {
                !file.isFile -> CodeFileLoad.Failed("Bukan berkas biasa: ${file.path}")
                !file.canRead() -> CodeFileLoad.Failed("Berkas tidak bisa dibaca: ${file.path}")
                file.length() > maxBytes -> CodeFileLoad.TooLarge(file.length(), maxBytes)
                else -> decode(file.readBytes())
            }
        } catch (e: IOException) {
            CodeFileLoad.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            CodeFileLoad.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun save(file: File, text: String, charset: Charset, withBom: Boolean): CodeFileSave =
        withContext(Dispatchers.IO) {
            try {
                val encoded = encode(text, charset)
                writeAtomically(file, if (withBom) BOM + encoded else encoded)
                CodeFileSave.Saved
            } catch (e: CharacterCodingException) {
                CodeFileSave.Failed("Teks berisi karakter yang tidak bisa disimpan dalam ${charset.name()}")
            } catch (e: IOException) {
                CodeFileSave.Failed(e.message ?: e.javaClass.simpleName)
            } catch (e: SecurityException) {
                CodeFileSave.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    private fun decode(bytes: ByteArray): CodeFileLoad {
        if (looksBinary(bytes)) return CodeFileLoad.Binary
        val hasBom = bytes.size >= BOM.size &&
            bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]
        val offset = if (hasBom) BOM.size else 0
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val text = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            CodeFileLoad.Loaded(text, Charsets.UTF_8, hasBom)
        } catch (e: CharacterCodingException) {
            CodeFileLoad.Loaded(String(bytes, Charsets.ISO_8859_1), Charsets.ISO_8859_1, false)
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, BINARY_SCAN_BYTES)
        for (i in 0 until limit) {
            if (bytes[i] == 0.toByte()) return true
        }
        return false
    }

    private fun encode(text: String, charset: Charset): ByteArray {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val buffer = encoder.encode(CharBuffer.wrap(text))
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    /** Tulis ke berkas sementara lalu ganti nama; bila gagal, tulis langsung. */
    private fun writeAtomically(target: File, payload: ByteArray) {
        val dir = target.parentFile ?: throw IOException("Folder induk tidak ditemukan: ${target.path}")
        val temp = File(dir, target.name + TEMP_SUFFIX)
        try {
            FileOutputStream(temp).use { out ->
                out.write(payload)
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                FileOutputStream(target).use { out ->
                    out.write(payload)
                    out.fd.sync()
                }
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private companion object {
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
        const val BINARY_SCAN_BYTES = 8000
        const val TEMP_SUFFIX = ".molina-tmp"
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
