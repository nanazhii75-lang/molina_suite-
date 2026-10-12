package com.molina.suite.feature.code

import android.content.Context
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu

internal interface CodeActionMenuListener {
    fun onNew()
    fun onOpen()
    fun onReload()
    fun onSearch()
    fun onShare()
    fun onGoToLine()
    fun onSyntax()
    fun onEncoding()
    fun onAppearance()
    fun onStatistics()
    fun onWordwrapChanged(enabled: Boolean)
    fun onReadOnlyChanged(readOnly: Boolean)
}

/**
 * Menu popup tab Code (ikon titik tiga di header). Hanya membangun menu; setiap pilihan
 * diteruskan ke listener. Simpan, Simpan sebagai, dan Tutup ada di toolbar bawah.
 */
internal object CodeMenus {

    private const val ID_NEW = 1
    private const val ID_OPEN = 2
    private const val ID_RELOAD = 5

    private const val ID_SEARCH = 11
    private const val ID_GOTO = 12
    private const val ID_STATS = 13
    private const val ID_WRAP = 14
    private const val ID_READONLY = 15
    private const val ID_SYNTAX = 16
    private const val ID_ENCODING = 17
    private const val ID_APPEARANCE = 18
    private const val ID_SHARE = 19

    fun showActionMenu(
        context: Context,
        anchor: View,
        document: CodeDocument,
        options: CodeViewOptions,
        listener: CodeActionMenuListener
    ) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        val menu = popup.menu
        menu.add(Menu.NONE, ID_NEW, 0, R.string.code_menu_new)
        menu.add(Menu.NONE, ID_OPEN, 1, R.string.code_menu_open)
        menu.add(Menu.NONE, ID_RELOAD, 2, R.string.code_menu_reload).isEnabled = document.file != null
        menu.add(Menu.NONE, ID_SEARCH, 3, R.string.code_menu_search)
        menu.add(Menu.NONE, ID_SHARE, 4, R.string.code_menu_share)
        menu.add(Menu.NONE, ID_GOTO, 5, R.string.code_menu_goto)
        menu.add(Menu.NONE, ID_SYNTAX, 6, R.string.code_menu_syntax)
        menu.add(Menu.NONE, ID_ENCODING, 7, R.string.code_menu_encoding)
        menu.add(Menu.NONE, ID_APPEARANCE, 8, R.string.code_menu_appearance)
        menu.add(Menu.NONE, ID_STATS, 9, R.string.code_menu_stats)
        checkable(menu.add(Menu.NONE, ID_WRAP, 10, R.string.code_menu_wordwrap), options.wordwrap)
        checkable(menu.add(Menu.NONE, ID_READONLY, 11, R.string.code_menu_readonly), options.readOnly)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                ID_NEW -> listener.onNew()
                ID_OPEN -> listener.onOpen()
                ID_RELOAD -> listener.onReload()
                ID_SEARCH -> listener.onSearch()
                ID_SHARE -> listener.onShare()
                ID_GOTO -> listener.onGoToLine()
                ID_SYNTAX -> listener.onSyntax()
                ID_ENCODING -> listener.onEncoding()
                ID_APPEARANCE -> listener.onAppearance()
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
