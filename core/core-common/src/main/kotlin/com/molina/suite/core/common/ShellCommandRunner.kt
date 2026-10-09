package com.molina.suite.core.common

/**
 * Perintah shell yang dijalankan di konteks engine terminal.
 *
 * [executable] harus path absolut ke berkas yang bisa dieksekusi.
 * [label] dipakai engine untuk notifikasi dan log.
 */
data class ShellCommand(
    val executable: String,
    val arguments: List<String> = emptyList(),
    val workingDirectory: String? = null,
    val label: String
)

/** Hasil akhir sebuah [ShellCommand]. */
sealed interface ShellResult {
    /** Proses selesai. [exitCode] bukan nol berarti perintah itu sendiri gagal. */
    data class Finished(val exitCode: Int, val stdout: String, val stderr: String) : ShellResult

    /** Perintah tidak bisa dijalankan atau engine tidak melaporkan kode keluar. */
    data class Failed(val reason: String) : ShellResult
}

/**
 * Kontrak antara modul fitur dan engine terminal untuk menjalankan perintah
 * tanpa mengenal kelas engine.
 */
interface ShellCommandRunner {
    /**
     * Menjalankan [command] di latar belakang dan menunggu sampai prosesnya
     * berakhir. Untuk proses berumur panjang, panggil dari coroutine sendiri.
     *
     * Membatalkan coroutine hanya berhenti menunggu hasil; proses yang sudah
     * berjalan tidak dihentikan. Hentikan prosesnya lewat perintah terpisah.
     */
    suspend fun runInBackground(command: ShellCommand): ShellResult
}
