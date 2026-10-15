package com.molina.suite.core.common

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kiriman skrip dari tab Code ke Library: [name] saran nama entri, [content] isi skrip utuh. */
data class LibraryDraft(val name: String, val content: String, val serial: Long)

/**
 * Papan permintaan antar-modul: tab Code mengirim isi editor, Library (di Settings) membukanya
 * sebagai dialog "Entri baru" yang sudah terisi. Kedua sisi tidak saling mengenal kelasnya.
 * Kiriman yang datang sebelum Library dibuat tetap tersimpan sampai dikonsumsi.
 */
class LibraryDraftBoard {

    private val counter = AtomicLong()
    private val mutablePending = MutableStateFlow<LibraryDraft?>(null)

    val pending: StateFlow<LibraryDraft?> = mutablePending.asStateFlow()

    fun request(name: String, content: String) {
        mutablePending.value = LibraryDraft(name, content, counter.incrementAndGet())
    }

    /** Menandai [draft] selesai ditangani; kiriman yang lebih baru tidak ikut terhapus. */
    fun consume(draft: LibraryDraft) {
        mutablePending.compareAndSet(draft, null)
    }

    companion object {
        /** Batas isi yang dikirim; sama dengan batas berkas sinkron editor Library (1 MB). */
        const val MAX_CONTENT_CHARS = 1_000_000
    }
}
