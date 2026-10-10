package com.molina.suite.feature.code

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.HostChromeController
import com.molina.suite.core.storage.MolinaStorage
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import java.io.File

/**
 * Tab Code: header berkas, editor Sora, dan toolbar. Hanya urusan tampilan;
 * dokumen dan logika berkas ada di [CodeEditorSession] dan [CodeFileRepository].
 */
class CodeTabFragment : Fragment() {

    private val session: CodeEditorSession
        get() = CodeEngineModule.requireEngine().session

    private var editor: CodeEditor? = null
    private var header: View? = null
    private var titleView: TextView? = null
    private var pathView: TextView? = null
    private var emptyView: View? = null
    private var actionBar: View? = null
    private var keyboardWatcher: CodeKeyboardWatcher? = null
    private var afterPermission: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = afterPermission
        afterPermission = null
        if (granted) pending?.invoke() else toast(getString(R.string.code_permission_denied))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = inflater.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val editorView = CodeEditorFactory.create(context)
        editor = editorView

        val title = TextView(context).apply {
            setTextColor(CodePalette.TEXT)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val path = TextView(context).apply {
            setTextColor(CodePalette.TEXT_MUTED)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
        }
        val headerView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(title)
            addView(path)
        }
        val empty = buildEmptyState(context)
        val bar = buildActionBar(context)
        val content = FrameLayout(context).apply {
            addView(editorView, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(empty, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        header = headerView
        titleView = title
        pathView = path
        emptyView = empty
        actionBar = bar

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.BACKGROUND)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(headerView, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyboardWatcher = CodeKeyboardWatcher(requireActivity().window, view) { visible ->
            (activity as? HostChromeController)?.setChromeCompact(visible)
        }
        render(session.document.value)
        val target = requireNotNull(editor)
        viewLifecycleOwner.lifecycleScope.launch { session.attach(target) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.document.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateKeyboardWatcher()
    }

    override fun onPause() {
        keyboardWatcher?.stop()
        hideIme()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Shell memakai add/hide, jadi lifecycle tidak berubah saat tab disembunyikan.
        updateKeyboardWatcher()
    }

    override fun onDestroyView() {
        keyboardWatcher?.stop()
        keyboardWatcher = null
        editor?.let { target ->
            session.detach(target)
            target.release()
        }
        editor = null
        header = null
        titleView = null
        pathView = null
        emptyView = null
        actionBar = null
        super.onDestroyView()
    }

    private fun updateKeyboardWatcher() {
        if (isResumed && !isHidden) {
            keyboardWatcher?.start()
        } else {
            keyboardWatcher?.stop()
            hideIme()
        }
    }

    private fun render(doc: CodeDocument?) {
        val hasDocument = doc != null
        header?.visibility = if (hasDocument) View.VISIBLE else View.GONE
        actionBar?.visibility = if (hasDocument) View.VISIBLE else View.GONE
        editor?.visibility = if (hasDocument) View.VISIBLE else View.GONE
        emptyView?.visibility = if (hasDocument) View.GONE else View.VISIBLE
        if (doc == null) return
        val title = SpannableStringBuilder(doc.file.name)
        if (doc.dirty) {
            val start = title.length
            title.append(" \u25CF")
            title.setSpan(
                ForegroundColorSpan(CodePalette.ACCENT),
                start,
                title.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        titleView?.text = title
        pathView?.text = doc.file.parent.orEmpty()
    }

    private fun buildEmptyState(context: Context): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val title = TextView(context).apply {
            setText(R.string.code_empty_title)
            setTextColor(CodePalette.TEXT)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val hint = TextView(context).apply {
            setText(R.string.code_empty_hint)
            setTextColor(CodePalette.TEXT_MUTED)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        }
        val open = Button(context).apply {
            setText(R.string.code_action_open)
            setOnClickListener { requestOpen() }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(title, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(open, LinearLayout.LayoutParams(WRAP, WRAP))
        }
    }

    private fun buildActionBar(context: Context): View = CodeActionBar.build(
        context,
        listOf(
            CodeAction(R.string.code_key_open) { requestOpen() },
            CodeAction(R.string.code_key_paste) { session.paste() },
            CodeAction(R.string.code_key_undo) { session.undo() },
            CodeAction(R.string.code_key_redo) { session.redo() },
            CodeAction(R.string.code_key_left) { session.moveCursorLeft() },
            CodeAction(R.string.code_key_right) { session.moveCursorRight() },
            CodeAction(R.string.code_key_save) { saveCurrent() },
            CodeAction(R.string.code_key_close) { requestClose() }
        )
    )

    private fun requestOpen() {
        confirmDiscardIfDirty { ensureStorage { showPicker() } }
    }

    private fun requestClose() {
        confirmDiscardIfDirty { session.close() }
    }

    private fun showPicker() {
        val context = context ?: return
        when (val prepared = MolinaStorage.ensureDirectories()) {
            is MolinaStorage.EnsureResult.Ready ->
                CodeFilePicker(context, prepared.root) { openFile(it) }.show()
            is MolinaStorage.EnsureResult.Failed ->
                toast(getString(R.string.code_picker_error, prepared.reason))
        }
    }

    private fun openFile(file: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = session.open(file)) {
                is CodeFileLoad.Loaded -> Unit
                is CodeFileLoad.TooLarge -> toast(
                    getString(
                        R.string.code_file_too_large,
                        formatSize(result.sizeBytes),
                        formatSize(result.limitBytes)
                    )
                )
                CodeFileLoad.Binary -> toast(getString(R.string.code_file_binary))
                is CodeFileLoad.Failed ->
                    toast(getString(R.string.code_open_failed, file.name, result.reason))
            }
        }
    }

    private fun saveCurrent() {
        viewLifecycleOwner.lifecycleScope.launch { save() }
    }

    private suspend fun save(): Boolean {
        val name = session.document.value?.file?.name.orEmpty()
        return when (val result = session.save()) {
            CodeFileSave.Saved -> {
                toast(getString(R.string.code_save_ok, name))
                true
            }
            is CodeFileSave.Failed -> {
                toast(getString(R.string.code_save_failed, result.reason))
                false
            }
        }
    }

    private fun confirmDiscardIfDirty(proceed: () -> Unit) {
        val doc = session.document.value
        if (doc == null || !doc.dirty) {
            proceed()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.code_unsaved_title)
            .setMessage(getString(R.string.code_unsaved_message, doc.file.name))
            .setPositiveButton(R.string.code_unsaved_save) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { if (save()) proceed() }
            }
            .setNegativeButton(R.string.code_unsaved_discard) { _, _ -> proceed() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensureStorage(then: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            then()
        } else {
            afterPermission = then
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hideIme() {
        val target = view ?: return
        val imm = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    private fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(requireContext(), bytes)

    private fun toast(message: String) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
