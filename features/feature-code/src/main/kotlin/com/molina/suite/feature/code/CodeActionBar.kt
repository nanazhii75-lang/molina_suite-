package com.molina.suite.feature.code

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal class CodeAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val tint: Int,
    val run: () -> Unit
)

/** Toolbar bawah editor; tiap ikon berwarna sendiri dan abu-abu bila tidak bisa dipakai. */
internal class CodeActionBar private constructor(
    val view: View,
    private val buttons: Map<Int, Pair<CodeAction, ImageButton>>
) {

    /** Aktif atau nonaktifkan ikon [icon]; ikon nonaktif abu-abu dan tidak bisa diketuk. */
    fun setEnabled(@DrawableRes icon: Int, enabled: Boolean) {
        val (action, button) = buttons[icon] ?: return
        if (button.isEnabled == enabled) return
        button.isEnabled = enabled
        button.imageTintList = ColorStateList.valueOf(if (enabled) action.tint else CodePalette.TOOL_DISABLED)
    }

    companion object {
        private const val MIN_WIDTH_DP = 40
        private const val HEIGHT_DP = 48

        fun build(context: Context, actions: List<CodeAction>): CodeActionBar {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val buttons = LinkedHashMap<Int, Pair<CodeAction, ImageButton>>()
            for (action in actions) {
                val button = CodeUi.iconButton(context, action.icon, action.label, action.tint) { action.run() }
                button.minimumWidth = CodeUi.dp(context, MIN_WIDTH_DP)
                row.addView(
                    button,
                    LinearLayout.LayoutParams(0, CodeUi.dp(context, HEIGHT_DP), 1f)
                )
                buttons[action.icon] = action to button
            }
            val scroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = true
                setBackgroundColor(CodePalette.PANEL)
                addView(
                    row,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            return CodeActionBar(scroll, buttons)
        }
    }
}
