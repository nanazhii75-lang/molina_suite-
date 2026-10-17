package com.molina.suite.feature.settings.x11

/** Pemakaian memori fisik (RSS, perkiraan) desktop dan server X11. */
internal data class X11Usage(
    val desktopCount: Int,
    val desktopKb: Long,
    val serverCount: Int,
    val serverKb: Long
) {
    val totalKb: Long get() = desktopKb + serverKb
    val isIdle: Boolean get() = desktopCount == 0 && serverCount == 0

    companion object {
        val NONE = X11Usage(0, 0L, 0, 0L)

        fun read(
            table: List<ProcEntry>,
            rootPid: Int?,
            selfPid: Int,
            rssKb: (Int) -> Long
        ): X11Usage {
            val desktop = X11Selection.desktop(table, rootPid, selfPid)
            val server = X11Selection.server(table, selfPid)
            return X11Usage(
                desktopCount = desktop.size,
                desktopKb = desktop.sumOf(rssKb),
                serverCount = server.size,
                serverKb = server.sumOf(rssKb)
            )
        }

        /** "512 KB" atau "128 MB"; tanpa pengaruh locale. */
        fun format(kb: Long): String =
            if (kb >= 1024L) "${kb / 1024L} MB" else "$kb KB"
    }
}
