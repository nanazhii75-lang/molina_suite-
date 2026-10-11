package com.molina.suite.core.common

/** Alat yang dibutuhkan sebuah fitur: [id] di katalog paket dan [name] untuk ditampilkan. */
data class ToolRequirement(val id: String, val name: String)

/** Hasil pemeriksaan alat yang dibutuhkan sebuah fitur. */
sealed interface ToolReadiness {

    /** Semua alat terpasang. */
    object Ready : ToolReadiness

    /** Debian belum terpasang, jadi belum ada alat yang bisa dipakai. */
    object NoDebian : ToolReadiness

    /** Status tidak terbaca; pemanggil sebaiknya lanjut dan biarkan galat aslinya yang tampil. */
    object Unknown : ToolReadiness

    /** Terdaftar di katalog tetapi belum terpasang. */
    data class Missing(val tools: List<ToolRequirement>) : ToolReadiness

    /** Id yang tidak ada di katalog: kesalahan program, bukan sesuatu yang bisa dipasang pengguna. */
    data class Unregistered(val ids: List<String>) : ToolReadiness
}

/**
 * Kontrak antara modul fitur dan pemilik katalog paket (modul Settings) untuk memeriksa alat
 * tanpa mengenal kelas katalog atau detektornya.
 */
interface ToolReadinessChecker {

    /** Memeriksa [toolIds]; id yang tidak dikenal katalog dilaporkan sebagai [ToolReadiness.Unregistered]. */
    suspend fun check(toolIds: List<String>): ToolReadiness
}
