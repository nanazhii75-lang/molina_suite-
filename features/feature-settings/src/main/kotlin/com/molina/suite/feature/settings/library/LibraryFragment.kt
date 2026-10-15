package com.molina.suite.feature.settings.library

import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.LibraryDraft
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.PlaceholderTemplate
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.packages.PackageCatalog
import com.molina.suite.feature.settings.packages.PackageDetection
import com.molina.suite.feature.settings.packages.PackageDetector
import com.molina.suite.feature.settings.packages.PackageSpec
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.SettingsDependencies
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Daftar entri Library: tambah, ubah, hapus, pulihkan bawaan, dan jalankan (▶) di tab Terminal. */
class LibraryFragment : Fragment(R.layout.fragment_library), Refreshable {

    private class Views(val list: RecyclerView, val empty: TextView, val error: TextView)

    private var views: Views? = null
    private var runJob: Job? = null
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (views == null) return@registerForActivityResult
            if (granted) {
                load()
            } else {
                render(LibraryResult.Failure(getString(R.string.library_permission_denied)))
            }
        }
    private val adapter = LibraryAdapter(onRun = { prepareRun(it) }, onEdit = { showEditor(it) })

    private val repository: LibraryRepository
        get() = SettingsDependencies.libraryRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.library_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        views = Views(
            list,
            view.findViewById(R.id.library_empty),
            view.findViewById(R.id.library_error)
        )
        view.findViewById<View>(R.id.library_add).setOnClickListener { showEditor(null) }
        view.findViewById<View>(R.id.library_restore).setOnClickListener { restoreBuiltins() }
        observeDraft()
        load()
    }

    override fun onDestroyView() {
        views?.list?.adapter = null
        views = null
        super.onDestroyView()
    }

    /** Memuat daftar; saat pertama kali (berkas belum ada) menyemai entri bawaan lebih dulu. */
    private fun load() {
        if (!hasStoragePermission()) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            var report = EditorSync.Report.EMPTY
            val result = withContext<LibraryResult<List<LibraryEntry>>>(Dispatchers.IO) {
                if (!repository.isInitialized()) {
                    val seeded = repository.addMissing(BuiltinLibrary.entries)
                    if (seeded is LibraryResult.Failure) return@withContext seeded
                }
                BuiltinRetirer.apply(repository)
                BuiltinUpgrader.apply(repository)
                val listed = repository.list()
                if (listed is LibraryResult.Ok) {
                    report = EditorSync.apply(repository, listed.value)
                    if (report.imported.isNotEmpty()) repository.list() else listed
                } else {
                    listed
                }
            }
            announce(report)
            render(result)
            refreshRequirements()
        }
    }

    /** Dipanggil saat tab Settings tampil lagi; ikut menyinkronkan perubahan dari editor. */
    override fun refresh() {
        if (view == null) return
        if (hasStoragePermission()) load() else refreshRequirements()
    }

    override fun onResume() {
        super.onResume()
        refreshRequirements()
    }

    /** Menandai entri yang butuh paket Debian yang belum terpasang. Tanpa Debian, tidak ada penanda. */
    private fun refreshRequirements() {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            val installed = (detection as? PackageDetection.Ok)?.installed
            val labels = HashMap<String, String>()
            if (installed != null) {
                for ((id, specs) in LibraryRequirements.missingByEntry(installed)) {
                    labels[id] = specs.joinToString(", ") { getString(it.title) }
                }
            }
            adapter.submitMissing(labels)
        }
    }

    private fun hasStoragePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun render(result: LibraryResult<List<LibraryEntry>>) {
        val v = views ?: return
        when (result) {
            is LibraryResult.Ok -> {
                adapter.submitList(result.value)
                v.error.visibility = View.GONE
                v.empty.visibility = if (result.value.isEmpty()) View.VISIBLE else View.GONE
            }
            is LibraryResult.Failure -> {
                adapter.submitList(emptyList())
                v.empty.visibility = View.GONE
                v.error.text = getString(R.string.library_load_failed, result.reason)
                v.error.visibility = View.VISIBLE
            }
        }
    }

    /** Skrip kiriman tab Code: membuka dialog "Entri baru" yang sudah terisi nama dan isinya. */
    private fun observeDraft() {
        val board = SettingsDependencies.libraryDraft()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                board.pending.collect { draft ->
                    if (draft != null) {
                        board.consume(draft)
                        showEditor(null, draft)
                    }
                }
            }
        }
    }

    private fun restoreBuiltins() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = withContext(Dispatchers.IO) { repository.addMissing(BuiltinLibrary.entries) }) {
                is LibraryResult.Ok -> {
                    toast(
                        if (r.value == 0) getString(R.string.library_restore_none)
                        else getString(R.string.library_restore_done, r.value)
                    )
                    load()
                }
                is LibraryResult.Failure -> toast(r.reason)
            }
        }
    }

    /** Menanyakan nilai placeholder bila ada, lalu menjalankan entri. */
    private fun prepareRun(entry: LibraryEntry) {
        if (entry.id == IptvIndonesia.ENTRY_ID) {
            showIptv()
            return
        }
        if (LibraryRequirements.of(entry.id).isEmpty()) {
            proceedRun(entry)
            return
        }
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            val installed = (detection as? PackageDetection.Ok)?.installed
            val missing: List<PackageSpec> =
                if (installed == null) emptyList() else LibraryRequirements.missing(entry.id, installed)
            if (missing.isEmpty()) proceedRun(entry) else askInstall(entry, missing)
        }
    }

    /** Alat yang dibutuhkan belum terpasang: tawarkan install dulu, jalankan saja, atau batal. */
    private fun askInstall(entry: LibraryEntry, missing: List<PackageSpec>) {
        val ctx = context ?: return
        val names = missing.joinToString(", ") { getString(it.title) }
        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(entry.name)
            .setMessage(getString(R.string.library_requires_message, names))
            .setNegativeButton(R.string.library_cancel, null)
            .setNeutralButton(R.string.library_run_anyway) { _, _ -> proceedRun(entry) }
        if (entry.runIn == RunMode.DEBIAN) {
            val install = PackageCatalog.installAllCommand(missing)
            if (install != null) {
                builder.setPositiveButton(R.string.library_install_and_run) { _, _ ->
                    proceedRun(entry.copy(content = ShellLine.withInstall(entry.content, install)))
                }
            }
        }
        builder.show()
    }

    /** Dialog IPTV: putar, perbarui dari URL sendiri, atau kembali ke bundel bawaan. */
    private fun showIptv() {
        val ctx = requireContext()
        val appContext = ctx.applicationContext
        val pad = (24 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
        }
        val statusView = TextView(ctx).apply { text = getString(R.string.iptv_status_loading) }
        val urlLayout = TextInputLayout(ctx).apply { hint = getString(R.string.iptv_url_hint) }
        val urlInput = TextInputEditText(urlLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            maxLines = 1
        }
        urlLayout.addView(
            urlInput,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        column.addView(statusView)
        column.addView(
            urlLayout,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(IptvIndonesia.entry.name)
            .setView(column)
            .setPositiveButton(R.string.iptv_play, null)
            .setNeutralButton(R.string.iptv_update, null)
            .setNegativeButton(R.string.iptv_reset, null)
            .create()

        dialog.setOnShowListener {
            val play = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val update = dialog.getButton(DialogInterface.BUTTON_NEUTRAL)
            val reset = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            val buttons = listOf(play, update, reset)
            var urlFilled = false

            fun setBusy(busy: Boolean) {
                buttons.forEach { it.isEnabled = !busy }
                urlInput.isEnabled = !busy
            }

            fun describe(s: IptvSource.Status): String {
                var text = if (s.custom) {
                    val date = java.text.DateFormat
                        .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                        .format(java.util.Date(s.updatedAt))
                    getString(R.string.iptv_status_custom, s.channels, date)
                } else {
                    getString(R.string.iptv_status_builtin, s.channels)
                }
                if (s.url.startsWith("http://", ignoreCase = true)) {
                    text += "\n" + getString(R.string.iptv_http_warning)
                }
                return text
            }

            fun refreshStatus(prefix: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val s = withContext(Dispatchers.IO) { IptvSource.status(appContext) }
                    if (!urlFilled) {
                        urlFilled = true
                        urlInput.setText(s.url)
                    }
                    val base = describe(s)
                    statusView.text = if (prefix == null) base else prefix + "\n" + base
                }
            }

            play.setOnClickListener {
                setBusy(true)
                viewLifecycleOwner.lifecycleScope.launch {
                    val ready = withContext(Dispatchers.IO) { IptvSource.ensureActive(appContext) }
                    when (ready) {
                        is LibraryResult.Failure -> {
                            statusView.text = getString(R.string.library_run_failed, ready.reason)
                            setBusy(false)
                        }
                        is LibraryResult.Ok -> {
                            val host: android.content.Context = activity ?: appContext
                            val error = IptvSource.play(host, ready.value)
                            if (error == null) {
                                dialog.dismiss()
                            } else {
                                statusView.text = getString(R.string.library_run_failed, error)
                                setBusy(false)
                            }
                        }
                    }
                }
            }

            update.setOnClickListener {
                val typed = urlInput.text?.toString().orEmpty().trim()
                if (typed.isEmpty()) {
                    urlLayout.error = getString(R.string.library_error_value)
                    return@setOnClickListener
                }
                urlLayout.error = null
                setBusy(true)
                statusView.text = getString(R.string.iptv_downloading)
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { IptvSource.update(appContext, typed) }
                    setBusy(false)
                    when (result) {
                        is LibraryResult.Failure ->
                            refreshStatus(getString(R.string.iptv_update_failed, result.reason))
                        is LibraryResult.Ok ->
                            refreshStatus(getString(R.string.iptv_update_done, result.value))
                    }
                }
            }

            reset.setOnClickListener {
                setBusy(true)
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { IptvSource.reset(appContext) }
                    setBusy(false)
                    urlInput.setText("")
                    when (result) {
                        is LibraryResult.Failure ->
                            refreshStatus(getString(R.string.iptv_update_failed, result.reason))
                        is LibraryResult.Ok ->
                            refreshStatus(getString(R.string.iptv_reset_done, result.value))
                    }
                }
            }

            refreshStatus(null)
        }
        dialog.show()
    }

    private fun proceedRun(entry: LibraryEntry) {
        val names = PlaceholderTemplate.names(entry.content)
        if (names.isEmpty()) {
            execute(entry, emptyMap())
        } else {
            askValues(entry, names) { values -> execute(entry, values) }
        }
    }

    private fun execute(entry: LibraryEntry, values: Map<String, String>) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val line = if (ShellLine.isScript(entry.content)) {
                val staged = withContext(Dispatchers.IO) {
                    ScriptStager.stage(entry.id, ShellLine.fillScript(entry, values))
                }
                when (staged) {
                    is LibraryResult.Ok -> ShellLine.buildScriptRun(entry, staged.value)
                    is LibraryResult.Failure -> {
                        toast(getString(R.string.library_run_failed, staged.reason))
                        return@launch
                    }
                }
            } else {
                ShellLine.build(entry, values)
            }
            val reason = TerminalCommandRunner.run(host, "Library: " + entry.name, line)
            if (reason != null) toast(getString(R.string.library_run_failed, reason))
        }
    }

    private fun askValues(entry: LibraryEntry, names: List<String>, onDone: (Map<String, String>) -> Unit) {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
        }
        val fields = LinkedHashMap<String, Pair<TextInputLayout, TextInputEditText>>()
        for (name in names) {
            val layout = TextInputLayout(ctx).apply { hint = placeholderLabel(name) }
            val input = TextInputEditText(layout.context).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                maxLines = 1
                defaultValue(name)?.let {
                    setText(it)
                    setSelection(it.length)
                }
            }
            layout.addView(
                input,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            column.addView(
                layout,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            if (name == "DIR") {
                layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                layout.setEndIconDrawable(R.drawable.ic_folder_picker_folder)
                layout.endIconContentDescription = getString(R.string.folder_picker_choose)
                layout.setEndIconOnClickListener {
                    FolderPicker(ctx, FolderPicker.startFrom(input.text?.toString().orEmpty())) { picked ->
                        val path = picked.absolutePath
                        input.setText(path)
                        input.setSelection(path.length)
                    }.show()
                }
            }
            fields[name] = layout to input
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(entry.name)
            .setView(column)
            .setPositiveButton(R.string.library_run_action, null)
            .setNegativeButton(R.string.library_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val values = LinkedHashMap<String, String>()
                var valid = true
                for ((name, pair) in fields) {
                    val value = pair.second.text?.toString().orEmpty().trim()
                    pair.first.error = when {
                        value.isEmpty() -> getString(R.string.library_error_value)
                        value.any { c -> c == '\n' || c == '\r' } -> getString(R.string.library_error_multiline)
                        else -> null
                    }
                    if (pair.first.error != null) valid = false else values[name] = value
                }
                if (!valid) return@setOnClickListener
                dialog.dismiss()
                onDone(values)
            }
        }
        dialog.show()
    }

    private fun placeholderLabel(name: String): String = when (name) {
        "URL" -> getString(R.string.placeholder_url)
        "FILE" -> getString(R.string.placeholder_file)
        "OUT" -> getString(R.string.placeholder_out)
        "DIR" -> getString(R.string.placeholder_dir)
        else -> getString(R.string.placeholder_generic, name)
    }

    private fun defaultValue(name: String): String? = when (name) {
        "FILE", "OUT" -> "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}/"
        "DIR" -> "/sdcard/"
        else -> null
    }

    private fun showEditor(entry: LibraryEntry?, draft: LibraryDraft? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_library_entry, null)
        val nameLayout = dialogView.findViewById<TextInputLayout>(R.id.library_layout_name)
        val contentLayout = dialogView.findViewById<TextInputLayout>(R.id.library_layout_content)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_name)
        val contentInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_content)
        val noteInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_note)
        val debianCheck = dialogView.findViewById<MaterialCheckBox>(R.id.library_input_debian)
        if (entry != null) {
            nameInput.setText(entry.name)
            contentInput.setText(entry.content)
            noteInput.setText(entry.note)
            debianCheck.isChecked = entry.runIn == RunMode.DEBIAN
        } else {
            // Alat Library (python3, git, apt, ffmpeg) hanya ada di Debian; shell host minimal.
            debianCheck.isChecked = true
            if (draft != null) {
                nameInput.setText(draft.name)
                contentInput.setText(draft.content)
            }
        }

        val updateCounter = {
            val lines = LibraryLimits.lineCount(contentInput.text?.toString().orEmpty())
            contentLayout.helperText =
                getString(R.string.library_line_counter, lines, LibraryLimits.MAX_SCRIPT_LINES)
        }
        contentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateCounter()
        })
        updateCounter()

        var openAfterSave = false
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (entry == null) R.string.library_add_title else R.string.library_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.library_save, null)
            .setNegativeButton(R.string.library_cancel, null)
        if (entry != null) builder.setNeutralButton(R.string.library_delete, null)
        val dialog = builder.create()
        if (draft != null) dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            dialogView.findViewById<MaterialButton>(R.id.library_edit_in_editor).setOnClickListener {
                openAfterSave = true
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val openNow = openAfterSave
                openAfterSave = false
                val name = nameInput.text?.toString().orEmpty().trim()
                val content = contentInput.text?.toString().orEmpty().trim()
                val note = noteInput.text?.toString().orEmpty().trim()
                nameLayout.error = if (name.isEmpty()) getString(R.string.library_error_name) else null
                contentLayout.error = when {
                    content.isEmpty() -> getString(R.string.library_error_content)
                    LibraryLimits.lineCount(content) > LibraryLimits.MAX_SCRIPT_LINES ->
                        getString(R.string.library_error_too_long, LibraryLimits.MAX_SCRIPT_LINES)
                    else -> null
                }
                if (nameLayout.error != null || contentLayout.error != null) return@setOnClickListener
                val runIn = when {
                    debianCheck.isChecked -> RunMode.DEBIAN
                    entry?.runIn == RunMode.ANDROID -> RunMode.ANDROID
                    else -> RunMode.HOST
                }
                val saved = if (entry == null) {
                    LibraryEntry.create(name, content, note, runIn)
                } else {
                    entry.copy(name = name, content = content, note = note, runIn = runIn)
                }
                save(saved) {
                    dialog.dismiss()
                    if (openNow) openInEditor(saved)
                }
            }
            if (entry != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    confirmDelete(entry) { dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    /** Menyimpan isi mentah entri ke salinan suntingan, meminta tab Code membukanya, lalu pindah ke sana. */
    private fun openInEditor(entry: LibraryEntry) {
        val host = activity ?: return
        val board = SettingsDependencies.editorOpen()
        viewLifecycleOwner.lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) { ScriptStager.stageForEdit(entry.id, entry.content) }
            when (staged) {
                is LibraryResult.Ok -> {
                    board.request(staged.value)
                    (host as? HostTabSwitcher)?.showEngine(EngineId.CODE)
                }
                is LibraryResult.Failure -> toast(getString(R.string.library_editor_stage_failed, staged.reason))
            }
        }
    }

    /** Mengumumkan hasil sinkron balik dari editor: entri yang diperbarui dan yang ditolak. */
    private fun announce(report: EditorSync.Report) {
        for ((name, lines) in report.imported) toast(getString(R.string.library_editor_imported, name, lines))
        for ((name, reason) in report.rejected) toast(getString(R.string.library_editor_rejected, name, reason))
    }

    private fun confirmDelete(entry: LibraryEntry, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.library_delete_confirm, entry.name))
            .setPositiveButton(R.string.library_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = withContext(Dispatchers.IO) {
                        repository.remove(entry.id).also {
                            if (it is LibraryResult.Ok) ScriptStager.removeEditCopy(entry.id)
                        }
                    }) {
                        is LibraryResult.Ok -> {
                            onDeleted()
                            load()
                        }
                        is LibraryResult.Failure -> toast(r.reason)
                    }
                }
            }
            .setNegativeButton(R.string.library_cancel, null)
            .show()
    }

    private fun save(entry: LibraryEntry, onSaved: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = withContext(Dispatchers.IO) { repository.upsert(entry) }) {
                is LibraryResult.Ok -> {
                    onSaved()
                    load()
                }
                is LibraryResult.Failure -> toast(r.reason)
            }
        }
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
