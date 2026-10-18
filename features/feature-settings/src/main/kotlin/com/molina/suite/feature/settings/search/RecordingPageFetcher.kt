package com.molina.suite.feature.settings.search

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger

/**
 * Membungkus [PageFetcher] agar tiap permintaan tercatat di Catatan Koneksi. Yang disimpan hanya
 * host dan port ([ConnectionLedger] membuang jalur dan query), jadi kata kunci tidak ikut tercatat.
 */
class RecordingPageFetcher(
    private val delegate: PageFetcher,
    private val ledger: ConnectionLedger = ConnectionLedger.shared
) : PageFetcher {

    override suspend fun postForm(url: String, form: Map<String, String>): FetchResult {
        ledger.record(ActivitySource.SETTING, url)
        return delegate.postForm(url, form)
    }
}
