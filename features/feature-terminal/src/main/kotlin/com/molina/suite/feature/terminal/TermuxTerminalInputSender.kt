package com.molina.suite.feature.terminal

import com.molina.suite.core.common.TerminalInputResult
import com.molina.suite.core.common.TerminalInputSender
import java.lang.ref.WeakReference

/** Menyimpan referensi lemah ke tab Terminal yang sedang hidup. Hanya diakses dari main thread. */
internal object TerminalTabRegistry {
    private var reference: WeakReference<TerminalTabFragment>? = null

    fun attach(fragment: TerminalTabFragment) {
        reference = WeakReference(fragment)
    }

    fun detach(fragment: TerminalTabFragment) {
        if (reference?.get() === fragment) reference = null
    }

    fun current(): TerminalTabFragment? = reference?.get()
}

/** Pengirim teks ke sesi aktif tab Terminal. Panggil dari main thread. */
class TermuxTerminalInputSender : TerminalInputSender {

    override fun sendLine(line: String): TerminalInputResult {
        if (line.isEmpty()) return TerminalInputResult.Rejected("Baris kosong")
        if (line.any { it == '\n' || it == '\r' }) {
            return TerminalInputResult.Rejected("Teks harus satu baris")
        }
        val tab = TerminalTabRegistry.current()
            ?: return TerminalInputResult.Rejected("Tab Terminal belum dibuka")
        val reason = tab.writeLineToActiveSession(line)
        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)
    }

    override fun sendLineWhenReady(line: String): TerminalInputResult {
        if (line.isEmpty()) return TerminalInputResult.Rejected("Baris kosong")
        if (line.any { it == '\n' || it == '\r' }) {
            return TerminalInputResult.Rejected("Teks harus satu baris")
        }
        val tab = TerminalTabRegistry.current()
            ?: return TerminalInputResult.Rejected("Tab Terminal belum dibuka")
        val reason = tab.writeLineToActiveSessionWhenReady(line)
        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)
    }

    override fun sendLineInWorkdir(workdir: String, label: String, line: String): TerminalInputResult {
        if (line.isEmpty()) return TerminalInputResult.Rejected("Baris kosong")
        if (line.any { it == '\n' || it == '\r' }) {
            return TerminalInputResult.Rejected("Teks harus satu baris")
        }
        if (workdir.isEmpty() || workdir.any { it == '\n' || it == '\r' }) {
            return TerminalInputResult.Rejected("Folder kerja tidak valid")
        }
        val tab = TerminalTabRegistry.current()
            ?: return TerminalInputResult.Rejected("Tab Terminal belum dibuka")
        val reason = tab.writeLineInWorkdir(workdir, label, line)
        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)
    }
}
