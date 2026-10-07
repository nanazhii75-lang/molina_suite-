package com.molina.suite

import android.app.Application
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineRegistry

class MolinaApplication : Application() {
    val engines = EngineRegistry()
    val daemons = DaemonStatusBoard()
}
