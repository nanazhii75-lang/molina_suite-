package com.molina.suite.feature.code

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.text.format.Formatter
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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.EditorOpenBoard
import com.molina.suite.core.common.EditorOpenRequest
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.HostChromeController
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.LibraryDraftBoard
import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ScriptRunTarget
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.core.storage.MolinaStorage
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tab Code: header, editor Sora, bar pencarian, dan toolbar. Hanya urusan tampilan;
 * dokumen dan logika berkas ada di [CodeEditorSession] dan [CodeFileRepository].
 */
class CodeTabFragment : Fragment(), HostBackPressHandler {

    private val session: CodeEditorSession
        get() = CodeEngineModule.requireEngine().session

    private var editor: CodeEditor? = null
    private var pathView: TextView? = null
    private var contentView: View? = null
    private var searchBar: CodeSearchBar? = null
    private var tabBar: CodeTabBar? = null
    private var actionBar: CodeActionBar? = null
    private var keyboardWatcher: CodeKeyboardWatcher? = null
    private var drawer: DrawerLayout? = null
    private var treeController: CodeTreeController? = null
    private var lastFollowedPath: String? = null
    private var afterPermission: (() -> Unit)? = null

    /** Alur Lakukan (cek kompilasi); logikanya di [CodeCheckController], tab hanya menyediakan [CodeCheckController.Host]. */
    private val checkController: CodeCheckController by lazy {
        val engine = CodeEngineModule.requireEngine()
        CodeCheckController(
            object : CodeCheckController.Host {
                override val context: Context? get() = this@CodeTabFragment.context
                override val scope: CoroutineScope get() = viewLifecycleOwner.lifecycleScope
                override fun currentFile(): File? = session.document.value.file
                override fun isDirty(): Boolean = session.document.value.dirty
                override suspend fun save(): Boolean = this@CodeTabFragment.save()
                override fun goToLine(line: Int): Boolean = session.goToLine(line)
                override fun openSetup(target: ToolSetupTarget, toolIds: List<String>) {
                    engine.toolSetup.request(target, toolIds)
                    (this@CodeTabFragment.activity as? HostTabSwitcher)?.showEngine(EngineId.SETTINGS)
                }
            },
            engine.toolReadiness,
            engine.checkRunner
        )
    }

    /** Alur Ekspor dokumen; logikanya di [CodeExportController], tab hanya menyediakan [CodeExportController.Host]. */
    private val exportController: CodeExportController by lazy {
        val engine = CodeEngineModule.requireEngine()
        CodeExportController(
            object : CodeExportController.Host {
                override val context: Context? get() = this@CodeTabFragment.context
                override val scope: CoroutineScope get() = viewLifecycleOwner.lifecycleScope
                override fun currentFile(): File? = session.document.value.file
                override fun isDirty(): Boolean = session.document.value.dirty
                override suspend fun save(): Boolean = this@CodeTabFragment.save()
                override fun openSetup(target: ToolSetupTarget, toolIds: List<String>) {
                    engine.toolSetup.request(target, toolIds)
                    (this@CodeTabFragment.activity as? HostTabSwitcher)?.showEngine(EngineId.SETTINGS)
                }
            },
            engine.documents
        )
    }

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

        val path = TextView(context).apply {
            setTextColor(CodePalette.TEXT_MUTED)
            textSize = 11f
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
        }
        val moreButton = CodeUi.iconButton(context, R.drawable.ic_code_more, R.string.code_header_more) { }
        moreButton.setOnClickListener { showActionMenu(it) }

