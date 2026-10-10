package com.molina.suite.feature.code

import android.content.Context
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu

internal interface CodeFileMenuListener {
    fun onNew()
    fun onOpen()
    fun onSave()
    fun onSaveAs()
    fun onReload()
    fun onClose()
}

internal interface CodeActionMenuListener {
    fun onSearch()
    fun onGoToLine()
    fun onStatistics()
    fun onWordwrapChanged(enabled: Boolean)
    fun onReadOnlyChanged(readOnly: Boolean)
}

/** Menu popup tab Code. Hanya membangun menu; setiap pilihan diteruskan ke listener. */
internal object CodeMenus {

    private const val ID_NEW = 1
    private const val ID_OPEN = 2
    private const val ID_SAVE = 3
    private const val ID_SAVE_AS = 4
    private const val ID_RELOAD = 5
    private const val ID_CLOSE = 6

    private const val ID_SEARCH = 11
    private const val ID_GOTO = 12
    private const val ID_STATS = 13
    private const val ID_WRAP = 14
    private const val ID_READONLY = 15

    fun showFileMenu(
        context: Context,
        anchor: View,
        document: CodeDocument,
        listener: CodeFileMenuListener
    ) {
        val popup = PopupMenu(context, anchor, Gravity.START)
        val menu = popup.menu
        menu.add(Menu.NONE, ID_NEW, 0, R.string.code_menu_new)
        menu.add(Menu.NONE, ID_OPEN, 1, R.string.code_menu_open)
        menu.add(Menu.NONE, ID_SAVE, 2, R.string.code_menu_save).isEnabled =
            document.dirty || document.file == null
        menu.add(Menu.NONE, ID_SAVE_AS, 3, R.string.code_menu_save_as)
        menu.add(Menu.NONE, ID_RELOAD, 4, R.string.code_menu_reload).isEnabled = document.file != null
        menu.add(Menu.NONE, ID_CLOSE, 5, R.string.code_menu_close)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_NEW -> listener.onNew()
                ID_OPEN -> listener.onOpen()
                ID_SAVE -> listener.onSave()
                ID_SAVE_AS -> listener.onSaveAs()
                ID_RELOAD -> listener.onReload()
                ID_CLOSE -> listener.onClose()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    fun showActionMenu(
        context: Context,
        anchor: View,
        options: CodeViewOptions,
        listener: CodeActionMenuListener
    ) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        val menu = popup.menu
        menu.add(Menu.NONE, ID_SEARCH, 0, R.string.code_menu_search)
        menu.add(Menu.NONE, ID_GOTO, 1, R.string.code_menu_goto)
        menu.add(Menu.NONE, ID_STATS, 2, R.string.code_menu_stats)
        checkable(menu.add(Menu.NONE, ID_WRAP, 3, R.string.code_menu_wordwrap), options.wordwrap)
        checkable(menu.add(Menu.NONE, ID_READONLY, 4, R.string.code_menu_readonly), options.readOnly)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_SEARCH -> listener.onSearch()
                ID_GOTO -> listener.onGoToLine()
                ID_STATS -> listener.onStatistics()
                ID_WRAP -> listener.onWordwrapChanged(!item.isChecked)
                ID_READONLY -> listener.onReadOnlyChanged(!item.isChecked)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    private fun checkable(item: MenuItem, checked: Boolean) {
        item.isCheckable = true
        item.isChecked = checked
    }
}
