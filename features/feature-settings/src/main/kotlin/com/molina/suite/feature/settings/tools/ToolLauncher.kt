package com.molina.suite.feature.settings.tools

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.feature.settings.R
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

internal fun Fragment.toolsToast(message: String) {
    val ctx = context ?: return
    Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
}

/**
 * Menjalankan satu perintah alat di dalam Debian lewat tab Terminal: memeriksa paket yang
 * dibutuhkan, menawarkan install bila kurang, lalu mengetik perintah ke sesi aktif.
 * Panggil [launch] hanya saat view fragment hidup.
 */
internal class ToolLauncher(private val fragment: Fragment) {

    private var runJob: Job? = null

    fun launch(requiredIds: List<String>, command: String, @StringRes title: Int) {
        val appContext = fragment.context?.applicationContext ?: return
        val required = requiredIds.mapNotNull { id -> PackageCatalog.all.firstOrNull { it.id == id } }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }) {
                is PackageDetection.NoDebian -> fragment.toolsToast(fragment.getString(R.string.tools_no_debian))
                // Status tidak terbaca: jalankan saja, terminal akan menampilkan galat sebenarnya.
                is PackageDetection.Failed -> send(command)
                is PackageDetection.Ok -> {
                    val gap = required.filter { detection.installed[it.id] == false }
                    if (gap.isEmpty()) send(command) else askInstall(command, gap, title)
                }
            }
        }
    }

    private fun askInstall(command: String, missing: List<PackageSpec>, @StringRes title: Int) {
        val ctx = fragment.context ?: return
        val install = PackageCatalog.installAllCommand(missing) ?: return
        val names = missing.joinToString(", ") { fragment.getString(it.title) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(fragment.getString(R.string.tools_missing_message, names))
            .setNegativeButton(R.string.tools_cancel, null)
            .setNeutralButton(R.string.tools_run_anyway) { _, _ -> send(command) }
            .setPositiveButton(R.string.tools_install_and_run) { _, _ -> send("$install && $command") }
            .show()
    }

    /** Mengetik [inner] di dalam Debian pada sesi aktif tab Terminal. */
    private fun send(inner: String) {
        if (runJob?.isActive == true) return
        val host = fragment.activity ?: return
        val line = ShellLine.wrapForDebian(inner)
        runJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, line)
            if (reason != null) fragment.toolsToast(fragment.getString(R.string.tools_run_failed, reason))
        }
    }
}
