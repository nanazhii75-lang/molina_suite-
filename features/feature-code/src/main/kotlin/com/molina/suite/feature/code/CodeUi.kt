package com.molina.suite.feature.code

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.TypedValue
import android.widget.ImageButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Pembantu kecil untuk membuat view tab Code secara programatik. */
internal object CodeUi {

    fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * Tombol ikon yang tidak mengambil fokus, supaya editor tetap fokus dan
     * keyboard layar tidak menutup saat tombol disentuh.
     */
    fun iconButton(
        context: Context,
        @DrawableRes icon: Int,
        @StringRes description: Int,
        tint: Int = CodePalette.TEXT,
        onClick: () -> Unit
    ): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        val label = context.getString(description)
        contentDescription = label
        tooltipText = label
        isFocusable = false
        isFocusableInTouchMode = false
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true) &&
            ripple.resourceId != 0
        ) {
            setBackgroundResource(ripple.resourceId)
        } else {
            setBackgroundColor(Color.TRANSPARENT)
        }
        setOnClickListener { onClick() }
    }
}