        val headerView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(CodePalette.PANEL)
            setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 2), 0)
            addView(path, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(moreButton, LinearLayout.LayoutParams(CodeUi.dp(context, 36), CodeUi.dp(context, 28)))
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
            override fun onClosed() = session.closeSearchPanel()
        })
        val actions = buildActionBar(context)
        actionBar = actions
        val tabs = CodeTabBar(context, object : CodeTabBar.Callbacks {
            override fun onSelect(id: Long) = selectTab(id)
            override fun onClose(id: Long) = requestCloseTab(id)
        })
        tabBar = tabs

        pathView = path
        searchBar = bar

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CodePalette.EDITOR_BACKGROUND)
            addView(headerView, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(tabs.view, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(editorView, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(bar.view, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(actions.view, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        contentView = content

        val tree = CodeTreeController(
            context,
            CodeEngineModule.requireEngine().workspace,
            object : CodeTreeController.Host {
                override fun onOpenFile(file: File) {
                    closeTree()
                    openFile(file)
                }

                override fun onChooseFolder() {
                    closeTree()
                    requestOpenFolder()
                }

                override fun onChooseFile() {
                    closeTree()
                    requestOpenFile()
                }

                override fun onRunFile(file: File, target: ScriptRunTarget) {
                    closeTree()
                    runFile(file, target)
                }
            }
        )
        treeController = tree
        val drawerWidth = minOf(
            CodeUi.dp(context, DRAWER_WIDTH_DP),
            (context.resources.displayMetrics.widthPixels * DRAWER_MAX_FRACTION).toInt()
        )
        return CodeSwipeDrawerLayout(context) { editor }.apply {
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            setScrimColor(DRAWER_SCRIM)
            addView(content, DrawerLayout.LayoutParams(MATCH, MATCH))
            addView(tree.view, DrawerLayout.LayoutParams(drawerWidth, MATCH, Gravity.START))
            addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    hideIme()
                    tree.reload()
                }
            })
            drawer = this
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val workspace = CodeEngineModule.requireEngine().workspace
        workspace.restoreIfPossible()
        treeController?.attach(viewLifecycleOwner.lifecycleScope)
        keyboardWatcher = CodeKeyboardWatcher(
            window = requireActivity().window,
            anchor = view,
            onOverlapChanged = { px -> contentView?.setPadding(0, 0, 0, px) }
        ) { visible ->
            (activity as? HostChromeController)?.setChromeCompact(visible)
        }
        renderDocument(session.document.value)
        renderActions(session.document.value, session.hasContent.value)
        tabBar?.render(session.tabBar.value)
        val target = requireNotNull(editor)
        viewLifecycleOwner.lifecycleScope.launch { session.attach(target) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { session.document.collect { renderDocument(it) } }
                launch {
                    combine(session.document, session.hasContent) { doc, content -> doc to content }
                        .collect { (doc, content) -> renderActions(doc, content) }
                }
                launch { session.search.collect { searchBar?.render(it) } }
                launch { session.tabBar.collect { tabBar?.render(it) } }
                launch { workspace.tree.collect { treeController?.show(it) } }
                launch {
                    val board = CodeEngineModule.requireEngine().editorOpen
                    board.pending.collect { request ->
                        if (request != null) handleOpenRequest(board, request)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CodeEngineModule.requireEngine().workspace.restoreIfPossible()
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
        pathView = null
        contentView = null
        searchBar = null
        tabBar = null
        actionBar = null
        drawer = null
        treeController = null
        lastFollowedPath = null
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
        pathView?.text = doc.file?.parent ?: MolinaStorage.sharedRoot().path
        treeController?.setActiveFile(doc.file)
        followActiveFile(doc.file)
    }

    /** Arahkan pohon folder ke lokasi berkas aktif; hanya saat berkas aktif berganti, bukan tiap perubahan dokumen. */
    private fun followActiveFile(file: File?) {
        val path = file?.path
        if (path == lastFollowedPath) return
        val controller = treeController ?: return
        lastFollowedPath = path
        if (file == null) return
        when (CodeEngineModule.requireEngine().workspace.follow(file)) {
            CodeFollow.IGNORED -> Unit
            CodeFollow.REVEALED -> {
                controller.requestReveal()
                controller.reload()
            }
            CodeFollow.REROOTED -> controller.requestReveal()
        }
    }

    private fun notifyReplaceUnavailable() {
        toast(
            getString(
                if (session.options.value.readOnly) R.string.code_search_readonly else R.string.code_search_no_match
            )
        )
    }

    /**
     * Ikon yang butuh isi (Simpan, Cari, kursor, Salin semua) abu-abu di lembar kosong. Jalankan
     * hanya aktif untuk berkas bernama dengan ekstensi yang bisa dijalankan atau ditinjau; lembar
     * tanpa nama abu-abu karena memberi nama adalah tugas Simpan.
     */
    private fun renderActions(doc: CodeDocument, hasContent: Boolean) {
        val bar = actionBar ?: return
        val file = doc.file
        bar.setEnabled(R.drawable.ic_code_save, hasContent || file != null)
        bar.setEnabled(R.drawable.ic_code_search, hasContent)
        bar.setEnabled(R.drawable.ic_code_arrow_left, hasContent)
        bar.setEnabled(R.drawable.ic_code_arrow_right, hasContent)
        bar.setEnabled(R.drawable.ic_code_copy_all, hasContent)
        val canRun = file != null &&
            (CodePreviewKind.of(file.name) != null || ScriptRunLine.isRunnableName(file.name))
        bar.setEnabled(R.drawable.ic_code_run, canRun)
    }

    private fun buildActionBar(context: Context): CodeActionBar = CodeActionBar.build(
        context,
        listOf(
            CodeAction(R.drawable.ic_code_save, R.string.code_key_save, CodePalette.TOOL_SAVE) { showSaveChoice() },
            CodeAction(R.drawable.ic_code_paste, R.string.code_key_paste, CodePalette.TOOL_PASTE) { session.paste() },
            CodeAction(R.drawable.ic_code_undo, R.string.code_key_undo, CodePalette.TOOL_UNDO) { session.undo() },
            CodeAction(R.drawable.ic_code_redo, R.string.code_key_redo, CodePalette.TOOL_REDO) { session.redo() },
            CodeAction(R.drawable.ic_code_search, R.string.code_menu_search, CodePalette.TOOL_SEARCH) { openSearch() },
            CodeAction(R.drawable.ic_code_arrow_left, R.string.code_key_left, CodePalette.TOOL_CURSOR) { session.moveCursorLeft() },
            CodeAction(R.drawable.ic_code_arrow_right, R.string.code_key_right, CodePalette.TOOL_CURSOR) { session.moveCursorRight() },
            CodeAction(R.drawable.ic_code_copy_all, R.string.code_key_copy_all, CodePalette.TOOL_COPY_ALL) { copyAll() },
            CodeAction(R.drawable.ic_code_run, R.string.code_key_run, CodePalette.TOOL_RUN) { runCurrent() },
            CodeAction(R.drawable.ic_code_close, R.string.code_key_close, CodePalette.TOOL_CLOSE) { requestClose() }
        )
    )

    private fun showActionMenu(anchor: View) {
        val menuListener = object : CodeActionMenuListener {
            override fun onNew() = requestNew()
            override fun onOpen() = requestOpen()
            override fun onReload() = requestReload()
            override fun onSearch() = openSearch()
            override fun onGoToLine() = promptGoToLine()
            override fun onStatistics() = showStatistics()
            override fun onCheck() = checkController.start()
            override fun onShare() = shareText()
            override fun onSendToLibrary() = sendToLibrary()
            override fun onExport() = ensureStorage { exportController.start() }
            override fun onSyntax() = chooseSyntax()
            override fun onEncoding() = showEncodingInfo()
            override fun onAppearance() = showAppearance()
            override fun onWordwrapChanged(enabled: Boolean) = session.setWordwrap(enabled)
            override fun onReadOnlyChanged(readOnly: Boolean) = session.setReadOnly(readOnly)
        }
        CodeMenus.showActionMenu(
            requireContext(),
            anchor,
            session.document.value,
            session.options.value,
            session.hasContent.value,
            exportController.isAvailableFor(session.document.value.file),
            menuListener
        )
    }

    private fun openSearch() {
        searchBar?.show()
    }

    private fun closeSearch() {
        searchBar?.dismissSilently()
        session.stopSearch()
    }

    private fun requestNew() {
        closeSearch()
        viewLifecycleOwner.lifecycleScope.launch {
            if (!session.newDocument()) toast(session.tabLimitMessage())
        }
    }

    private fun requestClose() = requestCloseTab(session.tabBar.value.activeId)

    private fun selectTab(id: Long) {
        closeSearch()
        viewLifecycleOwner.lifecycleScope.launch { session.activateTab(id) }
    }

    private fun requestCloseTab(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val item = session.tabBar.value.items.firstOrNull { it.id == id } ?: return@launch
            if (!item.dirty) {
                closeTab(id)
                return@launch
            }
            if (session.tabBar.value.activeId != id) {
                closeSearch()
                session.activateTab(id)
            }
            confirmDiscardIfDirty { closeTab(id) }
        }
    }

    private fun closeTab(id: Long) {
        if (session.tabBar.value.activeId == id) closeSearch()
        viewLifecycleOwner.lifecycleScope.launch { session.closeTab(id) }
    }

    /** Buka: pilih berkas tunggal atau folder yang ditampilkan sebagai pohon direktori. */
    private fun requestOpen() {
        val choices = listOf(
            getString(R.string.code_open_choice_file),
            getString(R.string.code_open_choice_folder)
        )
        CodeChoiceDialog.show(requireContext(), R.string.code_open_choose_title, choices, -1) { index ->
            if (index == 0) requestOpenFile() else requestOpenFolder()
        }
    }

    private fun requestOpenFile() {
        ensureStorage { showPicker(CodePickerMode.OPEN) { openFile(it) } }
    }

    private fun requestOpenFolder() {
        ensureStorage {
            showPicker(CodePickerMode.OPEN_FOLDER) { folder ->
                CodeEngineModule.requireEngine().workspace.open(folder)
                drawer?.openDrawer(Gravity.START)
            }
        }
    }

    private fun closeTree() {
        drawer?.closeDrawer(Gravity.START)
    }

    /** Back menutup panel pohon lebih dulu; bila tertutup, perilaku default shell berjalan. */
    override fun onHostBackPressed(): Boolean {
        val layout = drawer ?: return false
        if (!layout.isDrawerOpen(Gravity.START)) return false
        layout.closeDrawer(Gravity.START)
        return true
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

    /** Permintaan modul lain (mis. Library) membuka berkas; papan menyimpannya sampai tab ini siap. */
    private suspend fun handleOpenRequest(board: EditorOpenBoard, request: EditorOpenRequest) {
        closeSearch()
        val file = File(request.path)
        when (val outcome = session.openRequested(file)) {
            is CodeRequestedOpen.Loaded -> reportLoad(outcome.load, file)
            CodeRequestedOpen.KeptUnsaved -> toast(getString(R.string.code_open_kept_unsaved, file.name))
        }
        board.consume(request)
    }

    private fun openFile(file: File) {
        closeSearch()
        viewLifecycleOwner.lifecycleScope.launch { reportLoad(session.open(file), file) }
    }

    private fun reportLoad(result: CodeFileLoad?, file: File) {
        when (result) {
            null -> Unit
            is CodeFileLoad.Loaded -> Unit
            is CodeFileLoad.TooLarge -> toast(
                getString(R.string.code_file_too_large, formatSize(result.sizeBytes), formatSize(result.limitBytes))
            )
            CodeFileLoad.Binary -> toast(getString(R.string.code_file_binary))
            is CodeFileLoad.Failed -> toast(getString(R.string.code_open_failed, file.name, result.reason))
        }
    }

    /**
     * Jalankan: berkas md, html, dan svg dibuka di Tinjauan (isi editor, tanpa menyimpan); selain
     * itu simpan dulu bila perlu, lalu jalankan di Debian lewat tab Terminal.
     */
    private fun runCurrent() {
        val doc = session.document.value
        val file = doc.file ?: return
        when {
            CodePreviewKind.of(file.name) != null -> openPreview(file)
            doc.dirty -> viewLifecycleOwner.lifecycleScope.launch {
                if (save()) runFile(file, ScriptRunTarget.DEBIAN)
            }
            else -> runFile(file, ScriptRunTarget.DEBIAN)
        }
    }

    /**
     * Mengirim isi editor ke Library sebagai entri baru: isi dan nama berkas (tanpa ekstensi)
     * ditaruh di papan Library, lalu tab Settings dibuka. Yang menyimpan adalah dialog "Entri
     * baru" di Library; di sini tidak ada yang ditulis ke Library, dan berkas di disk tidak berubah.
     */
    private fun sendToLibrary() {
        val host = activity as? HostTabSwitcher
        if (host == null) {
            toast(getString(R.string.code_library_no_switcher))
            return
        }
        val text = session.currentText()
        if (text.isBlank()) return
        if (text.length > LibraryDraftBoard.MAX_CONTENT_CHARS) {
            toast(getString(R.string.code_library_too_big))
            return
        }
        val fileName = session.document.value.file?.name
        val name = if (fileName == null) "" else fileName.substringBeforeLast('.', "").ifEmpty { fileName }
        val content = ScriptRunLine.withLibraryShebang(fileName, text)
        CodeEngineModule.requireEngine().libraryDraft.request(name, content)
        host.showEngine(EngineId.SETTINGS)
    }

    private fun openPreview(file: File) {
        val kind = CodePreviewKind.of(file.name) ?: return
        val context = requireContext()
        val text = session.currentText()
        viewLifecycleOwner.lifecycleScope.launch {
            val intent = withContext(Dispatchers.IO) { CodePreviewLauncher.prepare(context, kind, file, text) }
            if (intent == null) toast(getString(R.string.code_preview_cache_failed)) else startActivity(intent)
        }
    }

    private fun runFile(file: File, target: ScriptRunTarget) {
        viewLifecycleOwner.lifecycleScope.launch {
            val head = withContext(Dispatchers.IO) { CodeScriptRunner.readHead(file) }
            if (head == null) {
                toast(getString(R.string.code_run_unreadable, file.name))
                return@launch
            }
            val outcome = Activities.log.track(ActivitySource.CODE, "Jalankan ${file.name}") { entry ->
                CodeScriptRunner.run(
                    CodeEngineModule.requireEngine().terminalInput,
                    activity as? HostTabSwitcher,
                    file,
                    head,
                    target
                ).also { entry.report(it) }
            }
            when (outcome) {
                CodeRunOutcome.Started -> Unit
                CodeRunOutcome.Unsupported -> toast(getString(R.string.code_run_unsupported))
                CodeRunOutcome.NoSwitcher -> toast(getString(R.string.code_run_no_switcher))
                is CodeRunOutcome.Rejected -> toast(getString(R.string.code_run_failed, outcome.reason))
            }
        }
    }

    private fun copyAll() {
        val message = when (session.copyAll()) {
            CodeEditorSession.CopyAllResult.COPIED -> R.string.code_copy_all_done
            CodeEditorSession.CopyAllResult.EMPTY -> R.string.code_copy_all_empty
            CodeEditorSession.CopyAllResult.TOO_LARGE -> R.string.code_copy_all_too_large
            CodeEditorSession.CopyAllResult.NO_EDITOR -> R.string.code_copy_all_empty
        }
        toast(getString(message))
    }

    /** Ikon Simpan di toolbar: pilih Simpan atau Simpan sebagai. */
    private fun showSaveChoice() {
        val choices = listOf(
            getString(R.string.code_menu_save),
            getString(R.string.code_menu_save_as)
        )
        CodeChoiceDialog.show(requireContext(), R.string.code_key_save, choices, -1) { index ->
            if (index == 0) saveCurrent() else requestSaveAs()
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

    /** Enkode dikunci ke UTF-8, jadi dialog ini hanya memberi tahu dan tidak punya pilihan. */
    private fun showEncodingInfo() {
        val message = StringBuilder(getString(R.string.code_encoding_locked_message))
        if (session.document.value.hasBom) {
            message.append("\n\n").append(getString(R.string.code_encoding_bom_note))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.code_encoding_title)
            .setMessage(message.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        const val DRAWER_WIDTH_DP = 260
        const val DRAWER_MAX_FRACTION = 0.75f
        const val DRAWER_SCRIM = 0x66000000
    }
}
