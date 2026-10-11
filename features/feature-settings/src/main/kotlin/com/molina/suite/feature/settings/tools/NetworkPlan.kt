package com.molina.suite.feature.settings.tools

/**
 * Kartu Cek Jaringan: satu baris perintah (jalan di dalam Debian) yang mengukur ping, kecepatan
 * unduh, dan kecepatan upload lewat server Cloudflare, lalu mencetak ringkasannya di Terminal.
 *
 * Ping diukur dari jabat tangan TCP (curl time_connect dikurangi waktu DNS) karena ICMP sering
 * tidak tersedia di dalam proot. Kecepatan dihitung curl sebagai rata-rata selama transfer dan
 * dicetak dalam Mbps. Tiap tahap dibatasi [MAX_SECONDS] detik supaya jaringan lambat tidak
 * menggantung dan tidak menghabiskan kuota.
 */
internal object NetworkPlan {

    /** Hanya butuh curl, dipasang oleh perintah itu sendiri bila belum ada. */
    val requiredPackages: List<String> = emptyList()

    /** Perkiraan pemakaian data maksimum dalam MB, untuk teks kartu. */
    const val MAX_DATA_MB = 35

    private const val BASE = "https://speed.cloudflare.com"
    private const val PING_SAMPLES = 5
    private const val PING_TIMEOUT_SECONDS = 5
    private const val DOWN_BYTES = 25000000
    private const val UP_BYTES = 8000000
    private const val MAX_SECONDS = 15

    private const val ENSURE_CURL =
        "command -v curl >/dev/null 2>&1 || { apt update && apt install -y curl ca-certificates; }"

    private const val HEADER = """date '+Cek Jaringan  %d/%m/%Y %H:%M'"""

    private const val PING =
        """for n in ${'$'}(seq 1 $PING_SAMPLES); do """ +
            """curl -s -o /dev/null --max-time $PING_TIMEOUT_SECONDS -w '%{time_connect} %{time_namelookup}\n' $BASE/cdn-cgi/trace; done | """ +
            """awk '{t=($1-$2)*1000; if($1>0&&t>0){n++;s+=t;if(n==1||t<lo)lo=t;if(t>hi)hi=t;if(n>1){d=t-p;if(d<0)d=-d;j+=d}p=t}} """ +
            """END{if(n)printf "Ping    %.0f ms (min %.0f, maks %.0f, jitter %.0f, %d/$PING_SAMPLES sukses)\n",s/n,lo,hi,(n>1?j/(n-1):0),n; """ +
            """else print "Ping    gagal, tidak ada koneksi ke server"}'"""

    private const val DOWNLOAD =
        """curl -s -o /dev/null --max-time $MAX_SECONDS -w '%{speed_download}\n' '$BASE/__down?bytes=$DOWN_BYTES' | """ +
            """awk '{if($1>0)printf "Unduh   %.1f Mbps\n",$1*8/1000000; else print "Unduh   gagal"}'"""

    private const val UPLOAD =
        """head -c $UP_BYTES /dev/zero | """ +
            """curl -s -o /dev/null --max-time $MAX_SECONDS -H 'Content-Type: application/octet-stream' --data-binary @- -w '%{speed_upload}\n' $BASE/__up | """ +
            """awk '{if($1>0)printf "Upload  %.1f Mbps\n",$1*8/1000000; else print "Upload  gagal"}'"""

    private const val NOTE = "echo 'Ping dari jabat tangan TCP ke Cloudflare. Unduh dan upload: rata-rata selama transfer.'"

    /** Satu baris, dijalankan di dalam Debian lewat [ToolLauncher]. */
    val command: String = listOf(ENSURE_CURL, HEADER, PING, DOWNLOAD, UPLOAD, NOTE).joinToString("; ")
}
