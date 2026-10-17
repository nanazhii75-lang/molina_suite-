package com.molina.suite.feature.mpv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostIntents
import com.molina.suite.mpv.MPVActivity
import com.molina.suite.mpv.MpvOpenKind
import com.molina.suite.mpv.MpvSearchEntry
import com.molina.suite.mpv.MpvSessionControl

/**
 * Pintu masuk privat untuk perintah Terminal molina-play dan molina-cari. Komponen ini tidak
 * diekspor: hanya proses dengan UID aplikasi (shell Molina Terminal lewat `am start`) yang bisa
 * memanggilnya, aplikasi lain tidak. Tanpa tampilan; selesai di onCreate.
 */
class MolinaCommandActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handle(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "perintah Terminal gagal: ${intent?.action}", e)
            toast(R.string.molina_cmd_failed)
        } finally {
            finish()
        }
    }

    private fun handle(command: Intent?) {
        when (command?.action) {
            ACTION_PLAY -> play(command.getStringExtra(EXTRA_TARGET))
            ACTION_SEARCH -> search(command.getStringExtra(EXTRA_QUERY))
            else -> {
                Log.w(TAG, "aksi tidak dikenal: ${command?.action}")
                toast(R.string.molina_cmd_unknown)
            }
        }
    }

    private fun play(raw: String?) {
        val target = CommandInput.playTarget(raw)
        if (target == null) {
            toast(R.string.molina_cmd_invalid_target)
            return
        }
        val kind = if (target.source == PlaySource.URL) MpvOpenKind.URL else MpvOpenKind.LOCAL
        val player = Intent()
            .putExtra("filepath", target.value)
            .putExtra(MpvOpenKind.EXTRA, kind.name)
            .setClass(this, MPVActivity::class.java)
        startActivity(player)
    }

    private fun search(raw: String?) {
        val query = CommandInput.searchQuery(raw)
        if (query == null) {
            toast(R.string.molina_cmd_empty_query)
            return
        }
        MpvSearchEntry.request(query)
        val extras = Bundle().apply { putString(HostIntents.EXTRA_SHOW_ENGINE, EngineId.MPV.name) }
        if (!MpvSessionControl.showSuite(this, extras)) toast(R.string.molina_cmd_no_suite)
    }

    private fun toast(resId: Int) {
        Toast.makeText(applicationContext, resId, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MolinaCommand"

        /** Harus sama dengan aset molina-play dan molina-cari. */
        const val ACTION_PLAY = "com.molina.suite.mpv.action.PLAY"
        const val ACTION_SEARCH = "com.molina.suite.mpv.action.SEARCH"
        const val EXTRA_TARGET = "target"
        const val EXTRA_QUERY = "query"
    }
}
