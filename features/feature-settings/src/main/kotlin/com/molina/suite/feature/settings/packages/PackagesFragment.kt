package com.molina.suite.feature.settings.packages

import android.graphics.Rect
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
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar Paket: status paket Debian, install, update, dan install semua yang belum terpasang. */
class PackagesFragment : Fragment(R.layout.fragment_packages), Refreshable {

    private class Row(
        val spec: PackageSpec,
        val root: View,
        val status: TextView,
        val install: ImageButton,
        val update: ImageButton
    )

    private var statusView: TextView? = null
    private var installAllButton: MaterialButton? = null
    private var rows: List<Row> = emptyList()
    private var detection: PackageDetection? = null
    private var runJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        statusView = view.findViewById(R.id.packages_status)
        val installAll = view.findViewById<MaterialButton>(R.id.packages_install_all)
        installAll.setOnClickListener { installAllMissing() }
        installAllButton = installAll
        view.findViewById<View>(R.id.packages_refresh).setOnClickListener { refresh() }

        val container = view.findViewById<LinearLayout>(R.id.packages_list)
        val inflater = LayoutInflater.from(requireContext())
        rows = PackageCatalog.all.map { spec ->
            val root = inflater.inflate(R.layout.item_package, container, false)
            root.findViewById<TextView>(R.id.package_item_title).setText(spec.title)
            root.findViewById<TextView>(R.id.package_item_desc).setText(spec.description)
            val install = root.findViewById<ImageButton>(R.id.package_item_install)
            val update = root.findViewById<ImageButton>(R.id.package_item_update)
            install.setOnClickListener { runInTerminal(spec.installCommand()) }
            update.setOnClickListener { runInTerminal(spec.updateCommand()) }
            container.addView(root)
            Row(spec, root, root.findViewById(R.id.package_item_status), install, update)
        }
        highlightRequested(announce = savedInstanceState == null)
        refresh()
    }

    /**
     * Menyorot paket yang diminta fitur lain lewat argumen dan menggulir ke yang pertama. Petunjuk
     * singkat hanya tampil saat layar dibuka, bukan saat dibuat ulang karena rotasi.
     */
    private fun highlightRequested(announce: Boolean) {
        val ids = arguments?.getStringArray(ARG_HIGHLIGHT)?.toSet().orEmpty()
        val marked = rows.filter { it.spec.id in ids }
        if (marked.isEmpty()) return
        for (row in marked) {
            row.root.findViewById<View>(R.id.package_item_card)
                .setBackgroundResource(R.drawable.ms_bg_card_highlight)
        }
        val first = marked.first().root
        first.post { first.requestRectangleOnScreen(Rect(0, 0, first.width, first.height), false) }
        if (announce) toast(getString(R.string.packages_highlight_hint))
    }

    override fun onDestroyView() {
        statusView = null
        installAllButton = null
        rows = emptyList()
        super.onDestroyView()
    }

    override fun refresh() {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        detection = null
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            detection = result
            render()
        }
    }

    private fun render() {
        val current = detection
        val ok = current as? PackageDetection.Ok
        statusView?.text = when (current) {
            null -> getString(R.string.debian_status_checking)
            PackageDetection.NoDebian -> getString(R.string.packages_no_debian)
            is PackageDetection.Failed -> getString(R.string.packages_failed, current.reason)
            is PackageDetection.Ok ->
                getString(R.string.ms_packages_count, current.installed.count { it.value }, current.installed.size)
        }
        for (row in rows) {
            val installed = ok?.installed?.get(row.spec.id)
            row.status.setText(
                when (installed) {
                    true -> R.string.packages_installed
                    false -> R.string.packages_missing
                    null -> R.string.packages_unknown
                }
            )
            applyChip(row.status, installed)
            setEnabled(row.install, installed == false)
            setEnabled(row.update, installed == true)
        }
        val hasMissing = ok?.installed?.any { !it.value } == true
        installAllButton?.isEnabled = hasMissing
    }

    /** Chip status: hijau lembut bila terpasang, abu-abu netral bila belum ada atau belum diketahui. */
    private fun applyChip(chip: TextView, installed: Boolean?) {
        val on = installed == true
        chip.setBackgroundResource(if (on) R.drawable.ms_bg_chip_on else R.drawable.ms_bg_chip_off)
        chip.setTextColor(chip.context.getColor(if (on) R.color.ms_status_on else R.color.ms_status_off))
    }

    private fun setEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        // Peredupan ikon ditangani selector tint ms_icon_tint, bukan alpha, agar tidak ganda.
        button.alpha = 1f
    }

    private fun installAllMissing() {
        val ok = detection as? PackageDetection.Ok ?: return
        val missing = PackageCatalog.all.filter { ok.installed[it.id] == false }
        val command = PackageCatalog.installAllCommand(missing) ?: return
        runInTerminal(command)
    }

    private fun runInTerminal(command: String) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        val line = ShellLine.wrapForDebian(command)
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, line)
            if (reason != null) toast(getString(R.string.library_run_failed, reason))
        }
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ARG_HIGHLIGHT = "highlight_ids"

        /** Membuka layar Paket dengan paket [highlightIds] disorot (id katalog). */
        fun newInstance(highlightIds: List<String>): PackagesFragment = PackagesFragment().apply {
            arguments = Bundle().apply { putStringArray(ARG_HIGHLIGHT, highlightIds.toTypedArray()) }
        }
    }
}
