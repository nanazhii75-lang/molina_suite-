package com.molina.suite.feature.terminal

import android.app.AlertDialog
import android.app.ProgressDialog
import android.view.Menu
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.HostContextMenuListener
import com.molina.suite.terminal.app.TermuxFragment
import com.molina.suite.feature.terminal.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fragment tab Terminal: membungkus engine (TermuxFragment) dengan kontrak shell.
 * Engine tetap tidak mengenal core-common; adaptasinya hanya di sini.
 *
 * Sebelum sesi pertama dibuat, rootfs Debian bawaan APK dipasang lebih dulu (sekali saja).
 */
class TerminalTabFragment : TermuxFragment(), HostBackPressHandler, HostContextMenuListener {

    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressDialog: ProgressDialog? = null
    private var installRunning = false

    override fun onHostBackPressed(): Boolean = handleBackPressed()

    override fun onHostContextMenuClosed(menu: Menu) {
        onContextMenuClosed(menu)
    }

    override fun onBootstrapReady(proceed: Runnable) {
        val installer = DebianRootfsInstaller(requireActivity().application)
        if (!installer.isBundled() || installer.isInstalled()) {
            proceed.run()
            return
        }
        installDebian(installer, proceed)
    }

    override fun onDestroyView() {
        dismissProgress()
        super.onDestroyView()
    }

    private fun installDebian(installer: DebianRootfsInstaller, proceed: Runnable) {
        if (installRunning) return
        installRunning = true
        showProgress(R.string.debian_preinstall_copying)

        installScope.launch {
            val outcome = installer.ensureInstalled { phase ->
                runOnUi {
                    showProgress(
                        if (phase == DebianRootfsInstaller.Phase.COPYING) R.string.debian_preinstall_copying
                        else R.string.debian_preinstall_installing
                    )
                }
            }
            runOnUi {
                installRunning = false
                dismissProgress()
                when (outcome) {
                    is DebianRootfsInstaller.Outcome.Failed -> showFailure(outcome.message, installer, proceed)
                    DebianRootfsInstaller.Outcome.Installed -> proceed.run()
                }
            }
        }
    }

    private fun runOnUi(block: () -> Unit) {
        val host = activity ?: return
        host.runOnUiThread { if (isAdded) block() }
    }

    @Suppress("DEPRECATION")
    private fun showProgress(messageRes: Int) {
        val dialog = progressDialog ?: ProgressDialog(requireActivity()).apply {
            setTitle(R.string.debian_preinstall_title)
            isIndeterminate = true
            setCancelable(false)
            progressDialog = this
            show()
        }
        dialog.setMessage(getString(messageRes))
    }

    private fun dismissProgress() {
        try {
            progressDialog?.dismiss()
        } catch (e: RuntimeException) {
            // Window sudah hilang; tidak ada yang perlu ditutup.
        }
        progressDialog = null
        installRunning = false
    }

    private fun showFailure(message: String, installer: DebianRootfsInstaller, proceed: Runnable) {
        AlertDialog.Builder(requireActivity())
            .setTitle(R.string.debian_preinstall_failed_title)
            .setMessage(getString(R.string.debian_preinstall_failed_body, message))
            .setCancelable(false)
            .setPositiveButton(R.string.debian_preinstall_retry) { _, _ -> installDebian(installer, proceed) }
            .setNegativeButton(R.string.debian_preinstall_skip) { _, _ -> proceed.run() }
            .show()
    }
}
