package com.molina.suite.feature.x11

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import com.molina.suite.core.common.EngineLauncher
import com.molina.suite.core.common.EngineStatus
import com.molina.suite.core.common.X11AutoStopPrefs
import com.molina.suite.core.common.X11Controller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Menghentikan X11 sampai tuntas bila aplikasi berada di latar belakang selama waktu yang dipilih
 * di Setting. Latar belakang berarti tidak ada activity aplikasi (termasuk jendela X11) yang
 * terlihat. Pekerjaan panjang di desktop ikut berhenti; karena itu bawaannya mati.
 */
internal class X11IdleWatcher(
    private val application: Application,
    private val launcher: EngineLauncher,
    private val controller: X11Controller,
    private val prefs: X11AutoStopPrefs
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedActivities = 0
    private var backgroundSince: Long? = SystemClock.elapsedRealtime()

    fun start() {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                backgroundSince = null
            }

            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
                if (startedActivities == 0) backgroundSince = SystemClock.elapsedRealtime()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            launcher.status.collectLatest { status ->
                if (status == EngineStatus.RUNNING) watch()
            }
        }
    }

    private suspend fun watch() {
        while (true) {
            delay(POLL_MS)
            val minutes = prefs.minutes
            if (X11IdlePolicy.shouldStop(minutes, backgroundSince, SystemClock.elapsedRealtime())) {
                stopNow(minutes)
                return
            }
        }
    }

    private suspend fun stopNow(minutes: Int) {
        val result = try {
            controller.stopAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            null
        }
        val text = when {
            result == null -> application.getString(R.string.x11_idle_failed)
            result.isClean -> application.getString(
                R.string.x11_idle_stopped,
                minutes,
                X11StatusNotifier.formatKb(result.freedKb)
            )
            else -> application.getString(R.string.x11_idle_remaining, result.remaining.size)
        }
        Toast.makeText(application, text, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val POLL_MS = 30_000L
    }
}
