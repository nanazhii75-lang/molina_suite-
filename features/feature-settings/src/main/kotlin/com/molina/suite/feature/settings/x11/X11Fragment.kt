package com.molina.suite.feature.settings.x11

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityPriority
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineLauncher
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.SettingsDependencies
import com.molina.suite.feature.settings.TerminalCommandRunner
import com.molina.suite.feature.settings.packages.PackageDetection
import com.molina.suite.feature.settings.packages.PackageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DESKTOP_PACKAGE_ID = "x11desktop"
private const val USAGE_POLL_MS = 3_000L

/** Layar Molina X11: status dan kendali server, status dan kendali desktop, pemasangan desktop. */
class X11Fragment : Fragment(R.layout.fragment_x11), Refreshable {

    private enum class DesktopState { CHECKING, NO_DEBIAN, MISSING, INSTALLED, FAILED }

    private var serverStatus: TextView? = null
    private var serverError: TextView? = null
    private var desktopStatus: TextView? = null
    private var serverStart: MaterialButton? = null
    private var serverStop: MaterialButton? = null
    private var desktopInstall: MaterialButton? = null
    private var desktopStart: MaterialButton? = null
    private var desktopStop: MaterialButton? = null
    private var ramSummary: TextView? = null
    private var stopResult: TextView? = null
    private var stopChoose: MaterialButton? = null
    private var desktopState = DesktopState.CHECKING

    private fun launcher(): EngineLauncher? =
        SettingsDependencies.engines().findLauncher(EngineId.X11)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        serverStatus = view.findViewById(R.id.x11_server_status)
        serverError = view.findViewById(R.id.x11_server_error)
        desktopStatus = view.findViewById(R.id.x11_desktop_status)
        serverStart = view.findViewById(R.id.x11_server_start)
        serverStop = view.findViewById(R.id.x11_server_stop)
        desktopInstall = view.findViewById(R.id.x11_desktop_install)
        desktopStart = view.findViewById(R.id.x11_desktop_start)
        desktopStop = view.findViewById(R.id.x11_desktop_stop)
        ramSummary = view.findViewById(R.id.x11_ram_summary)
        stopResult = view.findViewById(R.id.x11_stop_result)
        stopChoose = view.findViewById(R.id.x11_stop_choose)

        view.findViewById<View>(R.id.x11_refresh).setOnClickListener { refresh() }
        serverStart?.setOnClickListener { startServer() }
        serverStop?.setOnClickListener { launcher()?.stop() }
        desktopInstall?.setOnClickListener { requestInstall() }
        desktopStart?.setOnClickListener {
            runInTerminal(R.string.x11_run_start_desktop, X11Commands.startDesktop())
        }
        desktopStop?.setOnClickListener {
            runInTerminal(R.string.x11_run_stop_desktop, X11Commands.stopDesktop())
        }

