package com.molina.suite.feature.mpv

import android.app.Application
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.mpv.MpvActivityReporter
import com.molina.suite.mpv.UrlPlayback
import com.molina.suite.mpv.search.YoutubeSearch

/**
 * Satu-satunya titik masuk shell ke modul mpv: memasang jembatan ytdl lalu
 * mendaftarkan fitur ke [EngineRegistry]. Shell tidak mengenal kelas pemutar.
 */
object MpvEngineModule {

    fun install(
        application: Application,
        engines: EngineRegistry,
        toolReadiness: ToolReadinessChecker,
        toolSetup: ToolSetupBoard
    ) {
        MpvYtdlBridge.install(application)
        YoutubeSearch.install(YtdlpSearchSource(MpvYtdlBridge.wrapperFile(application)))
        MpvActivityReporter.install(MpvActivityLogger())
        UrlPlayback.install(MpvToolGate(toolReadiness, toolSetup))
        engines.register(MpvFeature())
    }
}
