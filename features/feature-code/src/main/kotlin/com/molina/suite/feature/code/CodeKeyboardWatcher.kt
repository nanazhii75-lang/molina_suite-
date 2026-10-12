package com.molina.suite.feature.code

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager

/**
 * Mendeteksi keyboard layar saat tab aktif: memasang adjustResize selama aktif dan melaporkan
 * (1) keterlihatan keyboard dan (2) tinggi bagian [anchor] yang tertutup keyboard. Bagian kedua
 * dipakai tampilan untuk menaikkan toolbar bila jendela tidak ikut mengecil (0 bila jendela sudah
 * di-resize sistem). [stop] mengembalikan mode semula dan melaporkan keyboard tertutup.
 */
internal class CodeKeyboardWatcher(
    private val window: Window,
    private val anchor: View,
    private val onOverlapChanged: (Int) -> Unit = {},
    private val onKeyboardChanged: (Boolean) -> Unit
) : ViewTreeObserver.OnGlobalLayoutListener {

    private val frame = Rect()
    private val location = IntArray(2)
    private var savedSoftInputMode: Int? = null
    private var observing = false
    private var keyboardVisible = false
    private var overlap = 0

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
        publishOverlap(0)
        publish(false)
    }

    override fun onGlobalLayout() {
        val root = anchor.rootView
        root.getWindowVisibleDisplayFrame(frame)
        val covered = root.height - frame.bottom
        val visible = covered > root.height * KEYBOARD_HEIGHT_RATIO
        publish(visible)
        if (visible) {
            anchor.getLocationOnScreen(location)
            publishOverlap((location[1] + anchor.height - frame.bottom).coerceAtLeast(0))
        } else {
            publishOverlap(0)
        }
    }

    private fun publish(visible: Boolean) {
        if (visible == keyboardVisible) return
        keyboardVisible = visible
        onKeyboardChanged(visible)
    }

    private fun publishOverlap(value: Int) {
        if (value == overlap) return
        overlap = value
        onOverlapChanged(value)
    }

    private companion object {
        const val KEYBOARD_HEIGHT_RATIO = 0.15f
    }
}
