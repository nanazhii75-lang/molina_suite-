package com.molina.suite.feature.code

import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Layar kontrol tab Code: keadaan pemasangan dan server beserta tombol aksinya. */
class CodeControlFragment : Fragment() {

    private var stateText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var primaryButton: Button? = null
    private var secondaryButton: Button? = null
    private var primaryAction: (() -> Unit)? = null
    private var secondaryAction: (() -> Unit)? = null

    private val engine: CodeEngine
        get() = CodeEngineModule.requireEngine()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = inflater.context
        val padding = dp(24)

        val title = TextView(context).apply {
            setText(R.string.code_title)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val status = TextView(context).apply {
            textSize = 16f
            gravity = Gravity.CENTER
        }
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        val primary = Button(context).apply {
            visibility = View.GONE
            setOnClickListener { primaryAction?.invoke() }
        }
        val secondary = Button(context).apply {
            visibility = View.GONE
            setOnClickListener { secondaryAction?.invoke() }
        }

        stateText = status
        progressBar = progress
        primaryButton = primary
        secondaryButton = secondary

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(title, blockParams(0))
            addView(status, blockParams(dp(16)))
            addView(progress, blockParams(dp(16)))
            addView(primary, buttonParams(dp(16)))
            addView(secondary, buttonParams(dp(8)))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.installer.refresh()
                combine(engine.installer.state, engine.controller.state) { install, server ->
                    install to server
                }.collect { (install, server) -> render(install, server) }
            }
        }
    }

    override fun onDestroyView() {
        stateText = null
        progressBar = null
        primaryButton = null
        secondaryButton = null
        primaryAction = null
        secondaryAction = null
        super.onDestroyView()
    }

    private fun render(install: CodeInstallState, server: CodeServerState) {
        val text = stateText ?: return
        val progress = progressBar ?: return
        val context = requireContext()

        progress.visibility = View.GONE
        primaryButton?.visibility = View.GONE
        secondaryButton?.visibility = View.GONE
        primaryAction = null
        secondaryAction = null

        when (install) {
            CodeInstallState.RootfsMissing -> text.setText(R.string.code_state_rootfs_missing)
            is CodeInstallState.NotInstalled -> {
                text.text = getString(
                    R.string.code_state_not_installed,
                    Formatter.formatShortFileSize(context, install.downloadBytes)
                )
                showPrimary(R.string.code_action_install) { engine.installer.install() }
            }
            is CodeInstallState.Installing -> {
                progress.visibility = View.VISIBLE
                if (install.phase == InstallPhase.DOWNLOAD && install.totalBytes > 0) {
                    val percent = (install.downloadedBytes * 100 / install.totalBytes).toInt().coerceIn(0, 100)
                    progress.isIndeterminate = false
                    progress.progress = percent
                    text.text = getString(
                        R.string.code_state_downloading,
                        Formatter.formatShortFileSize(context, install.downloadedBytes),
                        Formatter.formatShortFileSize(context, install.totalBytes)
                    )
                } else {
                    progress.isIndeterminate = true
                    text.setText(phaseLabel(install.phase))
                }
            }
            is CodeInstallState.Failed -> {
                text.text = getString(R.string.code_state_failed, install.reason)
                showPrimary(R.string.code_action_retry) { engine.installer.install() }
            }
            is CodeInstallState.Installed -> renderServer(install.version, server)
        }
    }

    private fun renderServer(version: String, server: CodeServerState) {
        val text = stateText ?: return
        val progress = progressBar ?: return
        when (server) {
            CodeServerState.Stopped -> {
                text.text = getString(R.string.code_state_installed, version)
                showPrimary(R.string.code_action_start) { engine.controller.start() }
            }
            CodeServerState.Starting -> {
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                text.setText(R.string.code_state_starting)
            }
            CodeServerState.Running -> {
                text.text = getString(R.string.code_state_running, CodeServerEndpoint.BASE_URL)
                showPrimary(R.string.code_action_open) { (parentFragment as? CodeTabFragment)?.openWorkspace() }
                showSecondary(R.string.code_action_stop) { engine.controller.stop() }
            }
            CodeServerState.Stopping -> {
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
                text.setText(R.string.code_state_stopping)
            }
            is CodeServerState.Failed -> {
                text.text = getString(R.string.code_state_server_failed, server.reason)
                showPrimary(R.string.code_action_start_again) { engine.controller.start() }
            }
        }
    }

    private fun showPrimary(labelRes: Int, action: () -> Unit) {
        val button = primaryButton ?: return
        button.setText(labelRes)
        button.visibility = View.VISIBLE
        primaryAction = action
    }

    private fun showSecondary(labelRes: Int, action: () -> Unit) {
        val button = secondaryButton ?: return
        button.setText(labelRes)
        button.visibility = View.VISIBLE
        secondaryAction = action
    }

    private fun phaseLabel(phase: InstallPhase): Int = when (phase) {
        InstallPhase.PREPARE, InstallPhase.DOWNLOAD -> R.string.code_phase_prepare
        InstallPhase.VERIFY -> R.string.code_phase_verify
        InstallPhase.EXTRACT -> R.string.code_phase_extract
        InstallPhase.FINISH -> R.string.code_phase_finish
    }

    private fun blockParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

    private fun buttonParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
