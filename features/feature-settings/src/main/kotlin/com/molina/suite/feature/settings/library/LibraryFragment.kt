package com.molina.suite.feature.settings.library

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.TerminalInputResult
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.SettingsDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Daftar entri Library: tambah, ubah, hapus, dan jalankan (▶) di tab Terminal. */
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private class Views(val list: RecyclerView, val empty: TextView, val error: TextView)

    private var views: Views? = null
    private var runJob: Job? = null
    private val adapter = LibraryAdapter(onRun = { runEntry(it) }, onEdit = { showEditor(it) })

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
        reload()
    }

    override fun onDestroyView() {
        views?.list?.adapter = null
        views = null
        super.onDestroyView()
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.list() }
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

    /** Mengetik isi entri ke sesi Terminal aktif lalu Enter; menunggu sesi siap bila tab baru dibuka. */
    private fun runEntry(entry: LibraryEntry) {
        if (runJob?.isActive == true) return
        if (entry.content.any { it == '\n' || it == '\r' }) {
            toast(getString(R.string.library_run_failed, getString(R.string.library_error_multiline)))
            return
        }
        val switcher = activity as? HostTabSwitcher
        if (switcher == null) {
            toast(getString(R.string.library_run_failed, getString(R.string.library_error_no_switcher)))
            return
        }
        val sender = SettingsDependencies.terminalInput()
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            var result = sender.sendLine(entry.content)
            switcher.showEngine(EngineId.TERMINAL)
            var attempts = 0
            while (result is TerminalInputResult.Rejected && attempts < MAX_ATTEMPTS) {
                delay(RETRY_DELAY_MS)
                result = sender.sendLine(entry.content)
                attempts++
            }
            if (result is TerminalInputResult.Rejected) {
                toast(getString(R.string.library_run_failed, result.reason))
            }
        }
    }

    private fun showEditor(entry: LibraryEntry?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_library_entry, null)
        val nameLayout = dialogView.findViewById<TextInputLayout>(R.id.library_layout_name)
        val contentLayout = dialogView.findViewById<TextInputLayout>(R.id.library_layout_content)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_name)
        val contentInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_content)
        val noteInput = dialogView.findViewById<TextInputEditText>(R.id.library_input_note)
        if (entry != null) {
            nameInput.setText(entry.name)
            contentInput.setText(entry.content)
            noteInput.setText(entry.note)
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
                val saved = if (entry == null) {
                    LibraryEntry.create(name, content, note)
                } else {
                    LibraryEntry(entry.id, name, content, note)
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
                            reload()
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
                    reload()
                }
                is LibraryResult.Failure -> toast(r.reason)
            }
        }
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        // Sesi pertama bisa butuh waktu (bind service dan bootstrap): tunggu sampai 10 detik.
        const val MAX_ATTEMPTS = 20
        const val RETRY_DELAY_MS = 500L
    }
}
