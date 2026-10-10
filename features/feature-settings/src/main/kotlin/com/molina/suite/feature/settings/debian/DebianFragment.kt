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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar Debian: status rootfs, install, update, dan panduan login/logout. */
class DebianFragment : Fragment(R.layout.fragment_debian) {

    private class Row(val action: DebianAction, val root: View, val run: ImageButton)

    private var statusView: TextView? = null
    private var rows: List<Row> = emptyList()
    private var installed: Boolean? = null
    private var runJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusView = view.findViewById(R.id.debian_status)
        view.findViewById<View>(R.id.debian_refresh).setOnClickListener { refresh() }

        val container = view.findViewById<LinearLayout>(R.id.debian_actions)
        val inflater = LayoutInflater.from(requireContext())
        rows = DebianActions.all.map { action ->
            val root = inflater.inflate(R.layout.item_debian_action, container, false)
            root.findViewById<TextView>(R.id.debian_item_title).setText(action.title)
            root.findViewById<TextView>(R.id.debian_item_desc).setText(action.description)
            root.findViewById<TextView>(R.id.debian_item_command).text = action.command
            root.findViewById<MaterialButton>(R.id.debian_item_copy)
                .setOnClickListener { copy(action.command) }
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
    }

    override fun onDestroyView() {
        statusView = null
        rows = emptyList()
        super.onDestroyView()
    }

    private fun refresh() {
        statusView?.setText(R.string.debian_status_checking)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { DebianStatus.isInstalled(appContext) }
            installed = ok
            statusView?.setText(if (ok) R.string.debian_status_installed else R.string.debian_status_missing)
            updateRows()
        }
    }

    private fun updateRows() {
        for (row in rows) {
            val enabled = when (row.action.availability) {
                Availability.ALWAYS -> true
                Availability.WHEN_MISSING -> installed == false
                Availability.WHEN_INSTALLED -> installed == true
            }
            row.root.alpha = if (enabled) 1f else 0.5f
            row.run.isEnabled = enabled
        }
    }

    private fun runAction(action: DebianAction) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, action.command)
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
