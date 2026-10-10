package com.molina.suite.feature.mpv

import com.molina.suite.core.common.EngineRegistry

/**
 * Satu-satunya titik masuk shell ke modul mpv: mendaftarkan fitur ke
 * [EngineRegistry]. Shell tidak mengenal kelas pemutar maupun engine mpv.
 */
object MpvEngineModule {

    fun install(engines: EngineRegistry) {
        engines.register(MpvFeature())
    }
}
