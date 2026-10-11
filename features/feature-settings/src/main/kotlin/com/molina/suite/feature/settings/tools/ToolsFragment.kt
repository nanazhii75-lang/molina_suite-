package com.molina.suite.feature.settings.tools

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.packages.PackageCatalog
import com.molina.suite.feature.settings.packages.PackageDetection
import com.molina.suite.feature.settings.packages.PackageDetector
import com.molina.suite.feature.settings.packages.PackageSpec
import com.molina.suite.feature.settings.library.LibraryResult
import com.molina.suite.feature.settings.run.NetworkRun
import com.molina.suite.feature.settings.run.RunDialogFragment
import com.molina.suite.feature.settings.run.RunKind
import com.molina.suite.feature.settings.run.RunSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pusat Kontrol: alat siap pakai berbentuk form (Quick Downloader, Konversi MP3, Info video).
 * Setiap tombol menyusun satu baris perintah lalu mengetiknya ke tab Terminal lewat
 * [ToolLauncher], sama seperti tombol jalankan di Library.
 */
class ToolsFragment : Fragment(R.layout.fragment_tools), Refreshable {

    private class Views(
        val urlLayout: TextInputLayout,
        val url: TextInputEditText,
        val format: MaterialButton,
        val engine: MaterialButton,
        val requires: TextView,
        val convertPick: MaterialButton,
        val convertFormat: MaterialButton,
        val probePick: MaterialButton
    )

    private var views: Views? = null
    private val launcher = ToolLauncher(this)
    private val picker = MediaFilePicker(this)
    private var format = DownloadFormat.MP4_1080
    private var engine = DownloadEngine.YTDLP
    private var convertSource: MediaFile? = null
    private var convertTarget = ConvertTarget.MP3
    private var probeSource: MediaFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            format = DownloadFormat.values().getOrElse(savedInstanceState.getInt(STATE_FORMAT, 0)) { format }
            engine = DownloadEngine.values().getOrElse(savedInstanceState.getInt(STATE_ENGINE, 0)) { engine }
            convertTarget = ConvertTarget.values().getOrElse(savedInstanceState.getInt(STATE_CONVERT, 0)) { convertTarget }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_FORMAT, format.ordinal)
        outState.putInt(STATE_ENGINE, engine.ordinal)
        outState.putInt(STATE_CONVERT, convertTarget.ordinal)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val v = Views(
            view.findViewById(R.id.tools_url_layout),
            view.findViewById(R.id.tools_url),
            view.findViewById(R.id.tools_format),
            view.findViewById(R.id.tools_engine),
            view.findViewById(R.id.tools_requires),
            view.findViewById(R.id.tools_convert_pick),
            view.findViewById(R.id.tools_convert_format),
            view.findViewById(R.id.tools_probe_pick)
        )
        views = v
        view.findViewById<View>(R.id.tools_paste).setOnClickListener { pasteUrl() }
        view.findViewById<View>(R.id.tools_start).setOnClickListener { startDownload() }
        v.format.setOnClickListener { pickFormat() }
        v.engine.setOnClickListener { pickEngine() }
        v.convertPick.setOnClickListener { picker.pick { convertSource = it; renderSelection() } }
        v.convertFormat.setOnClickListener { pickConvertTarget() }
        v.probePick.setOnClickListener { picker.pick { probeSource = it; renderSelection() } }
        view.findViewById<View>(R.id.tools_convert_start).setOnClickListener { startConvert() }
        view.findViewById<View>(R.id.tools_probe_start).setOnClickListener { startProbe() }
        view.findViewById<TextView>(R.id.tools_network_desc).text =
            getString(R.string.tools_network_desc, NetworkRun.MAX_DATA_MB)
        view.findViewById<View>(R.id.tools_network_start).setOnClickListener { startNetworkCheck() }
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
        v.convertPick.text = pickLabel(convertSource)
        v.convertFormat.text = getString(R.string.tools_convert_target_label, getString(convertTarget.label))
        v.probePick.text = pickLabel(probeSource)
    }

    private fun pickLabel(file: MediaFile?): String =
        if (file == null) getString(R.string.tools_pick_label)
        else getString(R.string.tools_pick_selected, file.relativePath)

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
            toolsToast(getString(R.string.tools_paste_empty))
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

    /** Menampilkan alat yang dibutuhkan Quick Downloader beserta yang belum terpasang. */
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

    private fun startDownload() {
        val v = views ?: return
        val url = DownloaderPlan.normalizeUrl(v.url.text?.toString().orEmpty())
        v.urlLayout.error = if (url == null) getString(R.string.tools_url_invalid) else null
        if (url == null) return
        launcher.launch(
            DownloaderPlan.requiredPackages(engine),
            DownloaderPlan.command(url, format, engine),
            R.string.tools_downloader_title
        )
    }

    private fun pickConvertTarget() {
        val items = ConvertTarget.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tools_convert_target_title)
            .setSingleChoiceItems(Array(items.size) { getString(items[it].label) }, convertTarget.ordinal) { dialog, which ->
                convertTarget = items[which]
                dialog.dismiss()
                renderSelection()
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun startConvert() {
        val source = convertSource
        if (source == null) {
            toolsToast(getString(R.string.tools_pick_first))
            return
        }
        val target = convertTarget
        if (!target.isVideo) {
            runConvert(source, target, ConvertMode.REENCODE)
            return
        }
        val modes = ConvertMode.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tools_mode_title)
            .setItems(Array(modes.size) { getString(modes[it].label) }) { _, which ->
                runConvert(source, target, modes[which])
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun runConvert(source: MediaFile, target: ConvertTarget, mode: ConvertMode) {
        if (view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            val output = withContext(Dispatchers.IO) { MediaFiles.prepareOutput(source.baseName, target.extension) }
            if (output == null) {
                toolsToast(getString(R.string.tools_output_failed))
                return@launch
            }
            startRun(RunKind.CONVERT, listOf(source.terminalPath, output, ConvertPlan.ffmpegArgs(target, mode)))
        }
    }

    private fun startProbe() {
        val source = probeSource
        if (source == null) {
            toolsToast(getString(R.string.tools_pick_first))
            return
        }
        startRun(RunKind.PROBE, listOf(source.terminalPath))
    }

    private fun startNetworkCheck() {
        startRun(RunKind.NETWORK)
    }

    /**
     * Menjalankan skrip [kind] di Terminal dan menampilkan layar progresnya. Skrip memasang
     * alat yang kurang sendiri, jadi tidak ada pemeriksaan paket di sini.
     */
    private fun startRun(kind: RunKind, args: List<String> = emptyList()) {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val prepared = withContext(Dispatchers.IO) { RunSessions.prepare(appContext, kind, args) }) {
                is LibraryResult.Failure -> toolsToast(getString(R.string.tools_run_failed, prepared.reason))
                is LibraryResult.Ok -> {
                    val session = prepared.value
                    var screen: RunDialogFragment? = null
                    launcher.launch(
                        emptyList(),
                        session.command,
                        kind.title,
                        onStart = { screen = RunDialogFragment.show(childFragmentManager, session) },
                        onFailed = { screen?.dismissAllowingStateLoss() }
                    )
                }
            }
        }
    }

    private companion object {
        const val STATE_FORMAT = "tools.format"
        const val STATE_ENGINE = "tools.engine"
        const val STATE_CONVERT = "tools.convert"
    }
}
