package com.molina.suite

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.DaemonId
import com.molina.suite.core.common.EngineId
import com.molina.suite.databinding.ActivityMainBinding
import com.molina.suite.ui.EngineUnavailableFragment
import com.molina.suite.ui.colorRes
import com.molina.suite.ui.labelRes
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

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

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.nav_terminal
        }

        bindDaemon(DaemonId.TERMINAL, binding.dotTerminal)
        bindDaemon(DaemonId.WEBDAV, binding.dotWebdav)
        bindDaemon(DaemonId.KODI, binding.dotKodi)
    }

    private fun engineIdFor(menuId: Int): EngineId? = when (menuId) {
        R.id.nav_kodi -> EngineId.KODI
        R.id.nav_files -> EngineId.FILES
        R.id.nav_terminal -> EngineId.TERMINAL
        R.id.nav_webdash -> EngineId.WEBDASH
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
