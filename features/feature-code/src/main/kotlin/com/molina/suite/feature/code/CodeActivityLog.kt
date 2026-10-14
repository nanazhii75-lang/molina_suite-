package com.molina.suite.feature.code

import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityLog
import com.molina.suite.core.common.ActivitySource
import java.io.File
import java.nio.charset.Charset

/**
 * Pencatatan aktivitas tab Code. Hanya nama berkas dan hasilnya yang dicatat, tidak pernah
 * isinya. Dipisah dari [CodeFileRepository] supaya repositori tetap hanya mengurus baca-tulis.
 */

/** Membuka berkas sebagai satu aktivitas; [verb] adalah kata kerja judulnya ("Buka", "Muat ulang"). */
internal suspend fun CodeFileRepository.loadLogged(file: File, verb: String = "Buka"): CodeFileLoad =
    Activities.log.track(ActivitySource.CODE, "$verb ${file.name}") { entry ->
        load(file).also { result ->
            when (result) {
                is CodeFileLoad.Loaded -> Unit
                is CodeFileLoad.TooLarge -> entry.end(false, "terlalu besar (${result.sizeBytes} byte)")
                is CodeFileLoad.Binary -> entry.end(false, "berkas biner tidak dibuka")
                is CodeFileLoad.Failed -> entry.end(false, result.reason)
            }
        }
    }

/** Menyimpan berkas sebagai satu aktivitas. */
internal suspend fun CodeFileRepository.saveLogged(
    file: File,
    text: String,
    charset: Charset,
    withBom: Boolean
): CodeFileSave =
    Activities.log.track(ActivitySource.CODE, "Simpan ${file.name}") { entry ->
        save(file, text, charset, withBom).also { result ->
            if (result is CodeFileSave.Failed) entry.end(false, result.reason)
        }
    }

/** Mencatat hasil mengirim perintah jalankan ke Terminal; hasil yang sukses diakhiri oleh pemanggilnya. */
internal fun ActivityLog.Activity.report(outcome: CodeRunOutcome) {
    when (outcome) {
        CodeRunOutcome.Started -> note("perintah dikirim ke Terminal")
        CodeRunOutcome.Unsupported -> end(false, "jenis berkas tidak bisa dijalankan")
        CodeRunOutcome.NoSwitcher -> end(false, "tab Terminal tidak tersedia")
        is CodeRunOutcome.Rejected -> end(false, outcome.reason)
    }
}
