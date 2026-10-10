package com.molina.suite.feature.code

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.StringRes

internal class CodeAction(@StringRes val label: Int, val run: () -> Unit)

/**
 * Toolbar tombol di bawah editor. Tombolnya tidak mengambil fokus supaya
 * editor tetap fokus dan keyboard layar tidak menutup saat tombol disentuh.
 */
internal object CodeActionBar {

    private const val MIN_WIDTH_DP = 56
    private const val HEIGHT_DP = 48

    fun build(context: Context, actions: List<CodeAction>): View {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (action in actions) {
            val button = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                setText(action.label)
                isAllCaps = false
                setTextColor(CodePalette.TEXT)
                isFocusable = false
                isFocusableInTouchMode = false
                minWidth = (MIN_WIDTH_DP * density).toInt()
                minimumWidth = (MIN_WIDTH_DP * density).toInt()
                setOnClickListener { action.run() }
            }
            row.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (HEIGHT_DP * density).toInt()
                )
            )
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(CodePalette.PANEL)
            addView(row)
        }
    }
}
