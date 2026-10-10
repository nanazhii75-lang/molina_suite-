package com.molina.suite

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.DaemonId
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostBackPressHandler
import com.molina.suite.core.common.HostChromeController
import com.molina.suite.core.common.HostContextMenuListener
import com.molina.suite.databinding.ActivityMainBinding
import com.molina.suite.ui.EngineUnavailableFragment
import com.molina.suite.ui.colorRes
import com.molina.suite.ui.labelRes
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), HostChromeController {

    private lateinit var binding: ActivityMainBinding
    private val molina get() = application as MolinaApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val engineId = engineIdFor(item.itemId) ?: return@setOnItemSelectedListener false
            show(engineId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { }
        installBackHandling()

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_terminal
        }

        bindDaemon(DaemonId.TERMINAL, binding.dotTerminal)
        bindDaemon(DaemonId.WEBDAV, binding.dotWebdav)
        bindDaemon(DaemonId.MPV, binding.dotMpv)
    }

    /** Meneruskan tombol Back ke fragment tab yang sedang tampil sebelum perilaku default shell. */
    private fun installBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val handler = visibleFragment() as? HostBackPressHandler
                if (handler?.onHostBackPressed() == true) return
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    override fun onContextMenuClosed(menu: Menu) {
        super.onContextMenuClosed(menu)
        (visibleFragment() as? HostContextMenuListener)?.onHostContextMenuClosed(menu)
    }

    /** Mode ringkas: sembunyikan header dan navigasi bawah agar area kerja fragment lebih luas. */
    override fun setChromeCompact(compact: Boolean) {
        val visibility = if (compact) View.GONE else View.VISIBLE
        binding.header.visibility = visibility
        binding.bottomNav.visibility = visibility
    }

    private fun visibleFragment(): Fragment? =
        supportFragmentManager.fragments.firstOrNull { it.isAdded && !it.isHidden }

    private fun engineIdFor(menuId: Int): EngineId? = when (menuId) {
        R.id.nav_mpv -> EngineId.MPV
        R.id.nav_terminal -> EngineId.TERMINAL
        R.id.nav_code -> EngineId.CODE
        R.id.nav_settings -> EngineId.SETTINGS
        else -> null
    }

    /** Fragment tiap engine dipertahankan (add/hide) agar sesi terminal tidak hilang saat pindah tab. */
    private fun show(id: EngineId) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        fm.fragments.forEach { if (it.tag != id.name) tx.hide(it) }
        val existing = fm.findFragmentByTag(id.name)
        if (existing == null) {
            val fragment = molina.engines.find(id)?.createFragment()
                ?: EngineUnavailableFragment.newInstance(id)
            tx.add(R.id.engineContainer, fragment, id.name)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    private fun bindDaemon(id: DaemonId, dot: View) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                molina.daemons.status(id).collect { status ->
                    val color = ContextCompat.getColor(this@MainActivity, status.colorRes())
                    dot.backgroundTintList = ColorStateList.valueOf(color)
                    dot.contentDescription = getString(status.labelRes())
                }
            }
        }
    }
}
