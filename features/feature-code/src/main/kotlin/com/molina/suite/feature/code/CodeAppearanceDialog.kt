package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView

/** Dialog Gaya Visual: ukuran font, nomor baris, dan tema warna. */
internal object CodeAppearanceDialog {

    fun show(context: Context, current: CodeAppearance, onApply: (CodeAppearance) -> Unit) {
        val pad = CodeUi.dp(context, 20)
        val min = CodeAppearance.MIN_SP
        val label = TextView(context)
        val seek = SeekBar(context).apply {
            max = CodeAppearance.MAX_SP - min
            progress = (current.textSizeSp - min).coerceIn(0, CodeAppearance.MAX_SP - min)
        }
        fun render() {
            label.text = context.getString(R.string.code_appearance_size, min + seek.progress)
        }
        render()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) = render()
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        val lines = CheckBox(context).apply {
            setText(R.string.code_appearance_line_numbers)
            isChecked = current.lineNumbers
        }
        val themeLabel = TextView(context).apply {
            setText(R.string.code_appearance_theme)
            setPadding(0, pad / 2, 0, 0)
        }
        val choices = listOf(
            CodeAppearance.THEME_DARCULA to context.getString(R.string.code_theme_darcula),
            CodeAppearance.THEME_OBSIDIAN to context.getString(R.string.code_theme_obsidian)
        )
        val group = RadioGroup(context)
        val buttons = choices.map { (id, title) ->
            RadioButton(context).apply {
                text = title
                tag = id
                setId(View.generateViewId())
                isChecked = id == current.theme
            }.also { group.addView(it) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(seek)
            addView(lines)
            addView(themeLabel)
            addView(group)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_menu_appearance)
            .setView(content)
            .setPositiveButton(R.string.code_appearance_apply) { _, _ ->
                val chosen = buttons.firstOrNull { it.isChecked }?.tag as? String ?: current.theme
                onApply(
                    CodeAppearance(
                        textSizeSp = min + seek.progress,
                        lineNumbers = lines.isChecked,
                        theme = chosen
                    )
                )
            }
            .setNegativeButton(R.string.code_picker_cancel, null)
            .show()
    }
}
