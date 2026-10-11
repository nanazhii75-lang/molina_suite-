#!/bin/bash
# Cek Jaringan untuk Pusat Kontrol molina-suite.
# Pemakaian: net-check.sh <berkas-status>
# Terminal menampilkan ringkasan teks (versi teknis). Berkas status berisi baris key=value yang
# ditulis atomik dan dibaca layar Pusat Kontrol untuk menampilkan progres dan hasilnya.

S="$1"
if [ -z "$S" ]; then
    echo "Pemakaian: net-check.sh <berkas-status>" >&2
    exit 2
fi

BASE="https://speed.cloudflare.com"
PING_SAMPLES=5
DOWN_BYTES=25000000
UP_BYTES=8000000
MAX_SECONDS=15

ST_STATE=running
ST_STAGE=prepare
ST_PROGRESS=0
ST_ERROR=
P_AVG=-
P_MIN=-
P_MAX=-
P_JIT=-
P_OK=0
D_MBPS=-
U_MBPS=-

mkdir -p "$(dirname "$S")" 2>/dev/null

# Menulis seluruh status sekaligus lewat berkas sementara lalu rename, supaya pembaca tidak pernah
# melihat berkas setengah jadi. Gagal menulis tidak menghentikan pengukuran.
emit() {
    {
        printf 'state=%s\n' "$ST_STATE"
        printf 'stage=%s\n' "$ST_STAGE"
        printf 'progress=%s\n' "$ST_PROGRESS"
        printf 'error=%s\n' "$ST_ERROR"
        printf 'ping_ms=%s\n' "$P_AVG"
        printf 'ping_min=%s\n' "$P_MIN"
        printf 'ping_max=%s\n' "$P_MAX"
        printf 'ping_jitter=%s\n' "$P_JIT"
        printf 'ping_ok=%s\n' "$P_OK"
        printf 'down_mbps=%s\n' "$D_MBPS"
        printf 'up_mbps=%s\n' "$U_MBPS"
    } 2>/dev/null > "$S.tmp" && mv -f "$S.tmp" "$S" 2>/dev/null
    return 0
}

# Ctrl+C di Terminal tercermin di layar Pusat Kontrol sebagai gagal.
trap 'ST_STATE=failed; ST_ERROR="Dibatalkan"; emit; exit 130' INT TERM

emit

if ! command -v curl >/dev/null 2>&1; then
    ST_STAGE=install
    emit
    if ! { apt update && apt install -y curl ca-certificates; }; then
        ST_STATE=failed
        ST_ERROR="Gagal memasang curl"
        emit
        exit 1
    fi
fi

date '+Cek Jaringan  %d/%m/%Y %H:%M'

# Ping dari jabat tangan TCP (time_connect dikurangi waktu DNS); ICMP sering tidak ada di proot.
ST_STAGE=ping
ST_PROGRESS=5
emit
PING_OUT=$(for n in $(seq 1 "$PING_SAMPLES"); do
    curl -s -o /dev/null --max-time 5 -w '%{time_connect} %{time_namelookup}\n' "$BASE/cdn-cgi/trace"
done | awk '{t=($1-$2)*1000; if($1>0&&t>0){n++;s+=t;if(n==1||t<lo)lo=t;if(t>hi)hi=t;if(n>1){d=t-p;if(d<0)d=-d;j+=d}p=t}} END{if(n)printf "%.0f %.0f %.0f %.0f %d",s/n,lo,hi,(n>1?j/(n-1):0),n; else printf "- - - - 0"}')
PING_OUT=${PING_OUT:-"- - - - 0"}
read -r P_AVG P_MIN P_MAX P_JIT P_OK <<< "$PING_OUT"
if [ "${P_OK:-0}" -gt 0 ] 2>/dev/null; then
    printf 'Ping    %s ms (min %s, maks %s, jitter %s, %s/%s sukses)\n' "$P_AVG" "$P_MIN" "$P_MAX" "$P_JIT" "$P_OK" "$PING_SAMPLES"
else
    P_OK=0
    echo 'Ping    gagal, tidak ada koneksi ke server'
fi

ST_STAGE=download
ST_PROGRESS=30
emit
D_MBPS=$(curl -s -o /dev/null --max-time "$MAX_SECONDS" -w '%{speed_download}\n' "$BASE/__down?bytes=$DOWN_BYTES" | awk '{if($1>0)printf "%.1f",$1*8/1000000; else printf "-"}')
D_MBPS=${D_MBPS:--}
if [ "$D_MBPS" = "-" ]; then echo 'Unduh   gagal'; else echo "Unduh   $D_MBPS Mbps"; fi

ST_STAGE=upload
ST_PROGRESS=65
emit
U_MBPS=$(head -c "$UP_BYTES" /dev/zero | curl -s -o /dev/null --max-time "$MAX_SECONDS" -H 'Content-Type: application/octet-stream' --data-binary @- -w '%{speed_upload}\n' "$BASE/__up" | awk '{if($1>0)printf "%.1f",$1*8/1000000; else printf "-"}')
U_MBPS=${U_MBPS:--}
if [ "$U_MBPS" = "-" ]; then echo 'Upload  gagal'; else echo "Upload  $U_MBPS Mbps"; fi

if [ "$P_OK" -eq 0 ] && [ "$D_MBPS" = "-" ] && [ "$U_MBPS" = "-" ]; then
    ST_STATE=failed
    ST_ERROR="Tidak ada koneksi ke server"
else
    ST_STATE=done
fi
ST_STAGE=done
ST_PROGRESS=100
emit

echo 'Ping dari jabat tangan TCP ke Cloudflare. Unduh dan upload: rata-rata selama transfer.'
[ "$ST_STATE" = done ]
