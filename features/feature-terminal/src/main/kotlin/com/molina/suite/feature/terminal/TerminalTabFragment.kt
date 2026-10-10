package com.molina.suite.feature.terminal

import android.os.Bundle
import android.view.Menu
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.HostContextMenuListener
import com.molina.suite.terminal.app.TermuxFragment

/**
 * Fragment tab Terminal: membungkus engine (TermuxFragment) dengan kontrak shell.
 * Engine tetap tidak mengenal core-common; adaptasinya hanya di sini.
 */
class TerminalTabFragment : TermuxFragment(), HostBackPressHandler, HostContextMenuListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TerminalTabRegistry.attach(this)
    }

    override fun onDestroy() {
        TerminalTabRegistry.detach(this)
        super.onDestroy()
    }

    override fun onHostBackPressed(): Boolean = handleBackPressed()

    override fun onHostContextMenuClosed(menu: Menu) {
        onContextMenuClosed(menu)
    }
}
