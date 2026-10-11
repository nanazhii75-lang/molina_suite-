package com.molina.suite.feature.settings.tools

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import com.molina.suite.feature.settings.packages.PackageCatalog
import com.molina.suite.feature.settings.packages.PackageDetection
import com.molina.suite.feature.settings.packages.PackageDetector
import com.molina.suite.feature.settings.packages.PackageSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pusat Kontrol: alat siap pakai berbentuk form. Saat ini kartu Quick Downloader.
 * Tombol mulai menyusun satu baris yt-dlp lalu mengetiknya ke tab Terminal, sama seperti
 * tombol jalankan di Library.
 */
class ToolsFragment : Fragment(R.layout.fragment_tools), Refreshable {

    private class Views(
        val urlLayout: TextInputLayout,
        val url: TextInputEditText,
        val format: MaterialButton,
        val engine: MaterialButton,
        val requires: TextView
    )

    private var views: Views? = null
    private var format = DownloadFormat.MP4_1080
    private var engine = DownloadEngine.YTDLP
    private var runJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            format = DownloadFormat.values().getOrElse(savedInstanceState.getInt(STATE_FORMAT, 0)) { format }
            engine = DownloadEngine.values().getOrElse(savedInstanceState.getInt(STATE_ENGINE, 0)) { engine }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FORMAT, format.ordinal)
        outState.putInt(STATE_ENGINE, engine.ordinal)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val v = Views(
            view.findViewById(R.id.tools_url_layout),
            view.findViewById(R.id.tools_url),
            view.findViewById(R.id.tools_format),
            view.findViewById(R.id.tools_engine),
            view.findViewById(R.id.tools_requires)
        )
        views = v
        view.findViewById<View>(R.id.tools_paste).setOnClickListener { pasteUrl() }
        view.findViewById<View>(R.id.tools_start).setOnClickListener { start() }
        v.format.setOnClickListener { pickFormat() }
        v.engine.setOnClickListener { pickEngine() }
        renderSelection()
        refreshRequirements()
    }

    override fun onDestroyView() {
        views = null
        super.onDestroyView()
    }

    override fun refresh() {
        if (view != null) refreshRequirements()
    }

    override fun onResume() {
        super.onResume()
        refreshRequirements()
    }

    private fun renderSelection() {
        val v = views ?: return
        v.format.text = getString(R.string.tools_format_label, getString(format.label))
        v.engine.text = getString(R.string.tools_engine_label, getString(engine.label))
    }

    private fun pasteUrl() {
        val v = views ?: return
        val ctx = context ?: return
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(ctx).toString().trim()
        } else {
            ""
        }
        if (text.isEmpty()) {
            toast(getString(R.string.tools_paste_empty))
            return
        }
        v.url.setText(text)
        v.url.setSelection(text.length)
        v.urlLayout.error = null
    }

    private fun pickFormat() {
        val items = DownloadFormat.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tools_format_title)
            .setSingleChoiceItems(Array(items.size) { getString(items[it].label) }, format.ordinal) { dialog, which ->
                format = items[which]
                dialog.dismiss()
                renderSelection()
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun pickEngine() {
        val items = DownloadEngine.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tools_engine_title)
            .setSingleChoiceItems(Array(items.size) { getString(items[it].label) }, engine.ordinal) { dialog, which ->
                engine = items[which]
                dialog.dismiss()
                renderSelection()
                refreshRequirements()
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun specsOf(ids: List<String>): List<PackageSpec> =
        ids.mapNotNull { id -> PackageCatalog.all.firstOrNull { it.id == id } }

    /** Menampilkan alat yang dibutuhkan beserta yang belum terpasang. */
    private fun refreshRequirements() {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        val required = specsOf(DownloaderPlan.requiredPackages(engine))
        viewLifecycleOwner.lifecycleScope.launch {
            val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            val v = views ?: return@launch
            val lines = ArrayList<String>()
            lines.add(getString(R.string.tools_requires, required.joinToString(", ") { getString(it.title) }))
            when (detection) {
                is PackageDetection.NoDebian -> lines.add(getString(R.string.tools_no_debian))
                is PackageDetection.Failed -> Unit
                is PackageDetection.Ok -> {
                    val gap = required.filter { detection.installed[it.id] == false }
                    if (gap.isNotEmpty()) {
                        lines.add(getString(R.string.tools_requires_missing, gap.joinToString(", ") { getString(it.title) }))
                    }
                }
            }
            v.requires.text = lines.joinToString("\n")
        }
    }

    private fun start() {
        val v = views ?: return
        val url = DownloaderPlan.normalizeUrl(v.url.text?.toString().orEmpty())
        v.urlLayout.error = if (url == null) getString(R.string.tools_url_invalid) else null
        if (url == null) return
        val appContext = context?.applicationContext ?: return
        val required = specsOf(DownloaderPlan.requiredPackages(engine))
        val command = DownloaderPlan.command(url, format, engine)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }) {
                is PackageDetection.NoDebian -> toast(getString(R.string.tools_no_debian))
                // Status tidak terbaca: jalankan saja, terminal akan menampilkan galat sebenarnya.
                is PackageDetection.Failed -> send(command)
                is PackageDetection.Ok -> {
                    val gap = required.filter { detection.installed[it.id] == false }
                    if (gap.isEmpty()) send(command) else askInstall(command, gap)
                }
            }
        }
    }

    private fun askInstall(command: String, missing: List<PackageSpec>) {
        val ctx = context ?: return
        val install = PackageCatalog.installAllCommand(missing) ?: return
        val names = missing.joinToString(", ") { getString(it.title) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.tools_downloader_title)
            .setMessage(getString(R.string.tools_missing_message, names))
            .setNegativeButton(R.string.tools_cancel, null)
            .setNeutralButton(R.string.tools_run_anyway) { _, _ -> send(command) }
            .setPositiveButton(R.string.tools_install_and_run) { _, _ -> send("$install && $command") }
            .show()
    }

    /** Mengetik [inner] di dalam Debian pada sesi aktif tab Terminal. */
    private fun send(inner: String) {
        if (runJob?.isActive == true) return
        val host = activity ?: return
        val line = ShellLine.wrapForDebian(inner)
        runJob = viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, line)
            if (reason != null) toast(getString(R.string.tools_run_failed, reason))
        }
    }

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val STATE_FORMAT = "tools.format"
        const val STATE_ENGINE = "tools.engine"
    }
}
