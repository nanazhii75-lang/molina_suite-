package com.molina.suite.feature.code

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Date
import java.util.Locale

internal enum class CodePickerMode { OPEN, SAVE_AS }

/**
 * Pengelola berkas layar penuh untuk "Buka" dan "Simpan sebagai": breadcrumb path,
 * urutkan, favorit, filter, dan tombol melayang. Tidak mengenal editor; hasilnya
 * hanya berupa [File] lewat [onPicked].
 */
internal class CodeFilePicker(
    private val context: Context,
    startDir: File,
    private val mode: CodePickerMode,
    private val suggestedName: String,
    private val onPicked: (File) -> Unit
) {
    private enum class Sort(val prefValue: String, val label: Int) {
        NAME("name", R.string.code_picker_sort_name),
        DATE("date", R.string.code_picker_sort_date),
        SIZE("size", R.string.code_picker_sort_size);

        companion object {
            fun from(value: String?): Sort = entries.firstOrNull { it.prefValue == value } ?: NAME
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var currentDir: File = if (startDir.isDirectory) startDir else File("/")
    private var sort: Sort = Sort.from(prefs.getString(KEY_SORT, null))
    private var showHidden: Boolean = prefs.getBoolean(KEY_HIDDEN, false)
    private var nameFilter: String = ""
    private var items: List<File> = emptyList()

    private lateinit var dialog: Dialog
    private lateinit var breadcrumb: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var nameInput: EditText
    private lateinit var adapter: FileAdapter
    private lateinit var favoriteButton: View

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
        val title = if (mode == CodePickerMode.SAVE_AS) R.string.code_picker_title_save else R.string.code_picker_title_open

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(CodeUi.dp(context, 4), 0, CodeUi.dp(context, 4), 0)
            addView(CodeUi.iconButton(context, R.drawable.ic_code_arrow_back, R.string.code_picker_up) {
                goUpOrDismiss()
            }, LinearLayout.LayoutParams(CodeUi.dp(context, 48), CodeUi.dp(context, 56)))
            addView(
                TextView(context).apply {
                    setText(title)
                    setTextColor(CodePalette.TEXT)
                    textSize = 18f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        val sortButton = CodeUi.iconButton(context, R.drawable.ic_code_sort, R.string.code_picker_sort) { }
        sortButton.setOnClickListener { showSortMenu(it) }
        favoriteButton = CodeUi.iconButton(context, R.drawable.ic_code_star, R.string.code_picker_favorites) { }
        favoriteButton.setOnClickListener { showFavoritesMenu(it) }
        val filterButton = CodeUi.iconButton(context, R.drawable.ic_code_filter, R.string.code_picker_filter) { }
        filterButton.setOnClickListener { showFilterMenu(it) }
        for (button in listOf(sortButton, favoriteButton, filterButton)) {
            header.addView(button, LinearLayout.LayoutParams(CodeUi.dp(context, 44), CodeUi.dp(context, 56)))
        }

        breadcrumb = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 12), 0)
        }
        breadcrumbScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(CodePalette.BACKGROUND)
            addView(breadcrumb, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, CodeUi.dp(context, 44)))
        }

        adapter = FileAdapter()
        listView = ListView(context).apply {
            this.adapter = this@CodeFilePicker.adapter
            divider = null
            clipToPadding = false
            setPadding(0, 0, 0, CodeUi.dp(context, 200))
            setOnItemClickListener { _, _, position, _ -> onItemClicked(items.getOrNull(position)) }
        }
        emptyView = TextView(context).apply {
            setTextColor(CodePalette.TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(CodeUi.dp(context, 24), CodeUi.dp(context, 24), CodeUi.dp(context, 24), CodeUi.dp(context, 24))
        }
        val listArea = FrameLayout(context).apply {
            addView(listView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emptyView, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            addView(buildFloatingButtons(), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, CodeUi.dp(context, 16), CodeUi.dp(context, 16))
            })
        }

        nameInput = EditText(context).apply {
            setText(suggestedName)
            setSelection(text.length)
            setHint(R.string.code_picker_name_hint)
            setTextColor(CodePalette.TEXT)
            setHintTextColor(CodePalette.TEXT_MUTED)
            backgroundTintList = ColorStateList.valueOf(CodePalette.ACCENT)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.BACKGROUND)
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(breadcrumbScroll, LinearLayout.LayoutParams(MATCH, WRAP))
            if (mode == CodePickerMode.SAVE_AS) {
                addView(
                    nameInput,
                    LinearLayout.LayoutParams(MATCH, WRAP).apply {
                        setMargins(CodeUi.dp(context, 16), 0, CodeUi.dp(context, 16), CodeUi.dp(context, 4))
                    }
                )
            }
            addView(listArea, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
    }

    private fun buildFloatingButtons(): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        column.addView(floatingButton(R.drawable.ic_code_create_folder, R.string.code_picker_new_folder) { promptNewFolder() })
        if (mode == CodePickerMode.SAVE_AS) {
            column.addView(floatingButton(R.drawable.ic_code_save, R.string.code_picker_save_here) { confirmSave() })
        }
        column.addView(floatingButton(R.drawable.ic_code_close, R.string.code_picker_cancel) { dialog.dismiss() })
        return column
    }

    private fun floatingButton(icon: Int, label: Int, onClick: () -> Unit): View {
        val chip = TextView(context).apply {
            setText(label)
            setTextColor(CodePalette.TEXT)
            textSize = 13f
            setPadding(CodeUi.dp(context, 10), CodeUi.dp(context, 4), CodeUi.dp(context, 10), CodeUi.dp(context, 4))
            background = GradientDrawable().apply {
                setColor(CodePalette.PANEL)
                cornerRadius = CodeUi.dp(context, 6).toFloat()
            }
        }
        val circle = CodeUi.iconButton(context, icon, label, Color.WHITE) { onClick() }.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CodePalette.ACCENT_DARK)
            }
            elevation = CodeUi.dp(context, 4).toFloat()
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = CodeUi.dp(context, 10) })
            addView(circle, LinearLayout.LayoutParams(CodeUi.dp(context, 52), CodeUi.dp(context, 52)))
            layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = CodeUi.dp(context, 12) }
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
        val filter = nameFilter.lowercase(Locale.ROOT)
        val visible = children.orEmpty().filter { file ->
            (showHidden || !file.name.startsWith(".")) &&
                (filter.isEmpty() || file.name.lowercase(Locale.ROOT).contains(filter))
        }
        items = visible.sortedWith(comparator())
        adapter.notifyDataSetChanged()
        listView.setSelection(0)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        emptyView.setText(if (children == null) R.string.code_picker_unreadable else R.string.code_picker_empty)
        updateFavoriteTint()
    }

    private fun comparator(): Comparator<File> {
        val byName = compareBy<File> { it.name.lowercase(Locale.ROOT) }
        val order: Comparator<File> = when (sort) {
            Sort.NAME -> byName
            Sort.DATE -> compareByDescending<File> { it.lastModified() }.then(byName)
            Sort.SIZE -> compareByDescending<File> { if (it.isFile) it.length() else -1L }.then(byName)
        }
        return compareByDescending<File> { it.isDirectory }.then(order)
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
                    setTextColor(CodePalette.TEXT_MUTED)
                    setPadding(CodeUi.dp(context, 4), 0, CodeUi.dp(context, 4), 0)
                })
            }
            breadcrumb.addView(TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(if (index == segments.lastIndex) CodePalette.ACCENT else CodePalette.TEXT)
                setPadding(CodeUi.dp(context, 4), CodeUi.dp(context, 10), CodeUi.dp(context, 4), CodeUi.dp(context, 10))
                setOnClickListener { render(target) }
            })
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun onItemClicked(file: File?) {
        when {
            file == null -> Unit
            file.isDirectory -> render(file)
            mode == CodePickerMode.OPEN -> {
                dialog.dismiss()
                onPicked(file)
            }
            else -> {
                nameInput.setText(file.name)
                nameInput.setSelection(nameInput.text.length)
            }
        }
    }

    private fun goUpOrDismiss() {
        val parent = currentDir.parentFile
        if (parent == null) dialog.dismiss() else render(parent)
    }

    private fun confirmSave() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) {
            toast(context.getString(R.string.code_picker_name_invalid))
            return
        }
        val target = File(currentDir, name)
        when {
            target.isDirectory -> toast(context.getString(R.string.code_picker_name_is_folder, name))
            target.exists() -> AlertDialog.Builder(context)
                .setTitle(R.string.code_picker_overwrite_title)
                .setMessage(context.getString(R.string.code_picker_overwrite_message, name))
                .setPositiveButton(R.string.code_picker_overwrite_yes) { _, _ -> finish(target) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            else -> finish(target)
        }
    }

    private fun finish(file: File) {
        dialog.dismiss()
        onPicked(file)
    }

    private fun promptNewFolder() {
        val input = EditText(context).apply {
            setHint(R.string.code_picker_folder_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_picker_new_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> createFolder(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createFolder(name: String) {
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) {
            toast(context.getString(R.string.code_picker_name_invalid))
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
            toast(context.getString(R.string.code_picker_folder_failed, name))
        }
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        Sort.entries.forEachIndexed { index, option ->
            popup.menu.add(GROUP_SORT, index, index, option.label)
        }
        popup.menu.setGroupCheckable(GROUP_SORT, true, true)
        popup.menu.findItem(sort.ordinal)?.isChecked = true
        popup.setOnMenuItemClickListener { item ->
            sort = Sort.entries[item.itemId]
            prefs.edit().putString(KEY_SORT, sort.prefValue).apply()
            render(currentDir)
            true
        }
        popup.show()
    }

    private fun showFilterMenu(anchor: View) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        popup.menu.add(Menu.NONE, ID_HIDDEN, 0, R.string.code_picker_show_hidden).apply {
            isCheckable = true
            isChecked = showHidden
        }
        popup.menu.add(Menu.NONE, ID_NAME_FILTER, 1, R.string.code_picker_filter_name)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_HIDDEN -> {
                    showHidden = !item.isChecked
                    prefs.edit().putBoolean(KEY_HIDDEN, showHidden).apply()
                    render(currentDir)
                }
                ID_NAME_FILTER -> promptNameFilter()
            }
            true
        }
        popup.show()
    }

    private fun promptNameFilter() {
        val input = EditText(context).apply {
            setText(nameFilter)
            setHint(R.string.code_picker_filter_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_picker_filter_name)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                nameFilter = input.text.toString().trim()
                render(currentDir)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun favorites(): List<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().sorted()

    private fun saveFavorites(paths: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, HashSet(paths)).apply()
        updateFavoriteTint()
    }

    private fun updateFavoriteTint() {
        val active = favorites().contains(currentDir.path)
        (favoriteButton as? ImageView)?.imageTintList =
            ColorStateList.valueOf(if (active) CodePalette.ACCENT else CodePalette.TEXT)
    }

    private fun showFavoritesMenu(anchor: View) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        val saved = favorites()
        val isFavorite = saved.contains(currentDir.path)
        popup.menu.add(Menu.NONE, ID_TOGGLE_FAVORITE, 0,
            if (isFavorite) R.string.code_picker_favorite_remove else R.string.code_picker_favorite_add)
        saved.forEachIndexed { index, path ->
            popup.menu.add(GROUP_FAVORITES, FAVORITE_ID_BASE + index, 1 + index, path)
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == ID_TOGGLE_FAVORITE) {
                val updated = favorites().toMutableSet()
                if (isFavorite) updated.remove(currentDir.path) else updated.add(currentDir.path)
                saveFavorites(updated)
            } else {
                val path = saved.getOrNull(item.itemId - FAVORITE_ID_BASE)
                val target = path?.let { File(it) }
                if (target != null && target.isDirectory) {
                    render(target)
                } else if (path != null) {
                    toast(context.getString(R.string.code_picker_favorite_missing, path))
                    saveFavorites(favorites().toMutableSet().apply { remove(path) })
                }
            }
            true
        }
        popup.show()
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private inner class FileAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): File = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val file = items[position]
            val row = (convertView as? LinearLayout) ?: createRow()
            val icon = row.getChildAt(0) as ImageView
            val texts = row.getChildAt(1) as LinearLayout
            val name = texts.getChildAt(0) as TextView
            val meta = texts.getChildAt(1) as TextView
            icon.setImageResource(if (file.isDirectory) R.drawable.ic_code_folder else R.drawable.ic_code_file)
            icon.imageTintList = ColorStateList.valueOf(
                if (file.isDirectory) CodePalette.FOLDER else CodePalette.TEXT_MUTED
            )
            name.text = file.name
            val date = DateFormat.getMediumDateFormat(context).format(Date(file.lastModified()))
            meta.text = if (file.isDirectory) date else Formatter.formatShortFileSize(context, file.length()) + " \u2022 " + date
            return row
        }

        private fun createRow(): LinearLayout {
            val icon = ImageView(context)
            val name = TextView(context).apply {
                setTextColor(CodePalette.TEXT)
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            val meta = TextView(context).apply {
                setTextColor(CodePalette.TEXT_MUTED)
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
                setPadding(CodeUi.dp(context, 16), CodeUi.dp(context, 10), CodeUi.dp(context, 16), CodeUi.dp(context, 10))
                addView(icon, LinearLayout.LayoutParams(CodeUi.dp(context, 32), CodeUi.dp(context, 32)))
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = CodeUi.dp(context, 16) })
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "molina_code_picker"
        const val KEY_SORT = "sort"
        const val KEY_HIDDEN = "show_hidden"
        const val KEY_FAVORITES = "favorites"
        const val GROUP_SORT = 1
        const val GROUP_FAVORITES = 2
        const val ID_HIDDEN = 1
        const val ID_NAME_FILTER = 2
        const val ID_TOGGLE_FAVORITE = 100
        const val FAVORITE_ID_BASE = 1000
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
