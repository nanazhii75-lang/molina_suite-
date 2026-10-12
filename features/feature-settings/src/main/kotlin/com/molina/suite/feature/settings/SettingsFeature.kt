package com.molina.suite.feature.settings

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implementasi kontrak [EngineFeature] untuk tab Settings. */
class SettingsFeature : EngineFeature {

    override val id: EngineId = EngineId.SETTINGS

    // Settings tidak punya proses latar belakang; selalu tersedia.
    private val mutableStatus = MutableStateFlow(EngineStatus.RUNNING)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    override fun createFragment(): Fragment = SettingsFragment()
}
