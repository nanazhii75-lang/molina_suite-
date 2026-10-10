package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Dialog Gaya Visual: ukuran font dan nomor baris. */
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
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(seek)
            addView(lines)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.code_menu_appearance)
            .setView(content)
            .setPositiveButton(R.string.code_appearance_apply) { _, _ ->
                onApply(CodeAppearance(textSizeSp = min + seek.progress, lineNumbers = lines.isChecked))
            }
            .setNegativeButton(R.string.code_picker_cancel, null)
            .show()
    }
}
