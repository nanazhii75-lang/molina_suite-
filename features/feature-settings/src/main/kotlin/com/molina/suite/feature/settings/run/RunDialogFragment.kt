package com.molina.suite.feature.settings.run

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Layar hampir penuh yang menampilkan progres satu proses Pusat Kontrol secara ramah pengguna.
 * Terminal tetap menjadi versi teknisnya. Data dibaca dari berkas status yang ditulis skrip,
 * bukan dari teks terminal. Tombol Keluar baru muncul setelah proses selesai atau dianggap macet.
 */
internal class RunDialogFragment : DialogFragment(R.layout.dialog_run) {

    private var kind: RunKind = RunKind.PROBE
    private var statusPath: String = ""
    private var startedAt: Long = 0L
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Molina_RunDialog)
        isCancelable = false
        val args = requireArguments()
        kind = RunKind.values().getOrElse(args.getInt(ARG_KIND, 0)) { RunKind.PROBE }
        statusPath = args.getString(ARG_STATUS_PATH).orEmpty()
        startedAt = args.getLong(ARG_STARTED_AT, System.currentTimeMillis())
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val presenter = kind.presenter
        view.findViewById<TextView>(R.id.run_title).setText(kind.title)
        val content = view.findViewById<FrameLayout>(R.id.run_content)
        layoutInflater.inflate(presenter.layout, content, true)
        presenter.onInflated(content)

        view.findViewById<Button>(R.id.run_exit).setOnClickListener { exit(EngineId.SETTINGS) }
        view.findViewById<Button>(R.id.run_terminal).setOnClickListener { exit(EngineId.TERMINAL) }

        // Selama proses jalan, tombol Kembali tidak menutup layar; setelah selesai ia sama dengan Keluar.
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP && finished) exit(EngineId.SETTINGS)
            true
        }

        render(view, presenter, RunSnapshot.Waiting(0L))
        val file = File(statusPath)
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) { RunWatcher.read(file, startedAt) }
                render(view, presenter, snapshot)
                if (snapshot is RunSnapshot.Finished || snapshot is RunSnapshot.TimedOut) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun exit(target: EngineId) {
        (activity as? HostTabSwitcher)?.showEngine(target)
        dismissAllowingStateLoss()
    }

    private fun render(root: View, presenter: RunPresenter, snapshot: RunSnapshot) {
        val badge = root.findViewById<TextView>(R.id.run_badge)
        val percent = root.findViewById<TextView>(R.id.run_percent)
        val progress = root.findViewById<ProgressBar>(R.id.run_progress)
        val message = root.findViewById<TextView>(R.id.run_message)

        @ColorRes val color: Int
        @StringRes val badgeText: Int
        val messageText: String
        var percentValue: Int? = null
        var indeterminate = false

        when (snapshot) {
            is RunSnapshot.Waiting -> {
                color = R.color.ms_amber
                badgeText = R.string.run_badge_preparing
                messageText = getString(R.string.run_message_waiting)
                indeterminate = true
            }
            is RunSnapshot.Active -> {
                color = R.color.ms_amber
                badgeText = R.string.run_badge_running
                messageText = presenter.stageLabel(snapshot.status.stage)?.let { getString(it) }
                    ?: getString(R.string.run_message_working)
                percentValue = snapshot.status.progress
            }
            is RunSnapshot.Finished -> {
                val ok = snapshot.status.state == RunState.DONE
                color = if (ok) R.color.ms_status_on else R.color.ms_status_error
                badgeText = if (ok) R.string.run_badge_done else R.string.run_badge_failed
                messageText = if (ok) {
                    getString(presenter.doneMessage)
                } else {
                    snapshot.status.error.ifEmpty { getString(R.string.run_message_failed) }
                }
                percentValue = snapshot.status.progress
            }
            is RunSnapshot.TimedOut -> {
                color = R.color.ms_status_error
                badgeText = R.string.run_badge_timeout
                messageText = getString(
                    if (snapshot.reason == TimeoutReason.NOT_STARTED) {
                        R.string.run_message_not_started
                    } else {
                        R.string.run_message_stalled
                    }
                )
                percentValue = snapshot.last?.progress
            }
        }

        val colorInt = ContextCompat.getColor(requireContext(), color)
        badge.setText(badgeText)
        badge.setTextColor(colorInt)
        badge.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(colorInt, BADGE_ALPHA))
        message.text = messageText
        progress.isIndeterminate = indeterminate
        if (!indeterminate) progress.progress = percentValue ?: 0
        progress.progressTintList = ColorStateList.valueOf(colorInt)
        progress.indeterminateTintList = ColorStateList.valueOf(colorInt)
        percent.visibility = if (percentValue == null) View.GONE else View.VISIBLE
        percent.text = getString(R.string.run_percent, percentValue ?: 0)

        finished = snapshot is RunSnapshot.Finished || snapshot is RunSnapshot.TimedOut
        root.findViewById<View>(R.id.run_exit).visibility = if (finished) View.VISIBLE else View.GONE

        presenter.bind(root.findViewById(R.id.run_content), snapshot)
    }

    companion object {
        private const val TAG = "molina-run"
        private const val ARG_KIND = "run.kind"
        private const val ARG_STATUS_PATH = "run.statusPath"
        private const val ARG_STARTED_AT = "run.startedAt"
        private const val POLL_INTERVAL_MS = 500L
        private const val BADGE_ALPHA = 0x33

        /** Menampilkan layar progres untuk [session] dan mengembalikan fragmennya. */
        fun show(manager: FragmentManager, session: RunSession): RunDialogFragment {
            val fragment = RunDialogFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_KIND, session.kind.ordinal)
                    putString(ARG_STATUS_PATH, session.statusFile.path)
                    putLong(ARG_STARTED_AT, session.startedAt)
                }
            }
            fragment.show(manager, TAG)
            return fragment
        }
    }
}
