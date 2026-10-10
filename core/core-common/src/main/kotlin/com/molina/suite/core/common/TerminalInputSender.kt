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
}
