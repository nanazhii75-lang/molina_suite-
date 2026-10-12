package com.molina.suite.feature.mpv

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.mpv.MainScreenFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementasi kontrak [EngineFeature] untuk tab mpv. Library native diperiksa
 * di thread latar saat startup: RUNNING bila siap, UNAVAILABLE bila tidak ter-bundel.
 */
internal class MpvFeature : EngineFeature {

    override val id: EngineId = EngineId.MPV

    private val mutableStatus = MutableStateFlow(EngineStatus.STARTING)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    init {
        Thread({
            mutableStatus.value =
                if (MpvNativeProbe.isReady()) EngineStatus.RUNNING else EngineStatus.UNAVAILABLE
        }, "mpv-native-probe").start()
    }

    override fun createFragment(): Fragment =
        if (MpvNativeProbe.isReady()) MainScreenFragment() else MpvUnavailableFragment()
}
