package com.molina.suite.feature.settings.logcat

import android.content.Context
import android.os.Build

/** Keterangan aplikasi dan perangkat untuk laporan crash dan laporan proses berhenti. */
internal object DeviceInfo {

    private const val UNKNOWN = "tidak diketahui"

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: UNKNOWN
    } catch (e: Exception) {
        UNKNOWN
    }

    fun summary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            (Build.SUPPORTED_ABIS.firstOrNull() ?: "abi tidak diketahui")
}
