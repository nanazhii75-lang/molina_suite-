package com.molina.suite.feature.code

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

internal class CodeAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val run: () -> Unit
)

/** Toolbar ikon di bawah editor; bisa digulir bila layar terlalu sempit. */
internal object CodeActionBar {

    private const val MIN_WIDTH_DP = 40
    private const val HEIGHT_DP = 48

    fun build(context: Context, actions: List<CodeAction>): View {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (action in actions) {
            val button = CodeUi.iconButton(context, action.icon, action.label) { action.run() }
            button.minimumWidth = CodeUi.dp(context, MIN_WIDTH_DP)
            row.addView(
                button,
                LinearLayout.LayoutParams(0, CodeUi.dp(context, HEIGHT_DP), 1f)
            )
        }
        return HorizontalScrollView(context).apply {
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
    }
}
