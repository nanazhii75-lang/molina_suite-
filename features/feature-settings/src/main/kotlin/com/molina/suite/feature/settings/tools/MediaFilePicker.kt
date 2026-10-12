package com.molina.suite.feature.settings.tools

import android.Manifest
import android.content.Context
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** File media beserta ukurannya, dibaca di thread IO supaya daftar tidak menyentuh disk saat digulir. */
internal data class MediaPickerItem(val media: MediaFile, val sizeBytes: Long)

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
            val items = withContext(Dispatchers.IO) {
                MediaFiles.list().map { MediaPickerItem(it, it.file.length()) }
            }
            val ctx = fragment.context ?: return@launch
            if (items.isEmpty()) {
                fragment.toolsToast(fragment.getString(R.string.tools_no_media))
                return@launch
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.tools_pick_title)
                .setAdapter(MediaFileAdapter(ctx, items)) { _, which -> onPicked(items[which].media) }
                .setNegativeButton(R.string.tools_cancel, null)
                .show()
        }
    }
}

/**
 * Baris pemilih file: ikon video atau audio, nama file (hingga dua baris supaya nama yang mirip
 * tetap bisa dibedakan), lalu "ukuran • format • folder".
 */
private class MediaFileAdapter(
    private val context: Context,
    private val items: List<MediaPickerItem>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    private class Holder(root: View) {
        val icon: ImageView = root.findViewById(R.id.media_item_icon)
        val name: TextView = root.findViewById(R.id.media_item_name)
        val meta: TextView = root.findViewById(R.id.media_item_meta)
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): MediaPickerItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: inflater.inflate(R.layout.item_media_file, parent, false).also { it.tag = Holder(it) }
        val holder = row.tag as Holder
        val item = items[position]
        val media = item.media
        if (media.isAudio) {
            holder.icon.setImageResource(R.drawable.ic_media_audio)
            holder.icon.setBackgroundResource(R.drawable.ms_bg_media_audio)
            holder.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ms_status_on))
        } else {
            holder.icon.setImageResource(R.drawable.ic_media_video)
            holder.icon.setBackgroundResource(R.drawable.ms_bg_media_video)
            holder.icon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ms_amber))
        }
        holder.name.text = media.file.name
        holder.meta.text = context.getString(
            R.string.tools_pick_meta,
            Formatter.formatShortFileSize(context, item.sizeBytes),
            media.formatLabel,
            media.folderLabel
        )
        return row
    }
}
