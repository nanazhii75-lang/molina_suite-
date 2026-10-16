package com.molina.suite.feature.mpv

import android.app.Activity
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.mpv.X11Opener

/** Menjembatani tombol X11 di home mpv ke peluncur X11 lewat registri engine. */
internal class MpvX11Opener(private val engines: EngineRegistry) : X11Opener {

    override fun open(host: Activity): Boolean {
        val launcher = engines.findLauncher(EngineId.X11) ?: return false
        launcher.launch(host)
        return true
    }
}
