package com.molina.suite.core.common

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver

/**
 * Mendeteksi keyboard layar dari ruang window yang tertutup. Hanya membaca; tidak mengubah mode
 * soft input window, jadi aman dipakai di tab yang sudah mengatur keyboard sendiri (Terminal).
 * [stop] melaporkan keyboard tidak terlihat supaya pemanggil memulihkan tampilan normal.
 */
class KeyboardVisibilityWatcher(
    private val anchor: View,
    private val onKeyboardChanged: (Boolean) -> Unit
) : ViewTreeObserver.OnGlobalLayoutListener {

    private val frame = Rect()
    private var observing = false
    private var keyboardVisible = false

    fun start() {
        if (observing) return
        observing = true
        anchor.viewTreeObserver.addOnGlobalLayoutListener(this)
        onGlobalLayout()
    }

    fun stop() {
        if (!observing) return
        observing = false
        val observer = anchor.viewTreeObserver
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(this)
        publish(false)
    }

    override fun onGlobalLayout() {
        val root = anchor.rootView
        root.getWindowVisibleDisplayFrame(frame)
        val covered = root.height - frame.bottom
        publish(covered > root.height * KEYBOARD_HEIGHT_RATIO)
    }

    private fun publish(visible: Boolean) {
        if (visible == keyboardVisible) return
        keyboardVisible = visible
        onKeyboardChanged(visible)
    }

    private companion object {
        const val KEYBOARD_HEIGHT_RATIO = 0.15f
    }
}
