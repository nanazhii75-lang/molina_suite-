package com.molina.suite.feature.settings.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
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
 * Layar Logcat di Pengaturan: log aplikasi molina sendiri, dikelompokkan per aktivitas (terbaru
 * di atas) dan per kategori (mpv, Terminal, Code, Setting, Lainnya), dengan warna per level, filter
 * level, pencarian, jeda, bersihkan, dan gulir otomatis. Pembacaan dan riwayat ada di
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
    private var categoryBar: LinearLayout? = null
    private val categoryChips = LinkedHashMap<LogCategory, TextView>()
    private var category = LogCategory.ALL
    private var shownCategory: LogCategory? = null
    private var shownEntries: List<LogcatEntry> = emptyList()
    private val expandedOverrides = HashMap<String, Boolean>()

    private var filter = LogcatFilter()
    private var paused = false
    private var shownRevision = -1L
    private var shownFilter: LogcatFilter? = null
    private var shownCount = 0
    private var viewSince = 0L
    private var shownSince = -1L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val entries = LogcatAdapter(
            onLine = { entry -> copyLine(entry) },
            onToggle = { header -> toggleGroup(header) },
            onCopyGroup = { header -> copyGroup(header) }
        )
        adapter = entries
        list = view.findViewById<RecyclerView>(R.id.logcat_list).also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = entries
            it.itemAnimator = null
        }
        statusView = view.findViewById(R.id.logcat_status)
        emptyView = view.findViewById(R.id.logcat_empty)
        autoScroll = view.findViewById(R.id.logcat_autoscroll)
        categoryBar = view.findViewById(R.id.logcat_category_bar)
        buildCategoryChips()
        levelButton = view.findViewById<MaterialButton>(R.id.logcat_level).also { button ->
            button.setOnClickListener { showLevelMenu(button) }
        }
        pauseButton = view.findViewById<MaterialButton>(R.id.logcat_pause).also {
            it.setOnClickListener { togglePause() }
        }
        view.findViewById<View>(R.id.logcat_clear).setOnClickListener { clear() }
        view.findViewById<View>(R.id.logcat_refresh).also {
            it.setOnClickListener { manualRefresh() }
            it.setOnLongClickListener { showAllHistory() }
        }
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
        shownSince = -1L
        categoryBar = null
        categoryChips.clear()
        shownCategory = null
        super.onDestroyView()
    }

    /** Menggambar ulang daftar bila isi buffer atau filter berubah (atau bila [force]). */
    private fun refresh(force: Boolean) {
        val target = adapter ?: return
        val revision = recorder.buffer.revision
        val unchanged = revision == shownRevision && filter == shownFilter &&
            viewSince == shownSince && category == shownCategory
        if (!force && unchanged) return
        val (full, firstVisible) = recorder.buffer.snapshotFrom(viewSince)
        val view = LogGrouping.build(full, firstVisible, filter, category, expandedOverrides)
        shownRevision = revision
        shownFilter = filter
        shownSince = viewSince
        shownCategory = category
        shownEntries = view.entries
        shownCount = view.entries.size
        target.submit(view.rows)
        updateChips(view.counts)
        emptyView?.visibility = if (view.rows.isEmpty()) View.VISIBLE else View.GONE
        // Yang terbaru ada di atas, jadi gulir otomatis mengikuti bagian atas daftar.
        if (view.rows.isNotEmpty() && autoScroll?.isChecked == true) list?.scrollToPosition(0)
        updateStatus()
    }

    /**
     * Segarkan: mulai pantau dari nol. Daftar hanya menampilkan log yang masuk setelah tombol
     * ditekan supaya perilaku aplikasi mudah dibaca; riwayat di buffer dan disk tidak dihapus.
     * Pemantauan yang sedang dijeda otomatis dilanjutkan.
     */
    private fun manualRefresh() {
        viewSince = recorder.buffer.total
        if (paused) {
            paused = false
            updatePauseLabel()
        }
        autoScroll?.isChecked = true
        refresh(true)
        toast(getString(R.string.logcat_refreshed))
    }

    /** Tahan Segarkan: kembali menampilkan seluruh riwayat yang ada di buffer. */
    private fun showAllHistory(): Boolean {
        viewSince = 0L
        refresh(true)
        toast(getString(R.string.logcat_show_all))
        return true
    }

    /** Gulir otomatis mati saat pengguna menggeser menjauhi atas dan hidup lagi saat tiba di atas. */
    private fun followScrollState(recyclerView: RecyclerView, newState: Int) {
        val box = autoScroll ?: return
        val canScrollUp = recyclerView.canScrollVertically(-1)
        if (newState == RecyclerView.SCROLL_STATE_DRAGGING && canScrollUp) {
            box.isChecked = false
        } else if (newState == RecyclerView.SCROLL_STATE_IDLE && !canScrollUp) {
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
            viewSince > 0L -> getString(R.string.logcat_status_since, shownCount, total)
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
        viewSince = 0L
        expandedOverrides.clear()
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

    /** Menyimpan baris yang sedang tampil ke folder log privat aplikasi. */
    private fun saveToFile() {
        val entries = shownEntries
        viewLifecycleOwner.lifecycleScope.launch { toast(output.save(entries)) }
    }

    /** Menyalin baris-baris terakhir yang sedang tampil ke papan klip. */
    private fun copyRecent() {
        toast(output.copyRecent(shownEntries))
    }

    private fun showLastCrash() {
        output.showLastCrash(requireContext())
    }

    /** Membuat satu tombol per [LogCategory]; pilihan dan jumlah masalahnya diperbarui di [updateChips]. */
    private fun buildCategoryChips() {
        val bar = categoryBar ?: return
        bar.removeAllViews()
        categoryChips.clear()
        for (item in LogCategory.values()) {
            val chip = layoutInflater.inflate(R.layout.item_logcat_category, bar, false) as TextView
            chip.setOnClickListener { selectCategory(item) }
            bar.addView(chip)
            categoryChips[item] = chip
        }
    }

    private fun selectCategory(item: LogCategory) {
        if (category == item) return
        category = item
        refresh(true)
    }

    private fun updateChips(counts: Map<LogCategory, CategoryCount>) {
        for ((item, chip) in categoryChips) {
            val problems = counts[item]?.problems ?: 0
            chip.text = if (problems > 0) {
                getString(R.string.logcat_category_problems, item.label, problems)
            } else {
                item.label
            }
            val selected = item == category
            chip.setBackgroundResource(if (selected) R.drawable.ms_bg_chip_on else R.drawable.ms_bg_chip_off)
            val color = when {
                problems > 0 -> R.color.ms_log_warn
                selected -> R.color.ms_text_primary
                else -> R.color.ms_text_secondary
            }
            chip.setTextColor(chip.context.getColor(color))
        }
    }

    private fun toggleGroup(header: LogRow.Header) {
        expandedOverrides[header.id] = !header.expanded
        refresh(true)
    }

    /** Menyalin seluruh baris satu aktivitas, apa adanya dan urut waktu, untuk dilaporkan. */
    private fun copyGroup(header: LogRow.Header) {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast(getString(R.string.logcat_copy_failed))
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("molina", header.entries.joinToString("\n") { it.toLine() }))
        toast(getString(R.string.logcat_group_copied))
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
