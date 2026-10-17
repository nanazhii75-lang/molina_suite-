package com.molina.suite.feature.mpv

import android.app.Application
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.PlaybackBoard
import com.molina.suite.core.common.TerminalInputSender
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolSetupBoard
import com.molina.suite.mpv.FormatInfoEntry
import com.molina.suite.mpv.MpvActivityReporter
import com.molina.suite.mpv.UrlPlayback
import com.molina.suite.mpv.X11Entry
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
        toolSetup: ToolSetupBoard,
        playback: PlaybackBoard,
        terminalInput: TerminalInputSender
    ) {
        MpvYtdlBridge.install(application)
        YoutubeSearch.install(
            FallbackSearchSource(
                primary = NewPipeSearchSource(),
                secondary = YtdlpSearchSource(MpvYtdlBridge.wrapperFile(application))
            )
        )
        playback.setControls(MpvPlaybackControls())
        MpvActivityReporter.install(
            CompositeMpvActivityListener(
                MpvActivityLogger(),
                MpvPlaybackPublisher(playback),
                MpvConnectionRecorder()
            )
        )
        UrlPlayback.install(MpvToolGate(toolReadiness, toolSetup))
        X11Entry.install(MpvX11Opener(engines))
        FormatInfoEntry.install(MpvFormatInfoOpener(terminalInput))
        engines.register(MpvFeature())
    }
}
