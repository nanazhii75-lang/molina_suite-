package com.molina.suite.feature.mpv

import android.app.Application
import com.molina.suite.core.common.EngineRegistry

/**
 * Satu-satunya titik masuk shell ke modul mpv: memasang jembatan ytdl lalu
 * mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas pemutar.
 */
object MpvEngineModule {

    fun install(application: Application, engines: EngineRegistry) {
        MpvYtdlBridge.install(application)
        engines.register(MpvFeature())
    }
}
