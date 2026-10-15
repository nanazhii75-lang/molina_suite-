package com.molina.suite.core.common

/** Hasil pengiriman teks ke sesi terminal. */
sealed interface TerminalInputResult {
    object Sent : TerminalInputResult
    data class Rejected(val reason: String) : TerminalInputResult
}

/**
 * Kontrak antara modul fitur dan engine terminal untuk mengetik teks ke sesi
 * terminal yang aktif, lalu menekan Enter. Berbeda dengan [ShellCommandRunner]
 * yang berjalan di latar belakang, ini tampil interaktif di tab Terminal.
 */
interface TerminalInputSender {
    fun sendLine(line: String): TerminalInputResult

    /**
     * Mengetik [line] ke tab terminal yang terikat ke folder [workdir]. Bila sudah ada tab
     * untuk folder itu dan shell-nya idle, tab itu dipakai ulang; bila tidak ada, atau sedang
     * menjalankan proses, tab baru berlabel [label] dibuat di [workdir] tanpa menyentuh tab
     * lain. Implementasi bawaan jatuh ke [sendLine] (sesi aktif).
     */
    fun sendLineInWorkdir(workdir: String, label: String, line: String): TerminalInputResult =
        sendLine(line)
}
