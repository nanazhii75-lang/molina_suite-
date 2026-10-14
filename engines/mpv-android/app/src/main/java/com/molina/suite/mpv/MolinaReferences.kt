package com.molina.suite.mpv

import android.content.Intent

/**
 * Kunci referensi playlist untuk siaran dari daftar channel.
 *
 * Server siaran bisa membalas dengan halaman HTML yang bertipe MIME playlist; mpv lalu menganggap
 * tiap baris HTML sebagai item playlist (muncul sebagai "<!DOCTYPE html>" di panel). Setelah
 * channel pertama termuat, pemutar mematikan access-references sehingga isi dari jaringan tidak
 * bisa lagi memperluas playlist. Pemanggil meminta lewat ekstra [EXTRA_LOCK_REFERENCES]; engine
 * tidak mengenal siapa pemanggilnya.
 */
internal object MolinaReferences {

    const val EXTRA_LOCK_REFERENCES = "molina_lock_references"

    fun requested(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_LOCK_REFERENCES, false) == true
}
