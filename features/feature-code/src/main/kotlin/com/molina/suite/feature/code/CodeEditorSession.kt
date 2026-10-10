package com.molina.suite.feature.code

import android.util.Log
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sesi penyuntingan seumur proses: memegang dokumen yang terbuka dan
 * menghubungkannya ke [CodeEditor] yang sedang terpasang. View boleh dibuat
 * ulang (rotasi, pindah tab); teks dipulihkan dari snapshot. Semua metode
 * dipanggil dari thread utama.
 */
internal class CodeEditorSession(
    private val files: CodeFileRepository,
    private val textMate: CodeTextMate
) {
    private class Snapshot(val text: String, val line: Int, val column: Int)

    private val mutableDocument = MutableStateFlow<CodeDocument?>(null)
    val document: StateFlow<CodeDocument?> = mutableDocument.asStateFlow()

    private var editor: CodeEditor? = null
    private var snapshot: Snapshot? = null
    private var applyingText = false
    private var revision = 0

    /** Pasang editor baru; memulihkan dokumen yang sedang terbuka bila ada. */
    suspend fun attach(target: CodeEditor) {
        editor = target
        target.subscribeEvent(
            ContentChangeEvent::class.java,
            EventReceiver<ContentChangeEvent> { event, _ ->
                if (target === editor && !applyingText &&
                    event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT
                ) {
                    onContentEdited()
                }
            }
        )
        val current = mutableDocument.value ?: return
        snapshot?.let { applyText(target, it.text, it.line, it.column) }
        applyHighlighting(target, current.file)
    }

    /** Lepas editor; teks disimpan sebagai snapshot untuk editor berikutnya. */
    fun detach(target: CodeEditor) {
        if (editor !== target) return
        if (mutableDocument.value != null) {
            val cursor = target.cursor
            snapshot = Snapshot(target.text.toString(), cursor.leftLine, cursor.leftColumn)
        }
        editor = null
    }

    suspend fun open(file: File): CodeFileLoad {
        val result = files.load(file)
        if (result !is CodeFileLoad.Loaded) return result
        revision++
        mutableDocument.value = CodeDocument(file, result.charset, result.hasBom, dirty = false)
        snapshot = Snapshot(result.text, 0, 0)
        val target = editor
        if (target != null) {
            applyText(target, result.text, 0, 0)
            applyHighlighting(target, file)
        }
        return result
    }

    suspend fun save(): CodeFileSave {
        val current = mutableDocument.value
            ?: return CodeFileSave.Failed("Tidak ada berkas yang terbuka")
        val text = editor?.text?.toString() ?: snapshot?.text
            ?: return CodeFileSave.Failed("Isi editor tidak tersedia")
        val savedRevision = revision
        val result = files.save(current.file, text, current.charset, current.hasBom)
        if (result is CodeFileSave.Saved && revision == savedRevision) {
            mutableDocument.update {
                if (it != null && it.file == current.file) it.copy(dirty = false) else it
            }
        }
        return result
    }

    fun close() {
        revision++
        mutableDocument.value = null
        snapshot = null
        editor?.let { applyText(it, "", 0, 0) }
    }

    fun undo() {
        editor?.let { if (it.canUndo()) it.undo() }
    }

    fun redo() {
        editor?.let { if (it.canRedo()) it.redo() }
    }

    fun moveCursorLeft() {
        editor?.moveSelection(SelectionMovement.LEFT)
    }

    fun moveCursorRight() {
        editor?.moveSelection(SelectionMovement.RIGHT)
    }

    fun paste() {
        editor?.pasteText()
    }

    private fun onContentEdited() {
        revision++
        mutableDocument.update { if (it != null && !it.dirty) it.copy(dirty = true) else it }
    }

    private fun applyText(target: CodeEditor, text: String, line: Int, column: Int) {
        applyingText = true
        try {
            target.setText(text)
        } finally {
            applyingText = false
        }
        val content = target.text
        val safeLine = line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
        val safeColumn = column.coerceIn(0, content.getColumnCount(safeLine))
        try {
            target.setSelection(safeLine, safeColumn)
        } catch (e: IndexOutOfBoundsException) {
            Log.w(TAG, "Posisi kursor tidak valid: $safeLine:$safeColumn", e)
        }
    }

    private suspend fun applyHighlighting(target: CodeEditor, file: File) {
        withContext(Dispatchers.Default) { textMate.warmUp() }
        if (target !== editor) return
        val highlighting = textMate.prepare(file.name)
        highlighting.scheme?.let { target.setColorScheme(it) }
        target.setEditorLanguage(highlighting.language)
    }

    private companion object {
        const val TAG = "CodeEditorSession"
    }
}
