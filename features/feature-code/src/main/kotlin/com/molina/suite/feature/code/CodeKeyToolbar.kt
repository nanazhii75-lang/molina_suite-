package com.molina.suite.feature.code

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.annotation.StringRes

/**
 * Toolbar tombol pintas di bawah editor. Tombolnya tidak mengambil fokus supaya
 * WebView tetap fokus dan keyboard layar tidak menutup saat tombol disentuh.
 */
internal class CodeKeyToolbar(
    private val sender: CodeKeySender,
    private val onToggleKeyboard: () -> Unit
) {
    private class Key(@StringRes val label: Int, val action: () -> Unit)

    private val keys = listOf(
        Key(R.string.code_key_keyboard) { onToggleKeyboard() },
        Key(R.string.code_key_esc) { sender.send(KeyEvent.KEYCODE_ESCAPE) },
        Key(R.string.code_key_tab) { sender.send(KeyEvent.KEYCODE_TAB) },
        Key(R.string.code_key_left) { sender.send(KeyEvent.KEYCODE_DPAD_LEFT) },
        Key(R.string.code_key_up) { sender.send(KeyEvent.KEYCODE_DPAD_UP) },
        Key(R.string.code_key_down) { sender.send(KeyEvent.KEYCODE_DPAD_DOWN) },
        Key(R.string.code_key_right) { sender.send(KeyEvent.KEYCODE_DPAD_RIGHT) },
        Key(R.string.code_key_undo) { sender.send(KeyEvent.KEYCODE_Z, ctrl = true) },
        Key(R.string.code_key_redo) { sender.send(KeyEvent.KEYCODE_Z, ctrl = true, shift = true) },
        Key(R.string.code_key_find) { sender.send(KeyEvent.KEYCODE_F, ctrl = true) },
        Key(R.string.code_key_save) { sender.send(KeyEvent.KEYCODE_S, ctrl = true) },
        Key(R.string.code_key_palette) { sender.send(KeyEvent.KEYCODE_F1) },
        Key(R.string.code_key_quick_open) { sender.send(KeyEvent.KEYCODE_P, ctrl = true) },
        Key(R.string.code_key_terminal) { sender.send(KeyEvent.KEYCODE_GRAVE, ctrl = true) }
    )

    fun build(context: Context): View {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (key in keys) {
            val button = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
                setText(key.label)
                setAllCaps(false)
                isFocusable = false
                isFocusableInTouchMode = false
                minWidth = (MIN_WIDTH_DP * density).toInt()
                minimumWidth = (MIN_WIDTH_DP * density).toInt()
                setOnClickListener { key.action() }
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
            addView(row)
        }
    }

    private companion object {
        const val MIN_WIDTH_DP = 48
        const val HEIGHT_DP = 48
    }
}
