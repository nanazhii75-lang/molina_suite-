package com.molina.suite.feature.code

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Bilah tab dokumen di atas editor; bisa digulir. Hanya menggambar, keadaan ada di sesi. */
internal class CodeTabBar(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onSelect(id: Long)
        fun onClose(id: Long)
    }

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(CodePalette.PANEL)
        addView(row, ViewGroup.LayoutParams(WRAP, CodeUi.dp(context, HEIGHT_DP)))
    }

    val view: View
        get() = scroll

    fun render(state: CodeTabBarState) {
        row.removeAllViews()
        var activeChip: View? = null
        for (item in state.items) {
            val active = item.id == state.activeId
            val chip = buildChip(item, active)
            row.addView(chip, LinearLayout.LayoutParams(WRAP, MATCH))
            if (active) activeChip = chip
        }
        activeChip?.let { chip -> scroll.post { scrollToChip(chip) } }
    }

    private fun scrollToChip(chip: View) {
        val visibleLeft = scroll.scrollX
        val visibleRight = visibleLeft + scroll.width
        if (chip.left < visibleLeft) {
            scroll.smoothScrollTo(chip.left, 0)
        } else if (chip.right > visibleRight) {
            scroll.smoothScrollTo(chip.right - scroll.width, 0)
        }
    }

    private fun buildChip(item: CodeTabItem, active: Boolean): View {
        val label = TextView(context).apply {
            text = titleOf(item)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = CodeUi.dp(context, MAX_TITLE_DP)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (active) CodePalette.TEXT else CodePalette.TEXT_MUTED)
            if (active) setTypeface(typeface, Typeface.BOLD)
        }
        val close = CodeUi.iconButton(
            context,
            R.drawable.ic_code_close,
            R.string.code_key_close,
            tint = CodePalette.TEXT_MUTED
        ) { callbacks.onClose(item.id) }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 2), 0)
            addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(
                close,
                LinearLayout.LayoutParams(CodeUi.dp(context, CLOSE_DP), CodeUi.dp(context, CLOSE_DP))
                    .apply { marginStart = CodeUi.dp(context, 4) }
            )
        }
        val indicator = View(context).apply {
            setBackgroundColor(if (active) CodePalette.ACCENT else Color.TRANSPARENT)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (active) CodePalette.BACKGROUND else CodePalette.PANEL)
            isClickable = true
            setOnClickListener { callbacks.onSelect(item.id) }
            addView(content, LinearLayout.LayoutParams(WRAP, 0, 1f))
            addView(indicator, LinearLayout.LayoutParams(MATCH, CodeUi.dp(context, 2)))
        }
    }

    private fun titleOf(item: CodeTabItem): CharSequence {
        val base = item.fileName ?: untitled(item.untitledNumber)
        val title = SpannableStringBuilder(base)
        if (item.dirty) {
            val start = title.length
            title.append(" \u25CF")
            title.setSpan(
                ForegroundColorSpan(CodePalette.ACCENT),
                start,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return title
    }

    private fun untitled(number: Int): String {
        val base = context.getString(R.string.code_untitled)
        return if (number <= 1) base else "$base $number"
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val HEIGHT_DP = 40
        const val MAX_TITLE_DP = 140
        const val CLOSE_DP = 32
    }
}
