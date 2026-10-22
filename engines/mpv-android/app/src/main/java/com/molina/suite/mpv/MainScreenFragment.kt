package com.molina.suite.mpv

import com.molina.suite.mpv.filepicker.DocumentPickerFragment
import com.molina.suite.mpv.config.SettingsActivity
import com.molina.suite.mpv.databinding.FragmentMainScreenBinding
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.molina.suite.mpv.search.QueueBar
import com.molina.suite.mpv.search.YoutubeResultsPanel
import com.molina.suite.mpv.search.MolinaPlaylist
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeSearchController
import com.molina.suite.mpv.history.HistoryPanel
import com.molina.suite.mpv.history.PlayHistory
import com.molina.suite.mpv.history.PlayHistoryEntry

class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {
    private lateinit var binding: FragmentMainScreenBinding

    private lateinit var documentTreeOpener: ActivityResultLauncher<Uri?>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playerLauncher: ActivityResultLauncher<Intent>

    private var firstRun = false
    private var returningFromPlayer = false

    private var prev = ""
    private var prevData: String? = null
    private var lastPath = ""
    private var resultsPanel: YoutubeResultsPanel? = null
    private var queueBar: QueueBar? = null
    private var historyPanel: HistoryPanel? = null

    /** Permintaan cari dari Terminal (molina-cari); satu kolom dua fungsi seperti pencarian biasa. */
    private val searchReceiver: (String) -> Unit = { query -> if (isAdded) submitSearchInput(query) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = savedInstanceState == null

        documentTreeOpener = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let { root ->
                requireContext().contentResolver.takePersistableUriPermission(
                    root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                saveChoice("doc", root.toString())

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", root.toString())
                filePickerLauncher.launch(i)
            }
        }
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            it.data?.getStringExtra("last_path")?.let { path ->
                lastPath = path
            }
            it.data?.getStringExtra("path")?.let { path ->
                playFile(path, MpvOpenKind.LOCAL)
            }
        }
        playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // we don't care about the result but remember that we've been here
            returningFromPlayer = true
            Log.v(TAG, "returned from player")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentMainScreenBinding.bind(view)

        binding.docBtn.setOnClickListener {
            try {
                documentTreeOpener.launch(LOCAL_ROOT_URI)
            } catch (e: ActivityNotFoundException) {
                // Android TV doesn't come with a document picker and certain versions just throw
                // instead of handling this gracefully
                binding.docBtn.isEnabled = false
            }
        }
        binding.ytBtn.setOnClickListener { showYoutubeSearchDialog() }
        binding.clipBtn.setOnClickListener { playClipboardLink() }
        resultsPanel = YoutubeResultsPanel(
            binding.resultsPanel, binding.ytBtn, binding.resetBtn,
            onLongPress = { showResultActions(it) }
        ) { result ->
            playResults(result)
        }.also { it.attach() }
        queueBar = QueueBar(binding.queueBar) { items -> launchPlaylist(items, 0) }.also { it.attach() }
        historyPanel = HistoryPanel(binding.resultsEmpty, onActions = { showHistoryActions(it) }) { playHistoryEntry(it) }
            .also { it.attach(); it.refresh() }
        MpvSearchEntry.attach(searchReceiver)
        // Ikon tanpa teks: tekan lama menampilkan labelnya.
        listOf(binding.ytBtn, binding.clipBtn, binding.docBtn, binding.settingsBtn, binding.resetBtn).forEach {
            TooltipCompat.setTooltipText(it, it.contentDescription)
        }
        binding.settingsBtn.setOnClickListener {
            saveChoice("") // will reset
            startActivity(Intent(context, SettingsActivity::class.java))
        }

        if (BuildConfig.DEBUG) {
            binding.settingsBtn.setOnLongClickListener { showDebugMenu(); true }
        }

