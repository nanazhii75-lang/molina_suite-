package com.molina.suite.feature.x11

import android.app.Application
import com.molina.suite.core.common.BackgroundKeepAlive
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.ShellCommandRunner
import com.molina.suite.core.common.X11AutoStopPrefs
import com.molina.suite.core.common.X11Controller

/**
 * Satu-satunya titik masuk shell ke engine X11: mendaftarkan peluncur ke
 * [EngineRegistry] dan menyalakan notifikasi status. Shell tidak mengenal kelas lorie.
 */
object X11EngineModule {

    fun install(
        application: Application,
        engines: EngineRegistry,
        shell: ShellCommandRunner,
        keepAlive: BackgroundKeepAlive,
        controller: X11Controller
    ) {
        val launcher = X11Launcher(application, shell, keepAlive)
        engines.registerLauncher(launcher)
        X11StopHandler.controller = controller
        X11StatusNotifier(application, launcher, controller).start()
        X11IdleWatcher(application, launcher, controller, X11AutoStopPrefs(application)).start()
    }
}
