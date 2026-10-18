package com.molina.suite.mpv.tv

data class TvChannel(val name: String, val url: String)

data class TvGroup(val title: String, val channels: List<TvChannel>)

object TvCatalog {
    val groups: List<TvGroup> = listOf(
        TvGroup("EMTEK", listOf(
            TvChannel("SCTV", "https://www.vidio.com/live/sctv"),
            TvChannel("INDOSIAR", "https://www.vidio.com/live/indosiar"),
            TvChannel("VIDIO", "https://www.vidio.com/live")
        )),
        TvGroup("MNC", listOf(
            TvChannel("RCTI+ (RCTI, MNCTV, GTV, INEWS)", "https://www.rctiplus.com")
        )),
        TvGroup("TRANS", listOf(
            TvChannel("TRANS TV", "https://www.transtv.co.id/live"),
            TvChannel("TRANS7", "https://www.trans7.co.id/live")
        ))
    )
}
