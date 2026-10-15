package com.molina.suite.feature.settings.library

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.molina.suite.feature.settings.R
import java.io.File
import java.util.Date
import java.util.Locale

/**
 * Pemilih folder layar penuh untuk placeholder {DIR} di Library. Tampilannya mengikuti pemilih
 * folder editor Code (header, breadcrumb, daftar folder, tombol melayang), tetapi berdiri sendiri
 * karena Settings tidak boleh bergantung pada modul Code. Hasilnya berupa [File] lewat [onPicked].
 */
internal class FolderPicker(
    private val context: Context,
    startDir: File,
    private val onPicked: (File) -> Unit
) {
    private var currentDir: File = startDir
    private var items: List<File> = emptyList()

    private val adapter = FolderAdapter()
    private lateinit var dialog: Dialog
    private lateinit var breadcrumb: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var emptyView: TextView
    private lateinit var listView: ListView

    fun show() {
        dialog = Dialog(context, android.R.style.Theme_Material_NoActionBar)
        dialog.setContentView(buildContent())
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                goUpOrDismiss()
                true
            } else {
                false
            }
        }
        render(currentDir)
        dialog.show()
    }

    private fun buildContent(): View {
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(PANEL)
            setPadding(dp(4), 0, dp(4), 0)
            addView(
                iconButton(R.drawable.ic_folder_picker_back, R.string.folder_picker_up, TEXT) { goUpOrDismiss() },
                LinearLayout.LayoutParams(dp(48), dp(56))
            )
            addView(
                TextView(context).apply {
                    setText(R.string.folder_picker_title)
                    setTextColor(TEXT)
                    textSize = 18f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        breadcrumb = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        breadcrumbScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(BACKGROUND)
            addView(breadcrumb, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        }

        listView = ListView(context).apply {
            this.adapter = this@FolderPicker.adapter
            divider = null
            clipToPadding = false
            setPadding(0, 0, 0, dp(150))
            setOnItemClickListener { _, _, position, _ ->
                items.getOrNull(position)?.let { render(it) }
            }
        }
        emptyView = TextView(context).apply {
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val listArea = FrameLayout(context).apply {
            addView(listView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emptyView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            addView(
                buildFloatingButtons(),
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                    setMargins(0, 0, dp(12), dp(12))
                }
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(breadcrumbScroll, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(listArea, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
    }

    private fun buildFloatingButtons(): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        column.addView(floatingButton(R.drawable.ic_folder_picker_new, R.string.folder_picker_new_folder) { promptNewFolder() })
        column.addView(floatingButton(R.drawable.ic_folder_picker_folder, R.string.folder_picker_use_folder) { finish(currentDir) })
        column.addView(floatingButton(R.drawable.ic_folder_picker_close, R.string.folder_picker_cancel) { dialog.dismiss() })
        return column
    }

    private fun floatingButton(icon: Int, label: Int, onClick: () -> Unit): View {
        val chip = TextView(context).apply {
            setText(label)
            setTextColor(TEXT)
            textSize = 11f
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(PANEL)
                cornerRadius = dp(6).toFloat()
            }
        }
        val circle = iconButton(icon, label, Color.WHITE) { onClick() }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ACCENT_DARK)
            }
            elevation = dp(3).toFloat()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
            addView(circle, LinearLayout.LayoutParams(dp(36), dp(36)))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(8) }
        }
    }

    private fun render(dir: File) {
        currentDir = dir
        renderBreadcrumb(dir)
        val children: Array<File>? = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            null
        }
        items = children.orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        adapter.notifyDataSetChanged()
        listView.setSelection(0)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(if (children == null) R.string.folder_picker_unreadable else R.string.folder_picker_empty)
    }

    private fun renderBreadcrumb(dir: File) {
        breadcrumb.removeAllViews()
        val segments = ArrayList<Pair<String, File>>()
        var node: File? = dir
        while (node != null) {
            val name = node.name
            segments.add(0, (if (name.isEmpty()) "/" else name) to node)
            node = node.parentFile
        }
        segments.forEachIndexed { index, (label, target) ->
            if (index > 0) {
                breadcrumb.addView(TextView(context).apply {
                    text = "\u203A"
                    setTextColor(TEXT_MUTED)
                    setPadding(dp(4), 0, dp(4), 0)
                })
            }
            breadcrumb.addView(TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(if (index == segments.lastIndex) ACCENT else TEXT)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener { render(target) }
            })
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun goUpOrDismiss() {
        val parent = currentDir.parentFile
        if (parent == null) dialog.dismiss() else render(parent)
    }

    private fun finish(folder: File) {
        dialog.dismiss()
        onPicked(folder)
    }

    private fun promptNewFolder() {
        val input = EditText(context).apply {
            setHint(R.string.folder_picker_folder_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.folder_picker_new_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> createFolder(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createFolder(name: String) {
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) {
            toast(context.getString(R.string.folder_picker_name_invalid))
            return
        }
        val folder = File(currentDir, name)
        val created = try {
            folder.mkdir()
        } catch (e: SecurityException) {
            false
        }
        if (created || folder.isDirectory) {
            render(currentDir)
        } else {
            toast(context.getString(R.string.folder_picker_folder_failed, name))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun iconButton(icon: Int, description: Int, tint: Int, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(tint)
            val label = context.getString(description)
            contentDescription = label
            tooltipText = label
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private inner class FolderAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): File = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val folder = items[position]
            val row = (convertView as? LinearLayout) ?: createRow()
            val texts = row.getChildAt(1) as LinearLayout
            (texts.getChildAt(0) as TextView).text = folder.name
            (texts.getChildAt(1) as TextView).text =
                DateFormat.getMediumDateFormat(context).format(Date(folder.lastModified()))
            return row
        }

        private fun createRow(): LinearLayout {
            val icon = ImageView(context).apply {
                setImageResource(R.drawable.ic_folder_picker_folder)
                imageTintList = ColorStateList.valueOf(FOLDER)
            }
            val name = TextView(context).apply {
                setTextColor(TEXT)
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
            val meta = TextView(context).apply {
                setTextColor(TEXT_MUTED)
                textSize = 12f
                maxLines = 1
            }
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(name)
                addView(meta)
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
                addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)))
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
            }
        }
    }

    companion object {
        private val BACKGROUND: Int = 0xFF2B2B2B.toInt()
        private val PANEL: Int = 0xFF313335.toInt()
        private val TEXT: Int = 0xFFA9B7C6.toInt()
        private val TEXT_MUTED: Int = 0xFF808080.toInt()
        private val ACCENT: Int = 0xFFFF9800.toInt()
        private val ACCENT_DARK: Int = 0xFF8D5A2B.toInt()
        private val FOLDER: Int = 0xFF7FB8E8.toInt()

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Folder terdekat yang ada dari path yang diketik pengguna; /sdcard bila tidak ada. */
        fun startFrom(typed: String): File {
            var node: File? = if (typed.isBlank()) null else File(typed.trim())
            while (node != null && !node.isDirectory) node = node.parentFile
            return node ?: File("/sdcard")
        }
    }
}