        stopChoose?.setOnClickListener { confirmStop() }
        observeUsage()
        observeLauncher()
        renderDesktop()
        refresh()
    }

    override fun onDestroyView() {
        serverStatus = null
        serverError = null
        desktopStatus = null
        serverStart = null
        serverStop = null
        desktopInstall = null
        desktopStart = null
        desktopStop = null
        ramSummary = null
        stopResult = null
        stopChoose = null
        super.onDestroyView()
    }

    override fun refresh() {
        val appContext = context?.applicationContext ?: return
        desktopState = DesktopState.CHECKING
        renderDesktop()
        viewLifecycleOwner.lifecycleScope.launch {
            val detection = withContext(Dispatchers.IO) { PackageDetector.detect(appContext) }
            desktopState = when (detection) {
                PackageDetection.NoDebian -> DesktopState.NO_DEBIAN
                is PackageDetection.Failed -> DesktopState.FAILED
                is PackageDetection.Ok ->
                    if (detection.installed[DESKTOP_PACKAGE_ID] == true) DesktopState.INSTALLED else DesktopState.MISSING
            }
            renderDesktop(detection as? PackageDetection.Failed)
        }
    }

    private fun observeLauncher() {
        val launcher = launcher()
        if (launcher == null) {
            renderServer(EngineStatus.UNAVAILABLE)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { launcher.status.collect { renderServer(it) } }
                launch { launcher.lastError.collect { renderError(it) } }
            }
        }
    }

    private fun startServer() {
        val host = activity ?: return
        launcher()?.launch(host)
    }

    private fun renderServer(status: EngineStatus) {
        serverStatus?.setText(
            when (status) {
                EngineStatus.RUNNING -> R.string.x11_status_running
                EngineStatus.STARTING -> R.string.x11_status_starting
                EngineStatus.STOPPED -> R.string.x11_status_stopped
                EngineStatus.ERROR -> R.string.x11_status_error
                EngineStatus.UNAVAILABLE -> R.string.x11_status_unavailable
            }
        )
        serverStart?.isEnabled = status == EngineStatus.STOPPED || status == EngineStatus.ERROR
        serverStop?.isEnabled = status == EngineStatus.STARTING || status == EngineStatus.RUNNING
    }

    private fun renderError(message: String?) {
        val view = serverError ?: return
        view.text = message.orEmpty()
        view.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun renderDesktop(failure: PackageDetection.Failed? = null) {
        desktopStatus?.text = when (desktopState) {
            DesktopState.CHECKING -> getString(R.string.x11_desktop_checking)
            DesktopState.NO_DEBIAN -> getString(R.string.x11_desktop_no_debian)
            DesktopState.MISSING -> getString(R.string.x11_desktop_missing)
            DesktopState.INSTALLED -> getString(R.string.x11_desktop_installed)
            DesktopState.FAILED -> getString(R.string.x11_desktop_failed, failure?.reason.orEmpty())
        }
        val installed = desktopState == DesktopState.INSTALLED
        desktopInstall?.isEnabled = desktopState == DesktopState.MISSING || desktopState == DesktopState.NO_DEBIAN
        desktopStart?.isEnabled = installed
        desktopStop?.isEnabled = installed
    }

    private fun observeUsage() {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val usage = withContext(Dispatchers.IO) { X11Control.usage(appContext) }
                    renderUsage(usage)
                    delay(USAGE_POLL_MS)
                }
            }
        }
    }

    private fun renderUsage(usage: X11Usage) {
        val view = ramSummary ?: return
        view.text = if (usage.isIdle) {
            getString(R.string.x11_ram_idle)
        } else {
            getString(
                R.string.x11_ram_summary,
                X11Usage.format(usage.serverKb),
                usage.serverCount,
                X11Usage.format(usage.desktopKb),
                usage.desktopCount,
                X11Usage.format(usage.totalKb)
            )
        }
    }

    /** Dialog pilihan: centang desktop, server, atau keduanya (semua) lalu hentikan sampai tuntas. */
    private fun confirmStop() {
        val host = activity ?: return
        val items = arrayOf(
            getString(R.string.x11_stop_item_desktop),
            getString(R.string.x11_stop_item_server)
        )
        val checked = booleanArrayOf(true, true)
        MaterialAlertDialogBuilder(host)
            .setTitle(R.string.x11_stop_dialog_title)
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(R.string.x11_stop_cancel, null)
            .setPositiveButton(R.string.x11_stop_confirm) { _, _ ->
                if (checked[0] || checked[1]) {
                    stopSelected(desktop = checked[0], server = checked[1])
                }
            }
            .show()
    }

    /**
     * Menghentikan langsung lewat /proc (tanpa mengetik ke Terminal): desktop dulu supaya aplikasi
     * X menutup rapi, lalu server. Hasil diperiksa ulang dan dilaporkan, termasuk perkiraan
     * memori yang dibebaskan.
     */
    private fun stopSelected(desktop: Boolean, server: Boolean) {
        val appContext = context?.applicationContext ?: return
        stopChoose?.isEnabled = false
        showStopResult(getString(R.string.x11_stop_working))
        viewLifecycleOwner.lifecycleScope.launch {
            val message = Activities.log.track(ActivitySource.SETTING, "Molina X11: hentikan tuntas") { entry ->
                val before = withContext(Dispatchers.IO) { X11Control.usage(appContext) }
                val desktopOutcome = if (desktop) X11Control.stopDesktop(appContext) else null
                val serverOutcome = if (server) {
                    launcher()?.stop()
                    X11Control.stopServer()
                } else {
                    null
                }
                val remaining = desktopOutcome?.remaining.orEmpty() + serverOutcome?.remaining.orEmpty()
                if (remaining.isEmpty()) {
                    val freedKb = (if (desktop) before.desktopKb else 0L) + (if (server) before.serverKb else 0L)
                    val text = appContext.getString(
                        R.string.x11_stop_done,
                        desktopOutcome?.stopped ?: 0,
                        serverOutcome?.stopped ?: 0,
                        X11Usage.format(freedKb)
                    )
                    entry.note(text)
                    text
                } else {
                    val pids = remaining.sorted().joinToString(", ")
                    entry.end(false, "proses tersisa: $pids", ActivityPriority.WARN)
                    appContext.getString(R.string.x11_stop_remaining, pids)
                }
            }
            showStopResult(message)
            stopChoose?.isEnabled = true
        }
    }

    private fun showStopResult(text: String) {
        val view = stopResult ?: return
        view.text = text
        view.visibility = View.VISIBLE
    }

    /** Membuka menu Paket (paket desktop disorot), atau menu Debian bila rootfs belum ada. */
    private fun requestInstall() {
        val target = if (desktopState == DesktopState.NO_DEBIAN) ToolSetupTarget.DEBIAN else ToolSetupTarget.PACKAGES
        SettingsDependencies.toolSetup().request(target, listOf(DESKTOP_PACKAGE_ID))
    }

    private fun runInTerminal(labelRes: Int, line: String) {
        val host = activity ?: return
        val label = getString(labelRes)
        viewLifecycleOwner.lifecycleScope.launch {
            val reason = TerminalCommandRunner.run(host, label, line)
            if (reason != null) {
                Toast.makeText(
                    host.applicationContext,
                    getString(R.string.library_run_failed, reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
