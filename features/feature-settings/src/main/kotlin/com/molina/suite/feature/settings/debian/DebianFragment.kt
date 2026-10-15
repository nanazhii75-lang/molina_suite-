package com.molina.suite.feature.settings.debian

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.feature.settings.ExpandableCard
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SESSION_POLL_MS = 1500L

/** Layar Debian: status rootfs, install, update, dan panduan login/logout. */
class DebianFragment : Fragment(R.layout.fragment_debian), Refreshable {

    private class Row(val action: DebianAction, val root: View, val run: ImageButton)

    private var statusView: TextView? = null
    private var rows: List<Row> = emptyList()
    private var installed: Boolean? = null
    private var insideDebian = false
    private var runJob: Job? = null

    /** Judul (id string) kartu yang sedang terbuka; bertahan saat layar dibuat ulang dari back stack. */
    private val expandedTitles = HashSet<Int>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusView = view.findViewById(R.id.debian_status)
        view.findViewById<View>(R.id.debian_refresh).setOnClickListener { refresh() }

        val container = view.findViewById<LinearLayout>(R.id.debian_actions)
        val inflater = LayoutInflater.from(requireContext())
        rows = DebianActions.all.map { action ->
            val root = inflater.inflate(R.layout.item_debian_action, container, false)
            root.findViewById<TextView>(R.id.debian_item_title).setText(action.title)
            root.findViewById<TextView>(R.id.debian_item_desc).setText(action.description)
            root.findViewById<TextView>(R.id.debian_item_command).text = action.preview ?: action.command
            root.findViewById<MaterialButton>(R.id.debian_item_copy)
                .setOnClickListener { copy(action.command) }
            ExpandableCard(
                trigger = root.findViewById(R.id.debian_item_card),
                details = listOf(root.findViewById(R.id.debian_item_details)),
                chevron = root.findViewById(R.id.debian_item_chevron),
                expanded = action.title in expandedTitles,
                onChanged = { open ->
                    if (open) expandedTitles.add(action.title) else expandedTitles.remove(action.title)
                }
            )
            val run = root.findViewById<ImageButton>(R.id.debian_item_run)
            if (action.runnable) {
                run.setOnClickListener { runAction(action) }
            } else {
                run.visibility = View.GONE
            }
            container.addView(root)
            Row(action, root, run)
        }
        updateRows()
        refresh()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (isVisible) pollSession()
                    delay(SESSION_POLL_MS)
                }
            }
        }
    }

    override fun onDestroyView() {
        statusView = null
        rows = emptyList()
        super.onDestroyView()
    }

    override fun refresh() {
        statusView?.setText(R.string.debian_status_checking)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { DebianStatus.isInstalled(appContext) }
            insideDebian = withContext(Dispatchers.IO) { DebianSession.isInside(appContext) }
            installed = ok
            statusView?.setText(if (ok) R.string.debian_status_installed else R.string.debian_status_missing)
            updateRows()
        }
    }

    /** Memeriksa penanda sesi Debian dan memperbarui kartu hanya bila statusnya berubah. */
    private suspend fun pollSession() {
        val appContext = context?.applicationContext ?: return
        val inside = withContext(Dispatchers.IO) { DebianSession.isInside(appContext) }
        if (inside != insideDebian) {
            insideDebian = inside
            updateRows()
        }
    }

    private fun updateRows() {
        for (row in rows) {
            val enabled = when (row.action.availability) {
                Availability.ALWAYS -> true
                Availability.WHEN_MISSING -> installed == false
                Availability.WHEN_INSTALLED -> installed == true
                Availability.WHEN_INSTALLED_OUTSIDE -> installed == true && !insideDebian
                Availability.WHEN_IN_DEBIAN -> insideDebian
            }
            row.root.alpha = if (enabled) 1f else 0.5f
            row.run.isEnabled = enabled
        }
    }

    private fun runAction(action: DebianAction) {
        val message = action.confirmMessage
        val ctx = context
        if (message != null && ctx != null) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(action.title)
                .setMessage(message)
                .setNegativeButton(R.string.library_cancel, null)
                .setPositiveButton(R.string.debian_confirm_run) { _, _ -> execute(action) }
                .show()
            return
        }
        execute(action)
    }

    private fun execute(action: DebianAction) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        val label = getString(action.title)
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, label, action.command)
            if (reason != null) toast(getString(R.string.library_run_failed, reason))
        }
    }

    private fun copy(text: String) {
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast(getString(R.string.debian_copy_failed))
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("molina", text))
        toast(getString(R.string.debian_copied))
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }
}
