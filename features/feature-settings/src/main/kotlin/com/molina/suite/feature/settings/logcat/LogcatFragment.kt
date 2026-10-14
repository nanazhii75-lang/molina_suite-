package com.molina.suite.feature.settings.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_MS = 300L

/**
 * Layar Logcat di Pengaturan: log aplikasi molina sendiri (mpv, Terminal, Code) dengan warna per
 * level, filter level, pencarian, jeda, bersihkan, dan gulir otomatis. Pembacaan dan riwayat ada di
 * [LogRecorder] yang berjalan sejak aplikasi dibuka, jadi layar ini langsung menampilkan log yang
 * sudah terkumpul. Saat dijeda, tampilan berhenti diperbarui tetapi log tetap terkumpul di buffer.
 */
class LogcatFragment : Fragment(R.layout.fragment_logcat) {

    private val recorder by lazy(LazyThreadSafetyMode.NONE) { LogRecorder.get(requireContext()) }
    private val output by lazy(LazyThreadSafetyMode.NONE) { LogcatOutput(requireContext().applicationContext) }

    private var adapter: LogcatAdapter? = null
    private var list: RecyclerView? = null
    private var statusView: TextView? = null
    private var emptyView: TextView? = null
    private var levelButton: MaterialButton? = null
    private var pauseButton: MaterialButton? = null
    private var autoScroll: MaterialCheckBox? = null

    private var filter = LogcatFilter()
    private var paused = false
    private var shownRevision = -1L
    private var shownFilter: LogcatFilter? = null
    private var shownCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val entries = LogcatAdapter { entry -> copyLine(entry) }
        adapter = entries
        list = view.findViewById<RecyclerView>(R.id.logcat_list).also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = entries
            it.itemAnimator = null
        }
        statusView = view.findViewById(R.id.logcat_status)
        emptyView = view.findViewById(R.id.logcat_empty)
        autoScroll = view.findViewById(R.id.logcat_autoscroll)
        levelButton = view.findViewById<MaterialButton>(R.id.logcat_level).also { button ->
            button.setOnClickListener { showLevelMenu(button) }
        }
        pauseButton = view.findViewById<MaterialButton>(R.id.logcat_pause).also {
            it.setOnClickListener { togglePause() }
        }
        view.findViewById<View>(R.id.logcat_clear).setOnClickListener { clear() }
        view.findViewById<View>(R.id.logcat_save).setOnClickListener { saveToFile() }
        view.findViewById<View>(R.id.logcat_copy_recent).setOnClickListener { copyRecent() }
        view.findViewById<View>(R.id.logcat_last_crash).setOnClickListener { showLastCrash() }
        view.findViewById<EditText>(R.id.logcat_search).doAfterTextChanged { text ->
            filter = filter.copy(query = text?.toString().orEmpty())
            refresh(false)
        }
        list?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                followScrollState(recyclerView, newState)
            }
        })

        updateLevelLabel()
        updatePauseLabel()
        refresh(true)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (!paused && view.isShown) refresh(false)
                    updateStatus()
                    delay(REFRESH_MS)
                }
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        list = null
        statusView = null
        emptyView = null
        levelButton = null
        pauseButton = null
        autoScroll = null
        shownRevision = -1L
        shownFilter = null
        super.onDestroyView()
    }

    /** Menggambar ulang daftar bila isi buffer atau filter berubah (atau bila [force]). */
    private fun refresh(force: Boolean) {
        val target = adapter ?: return
        val revision = recorder.buffer.revision
        if (!force && revision == shownRevision && filter == shownFilter) return
        val entries = recorder.buffer.snapshot(filter)
        shownRevision = revision
        shownFilter = filter
        shownCount = entries.size
        target.submit(entries)
        emptyView?.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        if (entries.isNotEmpty() && autoScroll?.isChecked == true) list?.scrollToPosition(entries.lastIndex)
        updateStatus()
    }

    /** Gulir otomatis mati saat pengguna menggeser menjauhi dasar dan hidup lagi saat tiba di dasar. */
    private fun followScrollState(recyclerView: RecyclerView, newState: Int) {
        val box = autoScroll ?: return
        val canScrollDown = recyclerView.canScrollVertically(1)
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING && canScrollDown) {
            box.isChecked = false
        } else if (newState == RecyclerView.SCROLL_STATE_IDLE && !canScrollDown) {
            box.isChecked = true
        }
    }

    private fun updateStatus() {
        val view = statusView ?: return
        val total = recorder.buffer.size
        val ended = recorder.endMessage
        view.text = when {
            ended != null -> getString(R.string.logcat_ended, ended)
            paused -> getString(R.string.logcat_status_paused, shownCount, total)
            else -> getString(R.string.logcat_status, shownCount, total)
        }
    }

    private fun showLevelMenu(anchor: View) {
        val levels = LogcatLevel.values()
        val popup = PopupMenu(requireContext(), anchor)
        levels.forEachIndexed { index, level -> popup.menu.add(0, index, index, level.label) }
        popup.setOnMenuItemClickListener { item ->
            filter = filter.copy(minLevel = levels[item.itemId])
            updateLevelLabel()
            refresh(false)
            true
        }
        popup.show()
    }

    private fun updateLevelLabel() {
        levelButton?.text = getString(R.string.logcat_level_label, filter.minLevel.label)
    }

    private fun togglePause() {
        paused = !paused
        updatePauseLabel()
        if (!paused) refresh(true) else updateStatus()
    }

    private fun updatePauseLabel() {
        pauseButton?.setText(if (paused) R.string.logcat_resume else R.string.logcat_pause)
    }

    /**
     * Mengosongkan tampilan, riwayat di disk, dan buffer sistem lewat [LogRecorder]. Kegagalan
     * menghapus riwayat tidak mengganggu tampilan, jadi hasilnya sengaja tidak dilaporkan.
     */
    private fun clear() {
        recorder.clear()
        refresh(true)
    }

    private fun copyLine(entry: LogcatEntry) {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast(getString(R.string.logcat_copy_failed))
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("molina", entry.toLine()))
        toast(getString(R.string.logcat_copied))
    }

    /** Menyimpan baris yang sedang tampil ke /sdcard/Molina/Logs. */
    private fun saveToFile() {
        val entries = recorder.buffer.snapshot(filter)
        viewLifecycleOwner.lifecycleScope.launch { toast(output.save(entries)) }
    }

    /** Menyalin baris-baris terakhir yang sedang tampil ke papan klip. */
    private fun copyRecent() {
        toast(output.copyRecent(recorder.buffer.snapshot(filter)))
    }

    private fun showLastCrash() {
        output.showLastCrash(requireContext())
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
