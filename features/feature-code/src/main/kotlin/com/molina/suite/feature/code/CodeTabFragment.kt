package com.molina.suite.feature.code

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
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
import android.widget.EditText
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
 * Tab Code: header, editor Sora, bar pencarian, dan toolbar. Hanya urusan tampilan;
 * dokumen dan logika berkas ada di [CodeEditorSession] dan [CodeFileRepository].
 */
class CodeTabFragment : Fragment() {

    private val session: CodeEditorSession
        get() = CodeEngineModule.requireEngine().session

    private var editor: CodeEditor? = null
    private var titleView: TextView? = null
    private var pathView: TextView? = null
    private var searchBar: CodeSearchBar? = null
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
        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(path)
        }
        val menuButton = CodeUi.iconButton(context, R.drawable.ic_code_menu, R.string.code_header_menu) { }
        menuButton.setOnClickListener { showFileMenu(it) }
        val moreButton = CodeUi.iconButton(context, R.drawable.ic_code_more, R.string.code_header_more) { }
        moreButton.setOnClickListener { showActionMenu(it) }

        val headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(CodeUi.dp(context, 4), CodeUi.dp(context, 4), CodeUi.dp(context, 4), CodeUi.dp(context, 4))
            addView(menuButton, LinearLayout.LayoutParams(CodeUi.dp(context, 44), CodeUi.dp(context, 48)))
            addView(
                titleColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = CodeUi.dp(context, 4) }
            )
            addView(moreButton, LinearLayout.LayoutParams(CodeUi.dp(context, 44), CodeUi.dp(context, 48)))
        }

        val bar = CodeSearchBar(context, object : CodeSearchBar.Callbacks {
            override fun onRequestChanged(request: CodeSearchRequest) = session.search(request)
            override fun onFind() = session.searchNext()
            override fun onReplace(replacement: String) {
                if (!session.replaceCurrent(replacement)) notifyReplaceUnavailable()
            }
            override fun onReplaceAll(replacement: String) {
                if (!session.replaceAll(replacement)) notifyReplaceUnavailable()
            }
            override fun onClosed() = session.stopSearch()
        })
        val actions = buildActionBar(context)

        titleView = title
        pathView = path
        searchBar = bar

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.BACKGROUND)
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(headerView, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(editorView, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(bar.view, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(actions, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyboardWatcher = CodeKeyboardWatcher(requireActivity().window, view) { visible ->
            (activity as? HostChromeController)?.setChromeCompact(visible)
        }
        renderDocument(session.document.value)
        val target = requireNotNull(editor)
        viewLifecycleOwner.lifecycleScope.launch { session.attach(target) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { session.document.collect { renderDocument(it) } }
                launch { session.search.collect { searchBar?.render(it) } }
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
        titleView = null
        pathView = null
        searchBar = null
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

    private fun renderDocument(doc: CodeDocument) {
        val name = doc.file?.name ?: getString(R.string.code_untitled)
        val title = SpannableStringBuilder(name)
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
        pathView?.text = doc.file?.parent ?: MolinaStorage.sharedRoot().path
    }

    private fun notifyReplaceUnavailable() {
        toast(
            getString(
                if (session.options.value.readOnly) R.string.code_search_readonly else R.string.code_search_no_match
            )
        )
    }

    private fun buildActionBar(context: Context): View = CodeActionBar.build(
        context,
        listOf(
            CodeAction(R.drawable.ic_code_folder, R.string.code_key_open) { requestOpen() },
            CodeAction(R.drawable.ic_code_paste, R.string.code_key_paste) { session.paste() },
            CodeAction(R.drawable.ic_code_undo, R.string.code_key_undo) { session.undo() },
            CodeAction(R.drawable.ic_code_redo, R.string.code_key_redo) { session.redo() },
            CodeAction(R.drawable.ic_code_search, R.string.code_menu_search) { openSearch() },
            CodeAction(R.drawable.ic_code_arrow_left, R.string.code_key_left) { session.moveCursorLeft() },
            CodeAction(R.drawable.ic_code_arrow_right, R.string.code_key_right) { session.moveCursorRight() },
            CodeAction(R.drawable.ic_code_save, R.string.code_key_save) { saveCurrent() },
            CodeAction(R.drawable.ic_code_close, R.string.code_key_close) { requestClose() }
        )
    )

    private fun showFileMenu(anchor: View) {
        CodeMenus.showFileMenu(requireContext(), anchor, session.document.value, object : CodeFileMenuListener {
            override fun onNew() = requestNew()
            override fun onOpen() = requestOpen()
            override fun onSave() = saveCurrent()
            override fun onSaveAs() = requestSaveAs()
            override fun onReload() = requestReload()
            override fun onClose() = requestClose()
        })
    }

    private fun showActionMenu(anchor: View) {
        CodeMenus.showActionMenu(requireContext(), anchor, session.options.value, object : CodeActionMenuListener {
            override fun onSearch() = openSearch()
            override fun onGoToLine() = promptGoToLine()
            override fun onStatistics() = showStatistics()
            override fun onShare() = shareText()
            override fun onSyntax() = chooseSyntax()
            override fun onEncoding() = chooseEncoding()
            override fun onAppearance() = showAppearance()
            override fun onWordwrapChanged(enabled: Boolean) = session.setWordwrap(enabled)
            override fun onReadOnlyChanged(readOnly: Boolean) = session.setReadOnly(readOnly)
        })
    }

    private fun openSearch() {
        searchBar?.show()
    }

    private fun closeSearch() {
        searchBar?.dismissSilently()
        session.stopSearch()
    }

    private fun requestNew() {
        confirmDiscardIfDirty {
            closeSearch()
            viewLifecycleOwner.lifecycleScope.launch { session.newDocument() }
        }
    }

    private fun requestClose() = requestNew()

    private fun requestOpen() {
        confirmDiscardIfDirty { ensureStorage { showPicker(CodePickerMode.OPEN) { openFile(it) } } }
    }

    private fun requestSaveAs(afterSaved: (() -> Unit)? = null) {
        ensureStorage {
            showPicker(CodePickerMode.SAVE_AS) { file ->
                viewLifecycleOwner.lifecycleScope.launch { if (saveAs(file)) afterSaved?.invoke() }
            }
        }
    }

    private fun requestReload() {
        val doc = session.document.value
        val file = doc.file ?: run {
            toast(getString(R.string.code_reload_untitled))
            return
        }
        val reload = {
            closeSearch()
            viewLifecycleOwner.lifecycleScope.launch { reportLoad(session.reload(), file) }
        }
        if (!doc.dirty) {
            reload()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.code_reload_title)
            .setMessage(getString(R.string.code_reload_message, file.name))
            .setPositiveButton(R.string.code_reload_confirm) { _, _ -> reload() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPicker(mode: CodePickerMode, onPicked: (File) -> Unit) {
        val context = context ?: return
        val doc = session.document.value
        when (val prepared = MolinaStorage.ensureDirectories()) {
            is MolinaStorage.EnsureResult.Ready -> {
                val startDir = doc.file?.parentFile?.takeIf { it.isDirectory } ?: prepared.root
                val suggested = doc.file?.name ?: getString(R.string.code_default_file_name)
                CodeFilePicker(context, startDir, mode, suggested, onPicked).show()
            }
            is MolinaStorage.EnsureResult.Failed ->
                toast(getString(R.string.code_picker_error, prepared.reason))
        }
    }

    private fun openFile(file: File) {
        closeSearch()
        viewLifecycleOwner.lifecycleScope.launch { reportLoad(session.open(file), file) }
    }

    private fun reportLoad(result: CodeFileLoad, file: File) {
        when (result) {
            is CodeFileLoad.Loaded -> Unit
            is CodeFileLoad.TooLarge -> toast(
                getString(R.string.code_file_too_large, formatSize(result.sizeBytes), formatSize(result.limitBytes))
            )
            CodeFileLoad.Binary -> toast(getString(R.string.code_file_binary))
            is CodeFileLoad.Failed -> toast(getString(R.string.code_open_failed, file.name, result.reason))
        }
    }

    private fun saveCurrent() {
        if (session.document.value.file == null) {
            requestSaveAs()
        } else {
            viewLifecycleOwner.lifecycleScope.launch { save() }
        }
    }

    private suspend fun save(): Boolean = report(session.save(), session.document.value.file)

    private suspend fun saveAs(file: File): Boolean = report(session.saveAs(file), file)

    private fun report(result: CodeFileSave, file: File?): Boolean = when (result) {
        CodeFileSave.Saved -> {
            toast(getString(R.string.code_save_ok, file?.name.orEmpty()))
            true
        }
        is CodeFileSave.Failed -> {
            toast(getString(R.string.code_save_failed, result.reason))
            false
        }
    }

    private fun confirmDiscardIfDirty(proceed: () -> Unit) {
        val doc = session.document.value
        if (!doc.dirty) {
            proceed()
            return
        }
        val name = doc.file?.name ?: getString(R.string.code_untitled)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.code_unsaved_title)
            .setMessage(getString(R.string.code_unsaved_message, name))
            .setPositiveButton(R.string.code_unsaved_save) { _, _ ->
                if (doc.file == null) {
                    requestSaveAs(afterSaved = proceed)
                } else {
                    viewLifecycleOwner.lifecycleScope.launch { if (save()) proceed() }
                }
            }
            .setNegativeButton(R.string.code_unsaved_discard) { _, _ -> proceed() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareText() {
        val subject = session.document.value.file?.name ?: getString(R.string.code_untitled)
        when (CodeShare.send(requireContext(), subject, session.currentText())) {
            CodeShare.Result.SENT -> Unit
            CodeShare.Result.TOO_LARGE -> toast(getString(R.string.code_share_too_large))
            CodeShare.Result.NO_APP -> toast(getString(R.string.code_share_failed))
        }
    }

    private fun chooseSyntax() {
        val names = session.syntaxNames()
        if (names.isEmpty()) {
            toast(getString(R.string.code_syntax_failed))
            return
        }
        val items = listOf(getString(R.string.code_syntax_plain)) + names
        CodeChoiceDialog.show(requireContext(), R.string.code_syntax_title, items, -1) { index ->
            val name = if (index == 0) null else names[index - 1]
            if (!session.setSyntax(name)) toast(getString(R.string.code_syntax_failed))
        }
    }

    private fun chooseEncoding() {
        val doc = session.document.value
        val options = CodeEncoding.values()
        val checked = options.indexOfFirst { it.charset == doc.charset && it.bom == doc.hasBom }
        CodeChoiceDialog.show(requireContext(), R.string.code_encoding_title, options.map { it.label }, checked) { index ->
            val choice = options[index]
            if (session.changeEncoding(choice.charset, choice.bom)) {
                toast(getString(R.string.code_encoding_changed, choice.label))
            } else {
                toast(getString(R.string.code_encoding_unsupported, choice.label))
            }
        }
    }

    private fun showAppearance() {
        val store = CodeAppearanceStore(requireContext())
        CodeAppearanceDialog.show(requireContext(), store.load()) { value ->
            store.save(value)
            session.applyAppearance(value)
        }
    }

    private fun promptGoToLine() {
        val total = session.lineCount()
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setHint(getString(R.string.code_goto_hint, total))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.code_menu_goto)
            .setView(input)
            .setPositiveButton(R.string.code_goto_confirm) { _, _ ->
                val line = input.text.toString().trim().toIntOrNull()
                if (line == null || !session.goToLine(line)) {
                    toast(getString(R.string.code_goto_invalid, total))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showStatistics() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = session.statistics()
            val doc = session.document.value
            val message = StringBuilder()
                .append(getString(R.string.code_stats_lines, stats.lines)).append('\n')
                .append(getString(R.string.code_stats_words, stats.words)).append('\n')
                .append(getString(R.string.code_stats_characters, stats.characters)).append('\n')
                .append(getString(R.string.code_stats_size, formatSize(stats.bytes))).append('\n')
                .append(getString(R.string.code_stats_encoding, doc.charset.name()))
            if (stats.selectedCharacters > 0) {
                message.append('\n').append(getString(R.string.code_stats_selected, stats.selectedCharacters))
            }
            if (!isAdded) return@launch
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.code_menu_stats)
                .setMessage(message.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
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
