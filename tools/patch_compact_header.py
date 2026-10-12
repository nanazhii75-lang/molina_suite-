import os, sys

MAIN = os.path.join("features", "feature-code", "src", "main")
KT = os.path.join(MAIN, "kotlin", "com", "molina", "suite", "feature", "code")
FRAGMENT = os.path.join(KT, "CodeTabFragment.kt")
MENUS = os.path.join(KT, "CodeMenus.kt")
TABBAR = os.path.join(KT, "CodeTabBar.kt")
STRINGS = os.path.join(MAIN, "res", "values", "strings.xml")
MENU_ICON = os.path.join(MAIN, "res", "drawable", "ic_code_menu.xml")


def die(msg):
    print("GAGAL: " + msg + " (tidak ada file yang diubah)")
    sys.exit(1)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)


for p in (FRAGMENT, MENUS, TABBAR, STRINGS):
    if not os.path.exists(p):
        die("tidak ada " + p + "; jalankan dari /storage/emulated/0/molina-suite")

MENUS_NEW = '''package com.molina.suite.feature.code

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
'''

FRAG_EDITS = [
    (
        "        val title = TextView(context).apply {\n"
        "            setTextColor(CodePalette.TEXT)\n"
        "            textSize = 16f\n",
        "        val title = TextView(context).apply {\n"
        "            setTextColor(CodePalette.TEXT)\n"
        "            textSize = 13f\n"
        "            includeFontPadding = false\n",
    ),
    (
        "            setTextColor(CodePalette.TEXT_MUTED)\n"
        "            textSize = 12f\n"
        "            maxLines = 1\n"
        "            ellipsize = TextUtils.TruncateAt.START\n",
        "            setTextColor(CodePalette.TEXT_MUTED)\n"
        "            textSize = 10f\n"
        "            includeFontPadding = false\n"
        "            maxLines = 1\n"
        "            ellipsize = TextUtils.TruncateAt.START\n",
    ),
    (
        "        val menuButton = CodeUi.iconButton(context, R.drawable.ic_code_menu, R.string.code_header_menu) { }\n"
        "        menuButton.setOnClickListener { showFileMenu(it) }\n",
        "",
    ),
    (
        "            setPadding(CodeUi.dp(context, 4), CodeUi.dp(context, 4), CodeUi.dp(context, 4), CodeUi.dp(context, 4))\n"
        "            addView(menuButton, LinearLayout.LayoutParams(CodeUi.dp(context, 44), CodeUi.dp(context, 48)))\n"
        "            addView(\n"
        "                titleColumn,\n"
        "                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)\n"
        "                    .apply { marginStart = CodeUi.dp(context, 4) }\n"
        "            )\n"
        "            addView(moreButton, LinearLayout.LayoutParams(CodeUi.dp(context, 44), CodeUi.dp(context, 48)))\n",
        "            setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 2), 0)\n"
        "            addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))\n"
        "            addView(moreButton, LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 36)))\n",
    ),
    (
        "    private fun showFileMenu(anchor: View) {\n"
        "        CodeMenus.showFileMenu(requireContext(), anchor, session.document.value, object : CodeFileMenuListener {\n"
        "            override fun onNew() = requestNew()\n"
        "            override fun onOpen() = requestOpen()\n"
        "            override fun onSave() = saveCurrent()\n"
        "            override fun onSaveAs() = requestSaveAs()\n"
        "            override fun onReload() = requestReload()\n"
        "            override fun onClose() = requestClose()\n"
        "        })\n"
        "    }\n\n"
        "    private fun showActionMenu(anchor: View) {\n"
        "        CodeMenus.showActionMenu(requireContext(), anchor, session.options.value, object : CodeActionMenuListener {\n"
        "            override fun onSearch() = openSearch()\n",
        "    private fun showActionMenu(anchor: View) {\n"
        "        val menuListener = object : CodeActionMenuListener {\n"
        "            override fun onNew() = requestNew()\n"
        "            override fun onOpen() = requestOpen()\n"
        "            override fun onReload() = requestReload()\n"
        "            override fun onSearch() = openSearch()\n",
    ),
    (
        "            override fun onReadOnlyChanged(readOnly: Boolean) = session.setReadOnly(readOnly)\n"
        "        })\n"
        "    }\n",
        "            override fun onReadOnlyChanged(readOnly: Boolean) = session.setReadOnly(readOnly)\n"
        "        }\n"
        "        CodeMenus.showActionMenu(\n"
        "            requireContext(),\n"
        "            anchor,\n"
        "            session.document.value,\n"
        "            session.options.value,\n"
        "            menuListener\n"
        "        )\n"
        "    }\n",
    ),
]

TABBAR_EDITS = [
    ("            textSize = 13f\n            maxLines = 1\n", "            textSize = 12f\n            maxLines = 1\n"),
    ("        const val HEIGHT_DP = 40\n", "        const val HEIGHT_DP = 32\n"),
    ("        const val CLOSE_DP = 32\n", "        const val CLOSE_DP = 28\n"),
]

changed = []

menus = read(MENUS)
if "showFileMenu" in menus:
    write(MENUS, MENUS_NEW)
    changed.append(MENUS)

fragment = read(FRAGMENT)
if "showFileMenu" in fragment or "ic_code_menu" in fragment:
    updated = fragment
    for old, new in FRAG_EDITS:
        if old not in updated:
            die("blok di CodeTabFragment.kt tidak cocok; jalankan git pull dulu:\n" + old[:120])
        updated = updated.replace(old, new, 1)
    if "showFileMenu" in updated or "ic_code_menu" in updated:
        die("masih ada sisa showFileMenu atau ic_code_menu di CodeTabFragment.kt")
    write(FRAGMENT, updated)
    changed.append(FRAGMENT)

tabbar = read(TABBAR)
if "HEIGHT_DP = 40" in tabbar:
    for old, new in TABBAR_EDITS:
        if old not in tabbar:
            die("blok di CodeTabBar.kt tidak cocok:\n" + old[:120])
        tabbar = tabbar.replace(old, new, 1)
    write(TABBAR, tabbar)
    changed.append(TABBAR)

strings = read(STRINGS)
line = '    <string name="code_header_menu">Menu berkas</string>\n'
if line in strings:
    write(STRINGS, strings.replace(line, "", 1))
    changed.append(STRINGS)

if os.path.exists(MENU_ICON):
    os.remove(MENU_ICON)
    changed.append(MENU_ICON + " (dihapus)")

print("selesai: %d berkas diubah" % len(changed))
for p in changed:
    print("  " + p)
