package com.molina.suite.feature.code

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementasi kontrak [EngineFeature] untuk tab Code. Editor berjalan di dalam
 * proses aplikasi tanpa daemon, jadi statusnya selalu siap.
 */
internal class CodeFeature : EngineFeature {

    override val id: EngineId = EngineId.CODE

    override val status: StateFlow<EngineStatus> =
        MutableStateFlow(EngineStatus.RUNNING).asStateFlow()

    override fun createFragment(): Fragment = CodeTabFragment()
}
