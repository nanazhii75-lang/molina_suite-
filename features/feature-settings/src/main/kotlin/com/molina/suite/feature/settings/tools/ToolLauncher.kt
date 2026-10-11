package com.molina.suite.feature.settings.tools

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.common.ToolReadiness
import com.molina.suite.core.common.ToolRequirement
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.SettingsDependencies
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.TerminalCommandRunner
import com.molina.suite.feature.settings.packages.PackageCatalog
import com.molina.suite.feature.settings.packages.PackageToolReadinessChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun Fragment.toolsToast(message: String) {
    val ctx = context ?: return
    Toast.makeText(ctx.applicationContext, message, Toast.LENGTH_LONG).show()
}

/**
 * Menjalankan satu perintah alat di dalam Debian lewat tab Terminal: memeriksa alat yang
 * dibutuhkan lebih dulu. Bila ada yang kurang, pengguna bisa memasangnya lalu menjalankan, atau
 * membuka menu Paket (paket yang kurang disorot). Debian yang belum ada dan alat yang tidak
 * terdaftar di katalog punya dialog sendiri. Panggil [launch] hanya saat view fragment hidup.
 */
internal class ToolLauncher(private val fragment: Fragment) {

    private var runJob: Job? = null

    fun launch(
        requiredIds: List<String>,
        command: String,
        @StringRes title: Int,
        onStart: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
        installWrapper: ((String) -> String)? = null
    ) {
        val appContext = fragment.context?.applicationContext ?: return
        val checker = PackageToolReadinessChecker(appContext)
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (val readiness = checker.check(requiredIds)) {
                // Status tidak terbaca: jalankan saja, terminal akan menampilkan galat sebenarnya.
                is ToolReadiness.Ready, is ToolReadiness.Unknown -> send(command, onStart, onFailed)
                is ToolReadiness.NoDebian -> askOpenDebian(title)
                is ToolReadiness.Missing ->
                    askInstall(command, readiness.tools, title, onStart, onFailed, installWrapper)
                is ToolReadiness.Unregistered -> showUnregistered(readiness.ids, title)
            }
        }
    }

    private fun askInstall(
        command: String,
        missing: List<ToolRequirement>,
        @StringRes title: Int,
        onStart: (() -> Unit)?,
        onFailed: (() -> Unit)?,
        installWrapper: ((String) -> String)?
    ) {
        val ctx = fragment.context ?: return
        val specs = missing.mapNotNull { tool -> PackageCatalog.all.firstOrNull { it.id == tool.id } }
        val install = PackageCatalog.installAllCommand(specs) ?: return
        val names = missing.joinToString("\n") { "• ${it.name}" }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(fragment.getString(R.string.tools_gap_message, names))
            .setPositiveButton(R.string.tools_install_and_run) { _, _ ->
                send(installWrapper?.invoke(install) ?: "$install && $command", onStart, onFailed)
            }
            .setNeutralButton(R.string.tools_open_packages) { _, _ ->
                openSetup(ToolSetupTarget.PACKAGES, missing.map { it.id })
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun askOpenDebian(@StringRes title: Int) {
        val ctx = fragment.context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(R.string.tools_no_debian)
            .setPositiveButton(R.string.tools_open_debian) { _, _ ->
                openSetup(ToolSetupTarget.DEBIAN, emptyList())
            }
            .setNegativeButton(R.string.tools_cancel, null)
            .show()
    }

    private fun showUnregistered(ids: List<String>, @StringRes title: Int) {
        val ctx = fragment.context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(fragment.getString(R.string.tools_unregistered_message, ids.joinToString(", ")))
            .setPositiveButton(R.string.tools_close, null)
            .show()
    }

    /** Meminta Settings membuka menu pemasangan; permintaan diproses SettingsFragment. */
    private fun openSetup(target: ToolSetupTarget, ids: List<String>) {
        SettingsDependencies.toolSetup().request(target, ids)
    }

    /** Mengetik [inner] di dalam Debian pada sesi aktif tab Terminal. */
    private fun send(inner: String, onStart: (() -> Unit)?, onFailed: (() -> Unit)?) {
        if (runJob?.isActive == true) return
        val host = fragment.activity ?: return
        val line = ShellLine.wrapForDebian(inner)
        runJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            onStart?.invoke()
            val reason = TerminalCommandRunner.run(host, line)
            if (reason != null) {
                fragment.toolsToast(fragment.getString(R.string.tools_run_failed, reason))
                onFailed?.invoke()
            }
        }
    }
}
