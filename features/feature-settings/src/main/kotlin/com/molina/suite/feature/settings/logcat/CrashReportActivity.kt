package com.molina.suite.feature.settings.logcat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.molina.suite.feature.settings.R
import java.io.File

/**
 * Layar laporan crash yang berdiri sendiri: hanya membaca berkas laporan, tidak menyentuh mesin
 * mpv, Terminal, atau Code, sehingga tetap terbuka walaupun layar utama crash saat dibuka. Isinya
 * bisa diseleksi, disalin, dibagikan, atau disimpan ke /sdcard/Molina/crash.
 */
class CrashReportActivity : AppCompatActivity() {

    private var report: File? = null
    private var content: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)
        if (!load(intent)) {
            Toast.makeText(applicationContext, R.string.crash_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        bind()
        if (savedInstanceState == null && intent.getStringExtra(EXTRA_COMMAND) == COMMAND_SHARE) {
            intent.removeExtra(EXTRA_COMMAND)
            share()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** Membaca laporan yang diminta (atau yang terbaru) dan menandainya sudah dibuka; false bila tidak ada. */
    private fun load(source: Intent): Boolean {
        val file = CrashFiles.resolve(applicationContext, source.getStringExtra(EXTRA_FILE)) ?: return false
        val text = CrashLogStore.read(file) ?: return false
        report = file
        content = text
        ProcessTracking.crashNotice(applicationContext).markSeen()
        return true
    }

    private fun bind() {
        val file = report ?: return
        findViewById<TextView>(R.id.crash_report_name).text = file.name
        findViewById<TextView>(R.id.crash_report_body).text = content
        findViewById<View>(R.id.crash_report_copy).setOnClickListener { copy() }
        findViewById<View>(R.id.crash_report_share).setOnClickListener { share() }
        findViewById<View>(R.id.crash_report_save).setOnClickListener { save() }
        findViewById<View>(R.id.crash_report_close).setOnClickListener { finish() }
    }

    private fun copy() {
        val message = if (CrashActions.copy(applicationContext, content)) R.string.crash_copied else R.string.logcat_copy_failed
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun share() {
        val file = report ?: return
        startActivity(CrashActions.shareIntent(this, file.name, content))
    }

    private fun save() {
        val file = report ?: return
        val message = when (val result = CrashExporter.exportToShared(file)) {
            is CrashExporter.Result.Saved -> getString(R.string.crash_saved, result.file.absolutePath)
            is CrashExporter.Result.Failed -> getString(R.string.crash_save_failed, result.reason)
        }
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val COMMAND_SHARE = "share"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_COMMAND = "command"

        /** Intent untuk membuka laporan [report]; [command] opsional (mis. [COMMAND_SHARE]). */
        fun intent(context: Context, report: File, command: String?): Intent {
            val intent = Intent(context, CrashReportActivity::class.java)
                .putExtra(EXTRA_FILE, report.absolutePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (command != null) intent.putExtra(EXTRA_COMMAND, command)
            return intent
        }
    }
}
