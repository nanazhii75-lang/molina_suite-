package com.molina.suite.feature.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.ToolSetupRequest
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.feature.settings.connections.ConnectionsFragment
import com.molina.suite.feature.settings.debian.DebianFragment
import com.molina.suite.feature.settings.integration.IntegrationFragment
import com.molina.suite.feature.settings.library.LibraryFragment
import com.molina.suite.feature.settings.logcat.LogcatFragment
import com.molina.suite.feature.settings.logcat.ProcessTracking
import com.molina.suite.feature.settings.mpvconfig.MpvConfigFragment
import com.molina.suite.feature.settings.packages.PackagesFragment
import com.molina.suite.feature.settings.tools.ToolsFragment
import com.molina.suite.feature.settings.x11.X11Fragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Layar utama Settings: daftar submenu. Submenu dibuka sebagai child fragment. */
class SettingsFragment : Fragment(R.layout.fragment_settings), HostBackPressHandler {

    private var backStackListener: FragmentManager.OnBackStackChangedListener? = null
    private var logcatSubtitle: TextView? = null
    private var logcatSubtitleColor: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menu = view.findViewById<View>(R.id.settings_menu)
        val container = view.findViewById<View>(R.id.settings_child_container)

        val sync = {
            val open = childFragmentManager.backStackEntryCount > 0
            container.visibility = if (open) View.VISIBLE else View.GONE
            menu.visibility = if (open) View.GONE else View.VISIBLE
        }
        val listener = FragmentManager.OnBackStackChangedListener {
            sync()
            if (childFragmentManager.backStackEntryCount == 0) updateCrashNotice()
        }
        childFragmentManager.addOnBackStackChangedListener(listener)
        backStackListener = listener

        view.findViewById<View>(R.id.settings_row_tools).setOnClickListener { openTools() }
        view.findViewById<View>(R.id.settings_row_library).setOnClickListener { openLibrary() }
        view.findViewById<View>(R.id.settings_row_debian).setOnClickListener { openDebian() }
        view.findViewById<View>(R.id.settings_row_x11).setOnClickListener { openX11() }
        view.findViewById<View>(R.id.settings_row_packages).setOnClickListener { openPackages() }
        view.findViewById<View>(R.id.settings_row_integration).setOnClickListener { openIntegration() }
        view.findViewById<View>(R.id.settings_row_logcat).setOnClickListener { openLogcat() }
        view.findViewById<View>(R.id.settings_row_connections).setOnClickListener { openConnections() }
        view.findViewById<View>(R.id.settings_row_mpvconfig).setOnClickListener { openMpvConfig() }
        logcatSubtitle = view.findViewById<TextView>(R.id.settings_logcat_subtitle).also {
            logcatSubtitleColor = it.currentTextColor
        }
        updateCrashNotice()
        observeToolSetup()
        observeLibraryDraft()
        sync()
    }

    /**
     * Menangani permintaan fitur lain (Quick Downloader, Play by URL di mpv) untuk membuka menu
     * pemasangan. Permintaan yang datang sebelum fragment ini dibuat tetap tertangani, karena
     * papan menyimpannya sampai dikonsumsi.
     */
    private fun observeToolSetup() {
        val board = SettingsDependencies.toolSetup()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                board.pending.collect { request ->
                    if (request != null) {
                        openSetup(request)
                        board.consume(request)
                    }
                }
            }
        }
    }

    /**
     * Menangani kiriman skrip dari tab Code: membuka Library bila belum terbuka. Dialog "Entri baru"
     * ditampilkan oleh LibraryFragment, yang juga mengonsumsi kirimannya.
     */
    private fun observeLibraryDraft() {
        val board = SettingsDependencies.libraryDraft()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                board.pending.collect { draft ->
                    if (draft != null) openLibraryForDraft()
                }
            }
        }
    }

    private fun openLibraryForDraft() {
        if (childFragmentManager.findFragmentById(R.id.settings_child_container) is LibraryFragment) return
        childFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, LibraryFragment())
            .addToBackStack(BACKSTACK_LIBRARY)
            .commit()
    }

    /** Menutup submenu yang terbuka lalu membuka Paket (paket yang kurang disorot) atau Debian. */
    private fun openSetup(request: ToolSetupRequest) {
        val (fragment, tag) = when (request.target) {
            ToolSetupTarget.PACKAGES ->
                Pair<Fragment, String>(PackagesFragment.newInstance(request.toolIds), BACKSTACK_PACKAGES)
            ToolSetupTarget.DEBIAN ->
                Pair<Fragment, String>(DebianFragment(), BACKSTACK_DEBIAN)
        }
        childFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, fragment)
            .addToBackStack(tag)
            .commit()
    }

    private fun openTools() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, ToolsFragment())
            .addToBackStack(BACKSTACK_TOOLS)
            .commit()
    }

    private fun openLibrary() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, LibraryFragment())
            .addToBackStack(BACKSTACK_LIBRARY)
            .commit()
    }

    private fun openPackages() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, PackagesFragment())
            .addToBackStack(BACKSTACK_PACKAGES)
            .commit()
    }

    private fun openIntegration() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, IntegrationFragment())
            .addToBackStack(BACKSTACK_INTEGRATION)
            .commit()
    }

    private fun openLogcat() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, LogcatFragment())
            .addToBackStack(BACKSTACK_LOGCAT)
            .commit()
    }

    private fun openMpvConfig() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, MpvConfigFragment())
            .addToBackStack(BACKSTACK_MPVCONFIG)
            .commit()
    }

    private fun openConnections() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, ConnectionsFragment())
            .addToBackStack(BACKSTACK_CONNECTIONS)
            .commit()
    }

    private fun openDebian() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, DebianFragment())
            .addToBackStack(BACKSTACK_DEBIAN)
            .commit()
    }

    private fun openX11() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, X11Fragment())
            .addToBackStack(BACKSTACK_X11)
            .commit()
    }

    /** Menandai baris Logcat dengan jumlah laporan crash yang belum dibuka. */
    private fun updateCrashNotice() {
        if (view == null) return
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val unseen = withContext(Dispatchers.IO) { ProcessTracking.crashNotice(appContext).unseen() }
            val subtitle = logcatSubtitle ?: return@launch
            if (unseen > 0) {
                subtitle.text = getString(R.string.settings_logcat_unseen, unseen)
                subtitle.setTextColor(subtitle.context.getColor(R.color.ms_log_error))
            } else {
                subtitle.setText(R.string.settings_logcat_subtitle)
                subtitle.setTextColor(logcatSubtitleColor)
            }
        }
    }

    override fun onHostBackPressed(): Boolean {
        if (childFragmentManager.backStackEntryCount == 0) return false
        childFragmentManager.popBackStack()
        return true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateCrashNotice()
            (childFragmentManager.findFragmentById(R.id.settings_child_container) as? Refreshable)?.refresh()
        }
    }

    override fun onDestroyView() {
        backStackListener?.let { childFragmentManager.removeOnBackStackChangedListener(it) }
        backStackListener = null
        logcatSubtitle = null
        super.onDestroyView()
    }

    private companion object {
        const val BACKSTACK_TOOLS = "tools"
        const val BACKSTACK_LIBRARY = "library"
        const val BACKSTACK_DEBIAN = "debian"
        const val BACKSTACK_PACKAGES = "packages"
        const val BACKSTACK_LOGCAT = "logcat"
        const val BACKSTACK_INTEGRATION = "integration"
        const val BACKSTACK_X11 = "x11"
        const val BACKSTACK_CONNECTIONS = "connections"
        const val BACKSTACK_MPVCONFIG = "mpvconfig"
    }
}
