package com.molina.suite.feature.settings.library

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.molina.suite.core.storage.MolinaStorage
import com.molina.suite.feature.settings.PlaceholderTemplate
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.SettingsDependencies
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Daftar entri Library: tambah, ubah, hapus, pulihkan bawaan, dan jalankan (▶) di tab Terminal. */
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private class Views(val list: RecyclerView, val empty: TextView, val error: TextView)

    private var views: Views? = null
    private var runJob: Job? = null
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
        load()
    }

    override fun onDestroyView() {
        views?.list?.adapter = null
        views = null
        super.onDestroyView()
    }

    /** Memuat daftar; saat pertama kali (berkas belum ada) menyemai entri bawaan lebih dulu. */
    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext<LibraryResult<List<LibraryEntry>>>(Dispatchers.IO) {
                if (!repository.isInitialized()) {
                    val seeded = repository.addMissing(BuiltinLibrary.entries)
                    if (seeded is LibraryResult.Failure) return@withContext seeded
                }
                repository.list()
            }
            render(result)
        }
    }

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
        val line = ShellLine.build(entry, values)
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, line)
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
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            column.addView(
                layout,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
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
        else -> getString(R.string.placeholder_generic, name)
    }

    private fun defaultValue(name: String): String? = when (name) {
        "FILE", "OUT" -> "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/${MolinaStorage.DOWNLOADS_DIR_NAME}/"
        else -> null
    }

    private fun showEditor(entry: LibraryEntry?) {
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
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (entry == null) R.string.library_add_title else R.string.library_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.library_save, null)
            .setNegativeButton(R.string.library_cancel, null)
        if (entry != null) builder.setNeutralButton(R.string.library_delete, null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString().orEmpty().trim()
                val content = contentInput.text?.toString().orEmpty().trim()
                val note = noteInput.text?.toString().orEmpty().trim()
                nameLayout.error = if (name.isEmpty()) getString(R.string.library_error_name) else null
                contentLayout.error = when {
                    content.isEmpty() -> getString(R.string.library_error_content)
                    content.any { c -> c == '\n' || c == '\r' } -> getString(R.string.library_error_multiline)
                    else -> null
                }
                if (nameLayout.error != null || contentLayout.error != null) return@setOnClickListener
                val runIn = if (debianCheck.isChecked) RunMode.DEBIAN else RunMode.HOST
                val saved = if (entry == null) {
                    LibraryEntry.create(name, content, note, runIn)
                } else {
                    entry.copy(name = name, content = content, note = note, runIn = runIn)
                }
                save(saved) { dialog.dismiss() }
            }
            if (entry != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    confirmDelete(entry) { dialog.dismiss() }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(entry: LibraryEntry, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.library_delete_confirm, entry.name))
            .setPositiveButton(R.string.library_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = withContext(Dispatchers.IO) { repository.remove(entry.id) }) {
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
