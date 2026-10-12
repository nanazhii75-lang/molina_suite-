package com.molina.suite.feature.mpv

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.ToolReadiness
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.mpv.UrlPlaybackGate
import kotlinx.coroutines.launch

/**
 * Memeriksa yt-dlp sebelum tautan situs video diputar. Bila belum ada, pengguna ditawari
 * membuka menu Paket (paket yang kurang disorot), memutar saja (tautan langsung ke berkas tetap
 * jalan tanpa yt-dlp), atau batal. Tautan langsung ke berkas media tidak diperiksa sama sekali.
 */
internal class MpvToolGate(
    private val checker: ToolReadinessChecker,
    private val setup: ToolSetupBoard
) : UrlPlaybackGate {

    override fun check(fragment: Fragment, url: String, proceed: () -> Unit) {
        if (!YtdlUrls.needsYtdl(url)) {
            proceed()
            return
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            when (val readiness = checker.check(listOf(YTDLP_ID))) {
                // Status tidak terbaca: putar saja, mpv menampilkan galat sebenarnya.
                is ToolReadiness.Ready, is ToolReadiness.Unknown -> proceed()
                is ToolReadiness.Missing -> ask(
                    fragment,
                    fragment.getString(
                        R.string.gate_missing_message,
                        readiness.tools.joinToString(", ") { it.name }
                    ),
                    R.string.gate_open_packages,
                    ToolSetupTarget.PACKAGES,
                    readiness.tools.map { it.id },
                    proceed
                )
                is ToolReadiness.NoDebian -> ask(
                    fragment,
                    fragment.getString(R.string.gate_no_debian_message),
                    R.string.gate_open_debian,
                    ToolSetupTarget.DEBIAN,
                    emptyList(),
                    proceed
                )
                is ToolReadiness.Unregistered -> showUnregistered(fragment, readiness.ids, proceed)
            }
        }
    }

    private fun ask(
        fragment: Fragment,
        message: String,
        @StringRes openLabel: Int,
        target: ToolSetupTarget,
        toolIds: List<String>,
        proceed: () -> Unit
    ) {
        val ctx = fragment.context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.gate_title)
            .setMessage(message)
            .setPositiveButton(openLabel) { _, _ -> openSetup(fragment, target, toolIds) }
            .setNeutralButton(R.string.gate_play_anyway) { _, _ -> proceed() }
            .setNegativeButton(R.string.gate_cancel, null)
            .show()
    }

    private fun showUnregistered(fragment: Fragment, ids: List<String>, proceed: () -> Unit) {
        val ctx = fragment.context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.gate_title)
            .setMessage(fragment.getString(R.string.gate_unregistered_message, ids.joinToString(", ")))
            .setPositiveButton(R.string.gate_play_anyway) { _, _ -> proceed() }
            .setNegativeButton(R.string.gate_cancel, null)
            .show()
    }

    /** Meminta Settings membuka menu pemasangan, lalu memindahkan shell ke tab Settings. */
    private fun openSetup(fragment: Fragment, target: ToolSetupTarget, toolIds: List<String>) {
        setup.request(target, toolIds)
        (fragment.activity as? HostTabSwitcher)?.showEngine(EngineId.SETTINGS)
    }

    private companion object {
        /** Id yt-dlp di katalog menu Paket; id yang salah terlihat sebagai dialog "belum terdaftar". */
        const val YTDLP_ID = "ytdlp"
    }
}
