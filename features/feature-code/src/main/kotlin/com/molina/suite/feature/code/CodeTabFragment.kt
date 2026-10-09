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
import kotlinx.coroutines.launch

/** Tab Code: menampilkan keadaan pemasangan code-server dan tombol aksinya. */
class CodeTabFragment : Fragment() {

    private var stateText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var actionButton: Button? = null

    private val installer: CodeServerInstaller
        get() = CodeEngineModule.requireEngine().installer

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
        val button = Button(context).apply {
            visibility = View.GONE
            setOnClickListener { installer.install() }
        }

        stateText = status
        progressBar = progress
        actionButton = button

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
            addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(16) }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                installer.refresh()
                installer.state.collect { render(it) }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Shell memakai add/hide, jadi lifecycle tidak berubah saat tab disembunyikan.
        if (!hidden) installer.refresh()
    }

    override fun onDestroyView() {
        stateText = null
        progressBar = null
        actionButton = null
        super.onDestroyView()
    }

    private fun render(state: CodeInstallState) {
        val text = stateText ?: return
        val progress = progressBar ?: return
        val button = actionButton ?: return
        val context = requireContext()

        progress.visibility = View.GONE
        button.visibility = View.GONE

        when (state) {
            CodeInstallState.RootfsMissing -> text.setText(R.string.code_state_rootfs_missing)
            is CodeInstallState.NotInstalled -> {
                text.text = getString(
                    R.string.code_state_not_installed,
                    Formatter.formatShortFileSize(context, state.downloadBytes)
                )
                button.setText(R.string.code_action_install)
                button.visibility = View.VISIBLE
            }
            is CodeInstallState.Installing -> {
                progress.visibility = View.VISIBLE
                if (state.phase == InstallPhase.DOWNLOAD && state.totalBytes > 0) {
                    val percent = (state.downloadedBytes * 100 / state.totalBytes).toInt().coerceIn(0, 100)
                    progress.isIndeterminate = false
                    progress.progress = percent
                    text.text = getString(
                        R.string.code_state_downloading,
                        Formatter.formatShortFileSize(context, state.downloadedBytes),
                        Formatter.formatShortFileSize(context, state.totalBytes)
                    )
                } else {
                    progress.isIndeterminate = true
                    text.setText(phaseLabel(state.phase))
                }
            }
            is CodeInstallState.Installed ->
                text.text = getString(R.string.code_state_installed, state.version)
            is CodeInstallState.Failed -> {
                text.text = getString(R.string.code_state_failed, state.reason)
                button.setText(R.string.code_action_retry)
                button.visibility = View.VISIBLE
            }
        }
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
