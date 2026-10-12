package com.molina.suite.feature.code

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** Membagikan isi editor sebagai teks lewat pemilih aplikasi sistem. */
internal object CodeShare {

    /** Batas aman supaya Intent tidak melewati batas transaksi Binder. */
    const val MAX_CHARS = 200_000

    enum class Result { SENT, TOO_LARGE, NO_APP }

    fun send(context: Context, subject: String, text: String): Result {
        if (text.length > MAX_CHARS) return Result.TOO_LARGE
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.code_share_title))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            Result.SENT
        } catch (e: ActivityNotFoundException) {
            Result.NO_APP
        }
    }
}
