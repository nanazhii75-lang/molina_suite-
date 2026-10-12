package com.molina.suite.feature.settings.run

/** Penilaian satu metrik; urutan enum dari terbaik ke terburuk. */
internal enum class Rating { GOOD, FAIR, POOR }

internal object NetworkQuality {
    private const val PING_GOOD_MS = 50.0
    private const val PING_FAIR_MS = 120.0
    private const val SPEED_GOOD_MBPS = 10.0
    private const val SPEED_FAIR_MBPS = 3.0

    fun ping(ms: Double): Rating = when {
        ms < PING_GOOD_MS -> Rating.GOOD
        ms < PING_FAIR_MS -> Rating.FAIR
        else -> Rating.POOR
    }

    fun speed(mbps: Double): Rating = when {
        mbps >= SPEED_GOOD_MBPS -> Rating.GOOD
        mbps >= SPEED_FAIR_MBPS -> Rating.FAIR
        else -> Rating.POOR
    }

    /** Penilaian keseluruhan = metrik terburuk; null bila tidak ada metrik. */
    fun overall(ratings: List<Rating>): Rating? = ratings.maxByOrNull { it.ordinal }
}

internal sealed interface MetricState {
    object Pending : MetricState
    object Measuring : MetricState
    data class Value(val number: Double, val rating: Rating) : MetricState
    object Failed : MetricState
}

internal data class PingDetail(val min: Int?, val max: Int?, val jitter: Int?)

/** Tiga metrik Cek Jaringan yang diturunkan dari status skrip. */
internal data class NetworkMetrics(
    val ping: MetricState,
    val pingDetail: PingDetail?,
    val download: MetricState,
    val upload: MetricState
) {
    /** Penilaian keseluruhan dari metrik yang berhasil diukur. */
    val verdict: Rating?
        get() = NetworkQuality.overall(
            listOf(ping, download, upload).mapNotNull { (it as? MetricState.Value)?.rating }
        )

    companion object {
        private val stageOrder = listOf("ping", "download", "upload")

        /**
         * [ended] true bila proses sudah selesai atau dianggap macet: metrik yang belum punya
         * nilai dianggap gagal, bukan "sedang diukur".
         */
        fun from(status: RunStatus?, ended: Boolean): NetworkMetrics {
            val ping = metric(status, 0, ended, status?.value("ping_ms")) { NetworkQuality.ping(it) }
            val detail = if (ping is MetricState.Value) {
                PingDetail(
                    status?.value("ping_min")?.toIntOrNull(),
                    status?.value("ping_max")?.toIntOrNull(),
                    status?.value("ping_jitter")?.toIntOrNull()
                )
            } else {
                null
            }
            return NetworkMetrics(
                ping,
                detail,
                metric(status, 1, ended, status?.value("down_mbps")) { NetworkQuality.speed(it) },
                metric(status, 2, ended, status?.value("up_mbps")) { NetworkQuality.speed(it) }
            )
        }

        private fun metric(
            status: RunStatus?,
            index: Int,
            ended: Boolean,
            raw: String?,
            rate: (Double) -> Rating
        ): MetricState {
            if (status == null) return MetricState.Pending
            val number = raw?.toDoubleOrNull()
            if (number != null) return MetricState.Value(number, rate(number))
            if (ended || status.state != RunState.RUNNING) return MetricState.Failed
            val current = stageOrder.indexOf(status.stage)
            return when {
                current == index -> MetricState.Measuring
                current > index -> MetricState.Failed
                else -> MetricState.Pending
            }
        }
    }
}
