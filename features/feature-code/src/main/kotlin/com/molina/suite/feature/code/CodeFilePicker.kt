package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Dialog pemilih berkas sederhana yang dibatasi di dalam [root]. */
internal class CodeFilePicker(
    private val context: Context,
    private val root: File,
    private val onPicked: (File) -> Unit
) {
    private sealed interface Entry {
        val label: String

        data class Up(val target: File) : Entry {
            override val label: String get() = "../"
        }

        data class Item(val file: File) : Entry {
            override val label: String get() = if (file.isDirectory) file.name + "/" else file.name
        }
    }

    private var entries: List<Entry> = emptyList()

    fun show() {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val pathView = TextView(context).apply {
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1)
        val listView = ListView(context).apply { this.adapter = adapter }
        val emptyView = TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val listArea = FrameLayout(context).apply {
            addView(listView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emptyView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(pathView, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(listArea, LinearLayout.LayoutParams(MATCH, dp(LIST_HEIGHT_DP)))
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.code_picker_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun render(dir: File) {
            pathView.text = dir.path
            val children: Array<File>? = try {
                dir.listFiles()
            } catch (e: SecurityException) {
                null
            }
            val list = ArrayList<Entry>()
            val parent = dir.parentFile
            if (dir != root && parent != null) list.add(Entry.Up(parent))
            children
                ?.sortedWith(
                    compareByDescending<File> { it.isDirectory }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
                )
                ?.forEach { list.add(Entry.Item(it)) }
            entries = list
            adapter.clear()
            adapter.addAll(list.map { it.label })
            adapter.notifyDataSetChanged()
            val noChildren = children == null || children.isEmpty()
            emptyView.visibility = if (noChildren) View.VISIBLE else View.GONE
            emptyView.setText(
                if (children == null) R.string.code_picker_unreadable else R.string.code_picker_empty
            )
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            when (val entry = entries.getOrNull(position)) {
                is Entry.Up -> render(entry.target)
                is Entry.Item ->
                    if (entry.file.isDirectory) {
                        render(entry.file)
                    } else {
                        dialog.dismiss()
                        onPicked(entry.file)
                    }
                null -> Unit
            }
        }

        render(root)
        dialog.show()
    }

    private companion object {
        const val LIST_HEIGHT_DP = 320
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
