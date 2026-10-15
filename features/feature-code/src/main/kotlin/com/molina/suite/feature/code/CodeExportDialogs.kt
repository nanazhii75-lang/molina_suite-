package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes

/** Dialog fitur Ekspor: proses dan pesan. Hanya tampilan; alurnya ada di [CodeExportController]. */
internal object CodeExportDialogs {

    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun showRunning(context: Context, message: String, onCancel: () -> Unit): AlertDialog {
        val pad = CodeUi.dp(context, 20)
        val size = CodeUi.dp(context, 40)
        val messageView = TextView(context)
        messageView.text = message
        messageView.setPadding(pad / 2, 0, 0, 0)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(ProgressBar(context), LinearLayout.LayoutParams(size, size))
            addView(messageView, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.code_export_running_title)
            .setView(row)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .create()
        dialog.show()
        return dialog
    }

    /**
     * Pesan yang teksnya bisa disalin. Bila [actionLabel] dan [onAction] diisi, tombol tambahan
     * ditampilkan di samping OK (mis. menuju menu Paket).
     */
    fun showMessage(
        context: Context,
        @StringRes title: Int,
        message: String,
        @StringRes actionLabel: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        val body = TextView(context)
        body.text = message
        body.setTextIsSelectable(true)
        val pad = CodeUi.dp(context, 20)
        body.setPadding(pad, CodeUi.dp(context, 8), pad, 0)
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.code_check_ok, null)
        if (actionLabel != null && onAction != null) {
            builder.setNeutralButton(actionLabel) { _, _ -> onAction() }
        }
        builder.show()
    }
}
