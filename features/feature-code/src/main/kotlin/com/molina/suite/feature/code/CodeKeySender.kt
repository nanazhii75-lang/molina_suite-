package com.molina.suite.feature.code

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View

/** Menyuntikkan tombol keyboard ke view target (WebView) sebagai KeyEvent. */
internal class CodeKeySender(private val target: View) {

    fun send(keyCode: Int, ctrl: Boolean = false, shift: Boolean = false) {
        var meta = 0
        if (ctrl) meta = meta or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        target.requestFocus()
        val downTime = SystemClock.uptimeMillis()
        target.dispatchKeyEvent(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
        )
        target.dispatchKeyEvent(
            KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta)
        )
    }
}
