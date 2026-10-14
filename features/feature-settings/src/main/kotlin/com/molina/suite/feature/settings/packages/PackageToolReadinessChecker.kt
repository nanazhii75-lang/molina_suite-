package com.molina.suite.feature.settings.packages

import android.content.Context
import com.molina.suite.core.common.ToolReadiness
import com.molina.suite.core.common.ToolReadinessChecker
import com.molina.suite.core.common.ToolRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Memeriksa alat lewat katalog dan detektor menu Paket, sehingga hasilnya sama dengan menu itu. */
internal class PackageToolReadinessChecker(context: Context) : ToolReadinessChecker {

    private val appContext = context.applicationContext

    override suspend fun check(toolIds: List<String>): ToolReadiness = withContext(Dispatchers.IO) {
        val specs = toolIds.distinct().map { id -> id to PackageCatalog.all.firstOrNull { it.id == id } }
        val unknown = specs.filter { it.second == null }.map { it.first }
        if (unknown.isNotEmpty()) return@withContext ToolReadiness.Unregistered(unknown)

        when (val detection = PackageDetector.detect(appContext)) {
            is PackageDetection.NoDebian -> ToolReadiness.NoDebian
            is PackageDetection.Failed -> ToolReadiness.Unknown
            is PackageDetection.Ok -> {
                val missing = specs.mapNotNull { (id, spec) ->
                    if (spec != null && detection.installed[id] == false) {
                        ToolRequirement(id, appContext.getString(spec.title))
                    } else {
                        null
                    }
                }
                if (missing.isEmpty()) ToolReadiness.Ready else ToolReadiness.Missing(missing)
            }
        }
    }

    override suspend fun versions(toolIds: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        when (val detection = PackageDetector.detect(appContext)) {
            is PackageDetection.Ok -> detection.versions.filterKeys { it in toolIds }
            else -> emptyMap()
        }
    }
}
