package com.molina.suite.feature.settings.integration

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.molina.suite.core.common.EngineRegistry
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.core.common.IntegrationRoute
import java.io.File

/**
 * Memeriksa kesiapan sebuah [IntegrationRoute] tanpa menjalankan apa pun: engine terdaftar,
 * perintah Terminal terpasang dan bisa dieksekusi, dan komponen privat ada di manifest. Berjalan
 * di thread latar belakang karena menyentuh disk dan PackageManager.
 */
internal class IntegrationProbe(context: Context, private val engines: EngineRegistry) {

    private val app = context.applicationContext

    class Result(
        val enginesReady: Boolean,
        val scripts: Map<String, Boolean>,
        val entries: Map<String, Boolean>
    ) {
        val scriptsReady: Boolean get() = scripts.values.all { it }
        val componentsReady: Boolean get() = entries.values.all { it }

        fun state(enabled: Boolean): IntegrationState =
            IntegrationStatus.of(enabled, enginesReady, scriptsReady, componentsReady)
    }

    fun check(route: IntegrationRoute): Result {
        val enginesReady = route.engines.all { id ->
            val status = engines.find(id)?.status?.value
            status != null && status != EngineStatus.UNAVAILABLE
        }
        val scripts = LinkedHashMap<String, Boolean>()
        for (name in route.scripts) {
            val file = File(app.filesDir, "usr/bin/$name")
            scripts[name] = file.isFile && file.canExecute()
        }
        val entries = LinkedHashMap<String, Boolean>()
        for (className in route.entryClasses) {
            entries[className] = isDeclared(className)
        }
        return Result(enginesReady, scripts, entries)
    }

    private fun isDeclared(className: String): Boolean = try {
        app.packageManager.getActivityInfo(ComponentName(app.packageName, className), 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
