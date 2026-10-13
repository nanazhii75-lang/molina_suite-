package com.molina.suite.feature.code

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.drawerlayout.widget.DrawerLayout
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.abs

/**
 * DrawerLayout yang juga terbuka oleh geseran ke kanan di mana saja pada area editor,
 * bukan hanya dari tepi kiri layar. Geseran hanya dihitung bila editor tidak sedang
 * bergulir ke samping dan tidak ada teks terpilih, supaya tidak bentrok dengan gulir
 * horizontal dan penyeretan pilihan. Geseran dari tepi kiri tetap ditangani DrawerLayout.
 */
internal class CodeSwipeDrawerLayout(
    context: Context,
    private val editorProvider: () -> CodeEditor?
) : DrawerLayout(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val openDistance = OPEN_DISTANCE_DP * context.resources.displayMetrics.density
    private val screenLocation = IntArray(2)
    private var tracking = false
    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                tracking = !isDrawerOpen(Gravity.START) && isInsideEditor(ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                val dx = ev.rawX - downX
                val dy = abs(ev.rawY - downY)
                when {
                    dx > openDistance && dx > dy * OPEN_DOMINANCE -> {
                        tracking = false
                        if (canOpen()) {
                            openDrawer(Gravity.START)
                            return true
                        }
                    }
                    dy > touchSlop * OPEN_SLOP_FACTOR || dx < -touchSlop -> tracking = false
                }
            }
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun canOpen(): Boolean {
        val target = editorProvider() ?: return false
        return target.offsetX == 0 && !target.cursor.isSelected
    }

    private fun isInsideEditor(rawX: Float, rawY: Float): Boolean {
        val target = editorProvider() ?: return false
        if (!target.isShown) return false
        target.getLocationOnScreen(screenLocation)
        val left = screenLocation[0]
        val top = screenLocation[1]
        return rawX >= left && rawX < left + target.width &&
            rawY >= top && rawY < top + target.height
    }

    private companion object {
        const val OPEN_SLOP_FACTOR = 5
        const val OPEN_DOMINANCE = 3
        const val OPEN_DISTANCE_DP = 96f
    }
}
