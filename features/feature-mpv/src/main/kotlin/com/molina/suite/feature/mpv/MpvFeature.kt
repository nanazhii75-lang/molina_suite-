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
 * Implementasi kontrak [EngineFeature] untuk tab mpv. Layar utama bawaan mpv
 * dipakai apa adanya; bila library native belum tersedia, tab menampilkan
 * [MpvUnavailableFragment] agar aplikasi tidak crash saat pemutar dibuka.
 */
internal class MpvFeature : EngineFeature {

    override val id: EngineId = EngineId.MPV

    private val mutableStatus = MutableStateFlow(EngineStatus.STOPPED)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    override fun createFragment(): Fragment {
        val ready = MpvNativeProbe.isReady()
        mutableStatus.value = if (ready) EngineStatus.RUNNING else EngineStatus.UNAVAILABLE
        return if (ready) MainScreenFragment() else MpvUnavailableFragment()
    }
}
