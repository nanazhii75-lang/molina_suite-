package com.molina.suite.feature.code

import android.content.Context
import android.view.View
import com.molina.suite.core.common.ScriptRunTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Penghubung [CodeWorkspace], model [CodeFolderTree], dan [CodeTreePanel]. Fragment hanya
 * memberi tahu folder yang aktif lewat [show]; pembukaan berkas dan pemilihan folder
 * diteruskan ke [Host] sehingga controller tidak mengenal editor.
 */
internal class CodeTreeController(
    context: Context,
    private val workspace: CodeWorkspace,
    private val host: Host
) : CodeTreePanel.Callbacks {

    interface Host {
        fun onOpenFile(file: File)
        fun onChooseFolder()
        fun onChooseFile()
        fun onRunFile(file: File, target: ScriptRunTarget)
    }

    private val panel = CodeTreePanel(context, this)
    private var scope: CoroutineScope? = null
    private var tree: CodeFolderTree? = null
    private var loadJob: Job? = null

    val view: View get() = panel.view

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    fun show(tree: CodeFolderTree?) {
        this.tree = tree
        if (tree == null) {
            loadJob?.cancel()
            panel.showNoFolder()
            return
        }
        panel.showFolder(tree.root)
        reload()
    }

    /** Baca ulang isi folder yang terbuka; hasil yang sudah usang dibuang. */
    fun reload() {
        val current = tree ?: return
        val owner = scope ?: return
        loadJob?.cancel()
        loadJob = owner.launch {
            val rows = current.rows()
            if (tree === current) panel.setRows(rows)
        }
    }

    fun setActiveFile(file: File?) = panel.setActivePath(file?.path)

    /** Gulir ke berkas aktif begitu isi pohon berikutnya tampil. */
    fun requestReveal() = panel.requestReveal()

    override fun onRowClicked(row: CodeTreeRow.Entry) {
        if (row.isDirectory) {
            tree?.toggle(row.file)
            reload()
        } else {
            host.onOpenFile(row.file)
        }
    }

    override fun onChooseFolder() = host.onChooseFolder()

    override fun onChooseFile() = host.onChooseFile()

    override fun onRunFile(file: File, target: ScriptRunTarget) = host.onRunFile(file, target)

    /** Jadikan folder induk sebagai folder kerja; folder induk yang tidak terbaca diabaikan. */
    override fun onUpFolder() {
        val parent = tree?.root?.parentFile ?: return
        if (parent.isDirectory && parent.canRead()) workspace.open(parent)
    }

    override fun onCloseFolder() = workspace.close()
}
