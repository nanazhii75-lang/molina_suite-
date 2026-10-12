package com.molina.suite.feature.code

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ScriptRunTarget
import java.io.File

/**
 * Tampilan panel pohon direktori (isi laci kiri). Hanya menggambar baris yang diberikan
 * dan meneruskan sentuhan lewat [Callbacks]; tidak membaca berkas sendiri.
 */
internal class CodeTreePanel(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onRowClicked(row: CodeTreeRow.Entry)
        fun onChooseFolder()
        fun onChooseFile()
        fun onUpFolder()
        fun onCloseFolder()
        fun onRunFile(file: File, target: ScriptRunTarget)
    }

    private var rows: List<CodeTreeRow> = emptyList()
    private var activePath: String? = null

    private val nameView = TextView(context).apply {
        setTextColor(CodePalette.TEXT)
        textSize = 16f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val pathView = TextView(context).apply {
        setTextColor(CodePalette.TEXT_MUTED)
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.START
    }
    private val headerButtons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    /** Tombol back di kiri judul: naik ke folder induk dari folder yang sedang terbuka. */
    private val upButton = CodeUi.iconButton(
        context,
        R.drawable.ic_code_arrow_back,
        R.string.code_tree_up_folder,
        CodePalette.ACCENT
    ) { callbacks.onUpFolder() }
    private val adapter = RowAdapter()
    private val listView = ListView(context)
    private val emptyListView = TextView(context).apply {
        setText(R.string.code_picker_empty)
        setTextColor(CodePalette.TEXT_MUTED)
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    private val noFolderView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    val view: View = build()

    init {
        showNoFolder()
    }

    fun showFolder(root: File) {
        nameView.text = root.name.ifEmpty { "/" }
        pathView.text = root.path
        pathView.visibility = View.VISIBLE
        headerButtons.visibility = View.VISIBLE
        upButton.visibility = View.VISIBLE
        val parent = root.parentFile
        setUpEnabled(parent != null && parent.canRead())
        noFolderView.visibility = View.GONE
        listView.visibility = View.VISIBLE
        rows = emptyList()
        adapter.notifyDataSetChanged()
        emptyListView.visibility = View.GONE
    }

    fun showNoFolder() {
        nameView.setText(R.string.code_tree_none_title)
        pathView.visibility = View.GONE
        headerButtons.visibility = View.GONE
        upButton.visibility = View.GONE
        listView.visibility = View.GONE
        emptyListView.visibility = View.GONE
        noFolderView.visibility = View.VISIBLE
        rows = emptyList()
        adapter.notifyDataSetChanged()
    }

    fun setRows(newRows: List<CodeTreeRow>) {
        rows = newRows
        adapter.notifyDataSetChanged()
        emptyListView.visibility = if (newRows.isEmpty()) View.VISIBLE else View.GONE
    }

    fun setActivePath(path: String?) {
        if (activePath == path) return
        activePath = path
        adapter.notifyDataSetChanged()
    }

    private fun build(): View {
        headerButtons.addView(
            CodeUi.iconButton(context, R.drawable.ic_code_file, R.string.code_tree_open_file_short) {
                callbacks.onChooseFile()
            },
            LinearLayout.LayoutParams(dp(44), dp(48))
        )
        headerButtons.addView(
            CodeUi.iconButton(context, R.drawable.ic_code_folder, R.string.code_tree_change_folder) {
                callbacks.onChooseFolder()
            },
            LinearLayout.LayoutParams(dp(44), dp(48))
        )
        headerButtons.addView(
            CodeUi.iconButton(context, R.drawable.ic_code_close, R.string.code_tree_close_folder) {
                callbacks.onCloseFolder()
            },
            LinearLayout.LayoutParams(dp(44), dp(48))
        )
        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(nameView)
            addView(pathView)
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(upButton, LinearLayout.LayoutParams(dp(40), dp(48)))
            addView(titleColumn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) })
            addView(headerButtons, LinearLayout.LayoutParams(WRAP, WRAP))
        }

        listView.apply {
            adapter = this@CodeTreePanel.adapter
            divider = null
            setOnItemClickListener { _, _, position, _ ->
                (rows.getOrNull(position) as? CodeTreeRow.Entry)?.let { callbacks.onRowClicked(it) }
            }
            setOnItemLongClickListener { _, itemView, position, _ ->
                val entry = rows.getOrNull(position) as? CodeTreeRow.Entry
                if (entry == null || entry.isDirectory || !ScriptRunLine.isRunnableName(entry.file.name)) {
                    false
                } else {
                    showRunMenu(itemView, entry.file)
                    true
                }
            }
        }

        noFolderView.addView(
            TextView(context).apply {
                setText(R.string.code_tree_none_hint)
                setTextColor(CodePalette.TEXT_MUTED)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        noFolderView.addView(
            Button(context).apply {
                setText(R.string.code_tree_open_folder)
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(CodePalette.ACCENT_DARK)
                setOnClickListener { callbacks.onChooseFolder() }
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) }
        )
        noFolderView.addView(
            Button(context).apply {
                setText(R.string.code_tree_open_file)
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(CodePalette.ACCENT_DARK)
                setOnClickListener { callbacks.onChooseFile() }
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) }
        )

        val body = FrameLayout(context).apply {
            addView(listView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emptyListView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            addView(noFolderView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.BACKGROUND)
            // Laci menelan sentuhan agar tidak menembus ke editor di belakangnya.
            isClickable = true
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
    }

    /** Back abu-abu dan tidak bisa diketuk bila folder terbuka tidak punya induk yang bisa dibaca. */
    private fun setUpEnabled(enabled: Boolean) {
        upButton.isEnabled = enabled
        upButton.imageTintList = ColorStateList.valueOf(if (enabled) CodePalette.ACCENT else CodePalette.TEXT_MUTED)
    }

    private fun showRunMenu(anchor: View, file: File) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        popup.menu.add(Menu.NONE, MENU_RUN_DEBIAN, 0, R.string.code_tree_run_debian)
        popup.menu.add(Menu.NONE, MENU_RUN_HOST, 1, R.string.code_tree_run_host)
        popup.setOnMenuItemClickListener { item ->
            val target = when (item.itemId) {
                MENU_RUN_DEBIAN -> ScriptRunTarget.DEBIAN
                MENU_RUN_HOST -> ScriptRunTarget.HOST
                else -> null
            }
            if (target != null) callbacks.onRunFile(file, target)
            target != null
        }
        popup.show()
    }

    private fun dp(value: Int): Int = CodeUi.dp(context, value)

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): CodeTreeRow = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun areAllItemsEnabled(): Boolean = false
        override fun isEnabled(position: Int): Boolean = rows.getOrNull(position) is CodeTreeRow.Entry

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: createRow()
            val chevron = row.getChildAt(0) as ImageView
            val icon = row.getChildAt(1) as ImageView
            val name = row.getChildAt(2) as TextView
            val item = rows[position]
            row.setPadding(dp(8) + item.depth * dp(14), dp(7), dp(8), dp(7))
            when (item) {
                is CodeTreeRow.Entry -> {
                    val active = !item.isDirectory && item.file.path == activePath
                    chevron.visibility = if (item.isDirectory) View.VISIBLE else View.INVISIBLE
                    chevron.setImageResource(
                        if (item.expanded) R.drawable.ic_code_arrow_down else R.drawable.ic_code_arrow_right
                    )
                    icon.visibility = View.VISIBLE
                    icon.setImageResource(if (item.isDirectory) R.drawable.ic_code_folder else R.drawable.ic_code_file)
                    icon.imageTintList = ColorStateList.valueOf(
                        if (item.isDirectory) CodePalette.FOLDER else CodePalette.TEXT_MUTED
                    )
                    name.text = item.file.name
                    name.setTextColor(if (active) CodePalette.ACCENT else CodePalette.TEXT)
                    row.setBackgroundColor(if (active) CodePalette.PANEL else Color.TRANSPARENT)
                }
                is CodeTreeRow.More -> {
                    chevron.visibility = View.INVISIBLE
                    icon.visibility = View.INVISIBLE
                    name.text = context.getString(R.string.code_tree_more, item.hidden)
                    name.setTextColor(CodePalette.TEXT_MUTED)
                    row.setBackgroundColor(Color.TRANSPARENT)
                }
                is CodeTreeRow.Unreadable -> {
                    chevron.visibility = View.INVISIBLE
                    icon.visibility = View.INVISIBLE
                    name.setText(R.string.code_tree_unreadable)
                    name.setTextColor(CodePalette.TEXT_MUTED)
                    row.setBackgroundColor(Color.TRANSPARENT)
                }
            }
            return row
        }

        private fun createRow(): LinearLayout {
            val chevron = ImageView(context).apply {
                imageTintList = ColorStateList.valueOf(CodePalette.TEXT_MUTED)
            }
            val icon = ImageView(context)
            val name = TextView(context).apply {
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(chevron, LinearLayout.LayoutParams(dp(18), dp(18)))
                addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginStart = dp(4) })
                addView(name, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) })
            }
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val MENU_RUN_DEBIAN = 1
        const val MENU_RUN_HOST = 2
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
