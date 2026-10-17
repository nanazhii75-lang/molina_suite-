package com.molina.suite.feature.settings.integration

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.common.IntegrationRoute
import com.molina.suite.core.common.IntegrationSettings
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.SettingsDependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layar Integrasi: daftar lintasan antar-modul (Terminal, Code, mpv) dengan saklar, status
 * kesiapan, dan tombol Periksa. Hanya membaca registri engine dan menulis [IntegrationSettings];
 * penegakan lintasan ada di modul yang bersangkutan.
 */
class IntegrationFragment : Fragment(R.layout.fragment_integration), Refreshable {

    private val statusViews = LinkedHashMap<IntegrationRoute, TextView>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val settings = IntegrationSettings(requireContext())
        val container = view.findViewById<LinearLayout>(R.id.integration_list)
        view.findViewById<View>(R.id.integration_refresh).setOnClickListener { refresh() }
        statusViews.clear()
        for (route in IntegrationRoute.values()) {
            val item = layoutInflater.inflate(R.layout.item_integration_route, container, false)
            item.findViewById<TextView>(R.id.integration_title).setText(titleOf(route))
            item.findViewById<TextView>(R.id.integration_desc).setText(descriptionOf(route))
            val toggle = item.findViewById<SwitchCompat>(R.id.integration_switch)
            toggle.isChecked = settings.isEnabled(route)
            toggle.setOnCheckedChangeListener { _, on ->
                settings.setEnabled(route, on)
                refresh()
            }
            item.findViewById<View>(R.id.integration_check).setOnClickListener { showCheck(route) }
            statusViews[route] = item.findViewById(R.id.integration_status)
            container.addView(item)
        }
        refresh()
    }

    override fun refresh() {
        if (view == null) return
        val context = context ?: return
        val settings = IntegrationSettings(context)
        viewLifecycleOwner.lifecycleScope.launch {
            val probe = IntegrationProbe(context, SettingsDependencies.engines())
            val states = withContext(Dispatchers.IO) {
                IntegrationRoute.values().associateWith { probe.check(it).state(settings.isEnabled(it)) }
            }
            if (view == null) return@launch
            for ((route, state) in states) statusViews[route]?.let { render(it, state) }
        }
    }

    private fun render(view: TextView, state: IntegrationState) {
        view.setText(stateText(state))
        val color = if (state == IntegrationState.READY || state == IntegrationState.DISABLED) {
            R.color.ms_text_secondary
        } else {
            R.color.ms_log_error
        }
        view.setTextColor(view.context.getColor(color))
    }

    private fun showCheck(route: IntegrationRoute) {
        val context = context ?: return
        val settings = IntegrationSettings(context)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                IntegrationProbe(context, SettingsDependencies.engines()).check(route)
            }
            if (!isAdded) return@launch
            val lines = ArrayList<String>()
            lines.add(getString(R.string.integration_check_enabled, mark(settings.isEnabled(route))))
            lines.add(getString(R.string.integration_check_engines, mark(result.enginesReady)))
            for ((name, ok) in result.scripts) {
                lines.add(getString(R.string.integration_check_script, mark(ok), name))
            }
            if (result.entries.isNotEmpty()) {
                lines.add(getString(R.string.integration_check_entry, mark(result.componentsReady)))
            }
            MaterialAlertDialogBuilder(context)
                .setTitle(titleOf(route))
                .setMessage(lines.joinToString("\n"))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun mark(ok: Boolean): String = if (ok) "\u2713" else "\u2717"

    @StringRes
    private fun titleOf(route: IntegrationRoute): Int = when (route) {
        IntegrationRoute.TERMINAL_TO_CODE -> R.string.integration_terminal_code_title
        IntegrationRoute.TERMINAL_TO_MPV -> R.string.integration_terminal_mpv_title
        IntegrationRoute.CODE_TO_MPV -> R.string.integration_code_mpv_title
    }

    @StringRes
    private fun descriptionOf(route: IntegrationRoute): Int = when (route) {
        IntegrationRoute.TERMINAL_TO_CODE -> R.string.integration_terminal_code_desc
        IntegrationRoute.TERMINAL_TO_MPV -> R.string.integration_terminal_mpv_desc
        IntegrationRoute.CODE_TO_MPV -> R.string.integration_code_mpv_desc
    }

    @StringRes
    private fun stateText(state: IntegrationState): Int = when (state) {
        IntegrationState.READY -> R.string.integration_state_ready
        IntegrationState.DISABLED -> R.string.integration_state_disabled
        IntegrationState.ENGINE_MISSING -> R.string.integration_state_engine
        IntegrationState.SCRIPT_MISSING -> R.string.integration_state_script
        IntegrationState.COMPONENT_MISSING -> R.string.integration_state_component
    }
}
