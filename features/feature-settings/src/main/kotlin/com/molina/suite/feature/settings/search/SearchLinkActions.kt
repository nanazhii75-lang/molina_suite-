package com.molina.suite.feature.settings.search

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Aksi pada tautan hasil. Hanya menerima http/https; skema lain ditolak. */
object SearchLinkActions {

    fun isOpenable(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    /** Membuka tautan di browser pilihan sistem. Mengembalikan false bila tidak ada browser. */
    fun openInBrowser(context: Context, url: String): Boolean {
        if (!isOpenable(url)) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun copy(context: Context, url: String): Boolean {
        if (!isOpenable(url)) return false
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        manager.setPrimaryClip(ClipData.newPlainText("tautan", url))
        return true
    }

    fun share(context: Context, url: String, chooserTitle: String): Boolean {
        if (!isOpenable(url)) return false
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
        val chooser = Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
