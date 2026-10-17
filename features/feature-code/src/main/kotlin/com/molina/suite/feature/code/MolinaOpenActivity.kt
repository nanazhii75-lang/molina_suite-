package com.molina.suite.feature.code

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostIntents
import com.molina.suite.core.common.IntegrationRoute
import com.molina.suite.core.common.IntegrationSettings

/**
 * Pintu masuk privat untuk perintah Terminal molina-open. Komponen ini tidak diekspor: hanya
 * proses dengan UID aplikasi (shell Molina Terminal lewat `am start`) yang bisa memanggilnya,
 * aplikasi lain tidak. Tanpa tampilan; selesai di onCreate.
 */
class MolinaOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "perintah Terminal gagal: ${intent?.action}", e)
            toast(R.string.molina_open_failed)
        } finally {
            finish()
        }
    }

    private fun handle(command: Intent?) {
        if (command?.action != ACTION_OPEN) {
            Log.w(TAG, "aksi tidak dikenal: ${command?.action}")
            toast(R.string.molina_open_unknown)
            return
        }
        if (!IntegrationSettings(this).isEnabled(IntegrationRoute.TERMINAL_TO_CODE)) {
            toast(R.string.molina_open_disabled)
            return
        }
        val file = OpenInput.fileTarget(command.getStringExtra(EXTRA_TARGET))
        if (file == null) {
            toast(R.string.molina_open_invalid_target)
            return
        }
        CodeEngineModule.requireEngine().editorOpen.request(file.path)
        if (!showCodeTab()) toast(R.string.molina_open_no_suite)
    }

    /** Membawa shell ke depan dan meminta tab Code; engine Code tidak mengenal kelas shell. */
    private fun showCodeTab(): Boolean {
        val launcher = packageManager.getLaunchIntentForPackage(packageName)?.component ?: return false
        val intent = Intent()
            .setComponent(launcher)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(HostIntents.EXTRA_SHOW_ENGINE, EngineId.CODE.name)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "shell tidak bisa dibuka", e)
            false
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MolinaOpen"

        /** Harus sama dengan aset molina-open. */
        const val ACTION_OPEN = "com.molina.suite.code.action.OPEN"
        const val EXTRA_TARGET = "target"
    }
}
