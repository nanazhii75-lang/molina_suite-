package com.molina.suite.feature.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.ToolSetupRequest
import com.molina.suite.core.common.ToolSetupTarget
import com.molina.suite.feature.settings.debian.DebianFragment
import com.molina.suite.feature.settings.library.LibraryFragment
import com.molina.suite.feature.settings.logcat.LogcatFragment
import com.molina.suite.feature.settings.packages.PackagesFragment
import com.molina.suite.feature.settings.tools.ToolsFragment
import kotlinx.coroutines.launch

/** Layar utama Settings: daftar submenu. Submenu dibuka sebagai child fragment. */
class SettingsFragment : Fragment(R.layout.fragment_settings), HostBackPressHandler {

    private var backStackListener: FragmentManager.OnBackStackChangedListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menu = view.findViewById<View>(R.id.settings_menu)
        val container = view.findViewById<View>(R.id.settings_child_container)

        val sync = {
            val open = childFragmentManager.backStackEntryCount > 0
            container.visibility = if (open) View.VISIBLE else View.GONE
            menu.visibility = if (open) View.GONE else View.VISIBLE
        }
        val listener = FragmentManager.OnBackStackChangedListener { sync() }
        childFragmentManager.addOnBackStackChangedListener(listener)
        backStackListener = listener

        view.findViewById<View>(R.id.settings_row_tools).setOnClickListener { openTools() }
        view.findViewById<View>(R.id.settings_row_library).setOnClickListener { openLibrary() }
        view.findViewById<View>(R.id.settings_row_debian).setOnClickListener { openDebian() }
        view.findViewById<View>(R.id.settings_row_packages).setOnClickListener { openPackages() }
        view.findViewById<View>(R.id.settings_row_logcat).setOnClickListener { openLogcat() }
        observeToolSetup()
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

    private fun openLogcat() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, LogcatFragment())
            .addToBackStack(BACKSTACK_LOGCAT)
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

    override fun onHostBackPressed(): Boolean {
        if (childFragmentManager.backStackEntryCount == 0) return false
        childFragmentManager.popBackStack()
        return true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            (childFragmentManager.findFragmentById(R.id.settings_child_container) as? Refreshable)?.refresh()
        }
    }

    override fun onDestroyView() {
        backStackListener?.let { childFragmentManager.removeOnBackStackChangedListener(it) }
        backStackListener = null
        super.onDestroyView()
    }

    private companion object {
        const val BACKSTACK_TOOLS = "tools"
        const val BACKSTACK_LIBRARY = "library"
        const val BACKSTACK_DEBIAN = "debian"
        const val BACKSTACK_PACKAGES = "packages"
        const val BACKSTACK_LOGCAT = "logcat"
    }
}