        onConfigurationChanged(view.resources.configuration)
    }

    override fun onDestroyView() {
        MpvSearchEntry.detach(searchReceiver)
        resultsPanel?.detach()
        resultsPanel = null
        queueBar?.detach()
        queueBar = null
        historyPanel?.detach()
        historyPanel = null
        super.onDestroyView()
    }

    private fun showDebugMenu() {
        assert(BuildConfig.DEBUG)
        val context = requireContext()
        with (AlertDialog.Builder(context)) {
            setItems(DEBUG_ACTIVITIES) { dialog, idx ->
                dialog.dismiss()
                val intent = Intent(Intent.ACTION_MAIN)
                intent.setClassName(context, "${context.packageName}.${DEBUG_ACTIVITIES[idx]}")
                startActivity(intent)
            }
            create().show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // phone screens are too small to show the action buttons alongside the logo
        if (!Utils.isXLargeTablet(requireContext())) {
            binding.logo.isVisible = newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE
        }
    }

    override fun onResume() {
        super.onResume()
        historyPanel?.refresh()
        if (firstRun) {
            restoreChoice()
        } else if (returningFromPlayer) {
            restoreChoice(prev, prevData)
        }
        firstRun = false
        returningFromPlayer = false
    }

    private fun saveChoice(type: String, data: String? = null) {
        if (prev != type)
            lastPath = ""
        prev = type
        prevData = data

        if (!binding.switch1.isChecked)
            return
        binding.switch1.isChecked = false
        with (PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()) {
            putString("MainScreenFragment_remember", type)
            if (data == null)
                remove("MainScreenFragment_remember_data")
            else
                putString("MainScreenFragment_remember_data", data)
            commit()
        }
    }

    private fun restoreChoice() {
        with (PreferenceManager.getDefaultSharedPreferences(requireContext())) {
            restoreChoice(
                getString("MainScreenFragment_remember", "") ?: "",
                getString("MainScreenFragment_remember_data", "")
            )
        }
    }

    private fun restoreChoice(type: String, data: String?) {
        when (type) {
            "doc" -> {
                val uri = Uri.parse(data)
                // check that we can still access the folder
                if (!DocumentPickerFragment.isTreeUsable(requireContext(), uri))
                    return

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", uri.toString())
                if (lastPath != "")
                    i.putExtra("default_path", lastPath)
                filePickerLauncher.launch(i)
            }
            "url" -> binding.ytBtn.callOnClick()
        }
    }

    private fun showYoutubeSearchDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
            setHint(R.string.search_youtube_hint)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.action_search_youtube)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ -> submitSearchInput(input.text.toString()) }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.cancel() }
            .create()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    /** Satu kolom dua fungsi: URL langsung diputar, selain itu dicari di YouTube. */
    private fun submitSearchInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val url = SearchInput.asUrl(text)
        if (url == null) {
            searchYoutube(text)
            return
        }
        UrlPlayback.check(this, url) { playFile(url, MpvOpenKind.URL) }
    }

    /** Memulai pencarian YouTube; hasilnya tampil di panel hasil. */
    private fun searchYoutube(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        YoutubeSearchController.search(trimmed)
    }

    /**
     * Memutar [selected] dan menyerahkan seluruh daftar hasil ke pemutar sebagai playlist,
     * sehingga Next/Previous mengikuti urutan hasil pencarian.
     */
    private fun playResults(selected: YoutubeResult) {
        val items = YoutubeSearchController.fetchedItems()
        val index = items.indexOfFirst { it.id == selected.id }
        if (index < 0) {
            playFile(selected.watchUrl, MpvOpenKind.SEARCH)
            return
        }
        launchPlaylist(items, index)
    }

    /**
     * Memutar [items] sebagai playlist mulai dari [index]; Next/Previous di pemutar mengikuti
     * urutan [items]. Satu item saja diputar biasa (MolinaPlaylist menolak playlist < 2 item).
     */
    private fun launchPlaylist(items: List<YoutubeResult>, index: Int) {
        val intent = Intent()
        intent.putExtra("filepath", items[index].watchUrl)
        intent.putExtra(MpvOpenKind.EXTRA, MpvOpenKind.SEARCH.name)
        MolinaPlaylist.put(intent, items.map { it.watchUrl }, items.map { it.title }, index)
        intent.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(intent)
    }

    /** Memutar URL pertama yang ada di clipboard; memberi tahu bila tidak ada. */
    private fun playClipboardLink() {
        val context = requireContext()
        val text = try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca clipboard: $e")
            null
        }
        val url = SharedUrl.extract(text)
        if (url == null) {
            Toast.makeText(context, R.string.molina_clipboard_no_link, Toast.LENGTH_SHORT).show()
            return
        }
        UrlPlayback.check(this, url) { playFile(url, MpvOpenKind.URL) }
    }

    /** Menu aksi satu baris (hasil pencarian atau riwayat); [actions] berpasangan label dan tindakan. */
    private fun showRowActions(title: String, actions: List<Pair<String, () -> Unit>>) {
        if (actions.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    /** Tekan lama pada hasil pencarian. */
    private fun showResultActions(item: YoutubeResult) {
        showRowActions(
            item.title,
            listOf(getString(R.string.molina_format_info_title) to { openFormatInfo(item.watchUrl) })
        )
    }

    /** Tekan lama pada riwayat: Info format (hanya tautan) dan hapus dari riwayat. */
    private fun showHistoryActions(entry: PlayHistoryEntry) {
        val actions = ArrayList<Pair<String, () -> Unit>>()
        if (FormatInfoEntry.isSupported(entry.path)) {
            actions.add(getString(R.string.molina_format_info_title) to { openFormatInfo(entry.path) })
        }
        actions.add(getString(R.string.molina_history_remove_one) to {
            PlayHistory.remove(requireContext(), entry.path)
            historyPanel?.refresh()
        })
        showRowActions(entry.title, actions)
    }

    private fun openFormatInfo(url: String) {
        if (!FormatInfoEntry.open(requireActivity(), url)) {
            Toast.makeText(requireContext(), R.string.molina_format_info_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Memutar ulang entri riwayat; posisi terakhir dilanjutkan oleh mpv (watch-later). */
    private fun playHistoryEntry(entry: PlayHistoryEntry) {
        val path = entry.path
        if (path.startsWith("http://") || path.startsWith("https://")) {
            UrlPlayback.check(this, path) { playFile(path, MpvOpenKind.URL) }
            return
        }
        if (!java.io.File(path).exists()) {
            PlayHistory.remove(requireContext(), path)
            historyPanel?.refresh()
            Toast.makeText(requireContext(), R.string.molina_history_missing, Toast.LENGTH_SHORT).show()
            return
        }
        playFile(path, MpvOpenKind.LOCAL)
    }

    private fun playFile(filepath: String, kind: MpvOpenKind) {
        val i: Intent
        if (filepath.startsWith("content://")) {
            i = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
        } else {
            i = Intent()
            i.putExtra("filepath", filepath)
        }
        i.putExtra(MpvOpenKind.EXTRA, kind.name)
        i.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(i)
    }

    companion object {
        private const val TAG = "mpv"

        // Folder awal picker "Open Local": akar penyimpanan internal (/sdcard).
        private val LOCAL_ROOT_URI: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:"
        )

        // list of debug or testing activities that can be launched
        private val DEBUG_ACTIVITIES = arrayOf(
            "IntentTestActivity"
        )
    }
}
