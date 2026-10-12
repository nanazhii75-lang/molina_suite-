package com.molina.suite.feature.code

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager

/**
 * Mendeteksi keyboard layar saat tab aktif: memasang adjustResize selama aktif
 * dan melaporkan perubahan keterlihatan keyboard. [stop] mengembalikan mode semula.
 */
internal class CodeKeyboardWatcher(
    private val window: Window,
    private val anchor: View,
    private val onKeyboardChanged: (Boolean) -> Unit
) : ViewTreeObserver.OnGlobalLayoutListener {

    private val frame = Rect()
    private var savedSoftInputMode: Int? = null
    private var observing = false
    private var keyboardVisible = false

    fun start() {
        if (observing) return
        val mode = window.attributes.softInputMode
        savedSoftInputMode = mode
        window.setSoftInputMode(
            (mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        anchor.viewTreeObserver.addOnGlobalLayoutListener(this)
        observing = true
        onGlobalLayout()
    }

    fun stop() {
        if (!observing) return
        observing = false
        val observer = anchor.viewTreeObserver
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(this)
        savedSoftInputMode?.let { window.setSoftInputMode(it) }
        savedSoftInputMode = null
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
