package com.molina.suite.feature.code

import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Kata sandi acak untuk code-server. Disimpan di direktori privat aplikasi
 * (di dalam rootfs) dan tidak pernah ditampilkan di UI.
 */
internal class CodeServerCredentials(private val file: File) {

    /** Mengembalikan kata sandi yang tersimpan, atau membuat yang baru bila belum ada. */
    @Synchronized
    fun ensure(): String {
        readExisting()?.let { return it }
        val password = generate()
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Tidak bisa membuat $parent")
        }
        file.writeText(password)
        restrictToOwner()
        return password
    }

    private fun readExisting(): String? =
        try {
            file.readText().trim().takeIf { it.length >= MIN_LENGTH }
        } catch (e: IOException) {
            null
        }

    private fun generate(): String {
        val random = SecureRandom()
        return buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }
    }

    private fun restrictToOwner() {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val LENGTH = 32
        const val MIN_LENGTH = 16
    }
}
