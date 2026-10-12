package com.molina.suite.feature.terminal

import android.os.Bundle
import android.view.Menu
import android.view.View
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.HostChromeController
import com.molina.suite.core.common.HostContextMenuListener
import com.molina.suite.core.common.KeyboardVisibilityWatcher
import com.molina.suite.terminal.app.TermuxFragment

/**
 * Fragment tab Terminal: membungkus engine (TermuxFragment) dengan kontrak shell.
 * Engine tetap tidak mengenal core-common; adaptasinya hanya di sini.
 */
class TerminalTabFragment : TermuxFragment(), HostBackPressHandler, HostContextMenuListener {

    private var keyboardWatcher: KeyboardVisibilityWatcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TerminalTabRegistry.attach(this)
    }

    override fun onDestroy() {
        TerminalTabRegistry.detach(this)
        super.onDestroy()
    }

    /** Keyboard aktif: header molina dan navigasi bawah disembunyikan (sama seperti tab Code). */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyboardWatcher = KeyboardVisibilityWatcher(view) { visible ->
            (activity as? HostChromeController)?.setChromeCompact(visible)
        }
        updateKeyboardWatcher()
    }

    override fun onResume() {
        super.onResume()
        updateKeyboardWatcher()
    }

    override fun onPause() {
        keyboardWatcher?.stop()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Shell memakai add/hide, jadi lifecycle tidak berubah saat tab disembunyikan.
        updateKeyboardWatcher()
    }

    override fun onDestroyView() {
        keyboardWatcher?.stop()
        keyboardWatcher = null
        super.onDestroyView()
    }

    private fun updateKeyboardWatcher() {
        if (isResumed && !isHidden) keyboardWatcher?.start() else keyboardWatcher?.stop()
    }

    override fun onHostBackPressed(): Boolean = handleBackPressed()

    override fun onHostContextMenuClosed(menu: Menu) {
        onContextMenuClosed(menu)
    }
}
