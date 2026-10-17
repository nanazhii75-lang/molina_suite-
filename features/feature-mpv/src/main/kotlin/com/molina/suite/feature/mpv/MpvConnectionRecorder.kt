package com.molina.suite.feature.mpv

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger
import com.molina.suite.mpv.MpvActivityListener
import com.molina.suite.mpv.MpvOpenKind

/**
 * Memasukkan asal (skema://host[:port]) yang dibuka mpv ke [ConnectionLedger]. Engine hanya
 * mengirim asal, bukan URL utuh, jadi yang masuk catatan tetap host dan port saja.
 */
internal class MpvConnectionRecorder(
    private val ledger: ConnectionLedger = ConnectionLedger.shared
) : MpvActivityListener {

    override fun opened(session: Long, kind: MpvOpenKind) = Unit

    override fun note(session: Long, text: String, error: Boolean) = Unit

    override fun closed(session: Long, played: Boolean, failure: String?) = Unit

    override fun reached(session: Long, origin: String) {
        ledger.record(ActivitySource.MPV, origin)
    }
}
