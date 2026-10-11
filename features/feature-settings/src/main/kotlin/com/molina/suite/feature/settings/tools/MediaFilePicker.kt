package com.molina.suite.feature.settings.tools

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Memilih file media dari /sdcard/Molina. Sengaja bukan pemilih file sistem: terminal butuh
 * path asli, sedangkan pemilih sistem memberi alamat content:// yang tidak dikenal ffmpeg.
 * Harus dibuat saat fragment dibuat (mendaftarkan peminta izin).
 */
internal class MediaFilePicker(private val fragment: Fragment) {

    private var pending: ((MediaFile) -> Unit)? = null

    private val permission =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = pending
            pending = null
            when {
                !granted -> fragment.toolsToast(fragment.getString(R.string.tools_permission_denied))
                callback != null -> show(callback)
            }
        }

    /** Panggil dari main thread saat view fragment hidup. */
    fun pick(onPicked: (MediaFile) -> Unit) {
        val ctx = fragment.context ?: return
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            show(onPicked)
        } else {
            pending = onPicked
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun show(onPicked: (MediaFile) -> Unit) {
        if (fragment.view == null) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { MediaFiles.list() }
            val ctx = fragment.context ?: return@launch
            if (files.isEmpty()) {
                fragment.toolsToast(fragment.getString(R.string.tools_no_media))
                return@launch
            }
            val labels = Array(files.size) { i ->
                files[i].relativePath + "  ·  " + Formatter.formatShortFileSize(ctx, files[i].file.length())
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.tools_pick_title)
                .setItems(labels) { _, which -> onPicked(files[which]) }
                .setNegativeButton(R.string.tools_cancel, null)
                .show()
        }
    }
}
