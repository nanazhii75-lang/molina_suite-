package com.molina.suite.feature.x11

import android.app.Application
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner

/**
 * Satu-satunya titik masuk shell ke engine X11: mendaftarkan peluncur ke
 * [EngineRegistry]. Shell tidak mengenal kelas lorie.
 */
object X11EngineModule {

    fun install(
        application: Application,
        engines: EngineRegistry,
        shell: ShellCommandRunner,
        keepAlive: BackgroundKeepAlive
    ) {
        engines.registerLauncher(X11Launcher(application, shell, keepAlive))
    }
}
