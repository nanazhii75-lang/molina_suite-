package com.molina.suite.feature.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.feature.settings.debian.DebianFragment
import com.molina.suite.feature.settings.library.LibraryFragment

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

        view.findViewById<View>(R.id.settings_row_library).setOnClickListener { openLibrary() }
        view.findViewById<View>(R.id.settings_row_debian).setOnClickListener { openDebian() }
        sync()
    }

    private fun openLibrary() {
        if (childFragmentManager.backStackEntryCount > 0) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.settings_child_container, LibraryFragment())
            .addToBackStack(BACKSTACK_LIBRARY)
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

    override fun onDestroyView() {
        backStackListener?.let { childFragmentManager.removeOnBackStackChangedListener(it) }
        backStackListener = null
        super.onDestroyView()
    }

    private companion object {
        const val BACKSTACK_LIBRARY = "library"
        const val BACKSTACK_DEBIAN = "debian"
    }
}
