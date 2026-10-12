package com.molina.suite.core.common

import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.StateFlow

/**
 * Kontrak tunggal antara shell launcher dan tiap engine. Shell hanya mengenal
 * interface ini sehingga tidak ada dependensi langsung ke kode engine.
 */
interface EngineFeature {
    val id: EngineId
    val status: StateFlow<EngineStatus>
    fun createFragment(): Fragment
}
