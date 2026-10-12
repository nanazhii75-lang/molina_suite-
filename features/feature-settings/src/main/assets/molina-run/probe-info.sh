#!/bin/bash
# Info Video untuk Pusat Kontrol molina-suite.
# Pemakaian: probe-info.sh <berkas-status> <berkas-media>
# Terminal menampilkan keluaran ffprobe apa adanya (versi teknis). Berkas status berisi baris
# key=value yang ditulis atomik dan dibaca layar Pusat Kontrol untuk menampilkan kartu info.

S="$1"
F="$2"
if [ -z "$S" ] || [ -z "$F" ]; then
    echo "Pemakaian: probe-info.sh <berkas-status> <berkas-media>" >&2
    exit 2
fi

ST_STATE=running
ST_STAGE=prepare
ST_PROGRESS=0
ST_ERROR=
R_FILE=
R_EXT=
R_DURATION=-
R_SIZE=-
R_BITRATE=-
R_HAS_VIDEO=
R_HAS_AUDIO=
V_CODEC=-
V_WIDTH=-
V_HEIGHT=-
V_FPS=-
A_CODEC=-
A_CHANNELS=-
A_RATE=-
A_BITRATE=-

mkdir -p "$(dirname "$S")" 2>/dev/null

# Menulis seluruh status sekaligus lewat berkas sementara lalu rename, supaya pembaca tidak pernah
# melihat berkas setengah jadi. Gagal menulis tidak menghentikan pembacaan.
emit() {
    {
        printf 'state=%s\n' "$ST_STATE"
        printf 'stage=%s\n' "$ST_STAGE"
        printf 'progress=%s\n' "$ST_PROGRESS"
        printf 'error=%s\n' "$ST_ERROR"
        printf 'file=%s\n' "$R_FILE"
        printf 'ext=%s\n' "$R_EXT"
        printf 'duration=%s\n' "$R_DURATION"
        printf 'size_bytes=%s\n' "$R_SIZE"
        printf 'bitrate_kbps=%s\n' "$R_BITRATE"
        printf 'has_video=%s\n' "$R_HAS_VIDEO"
        printf 'has_audio=%s\n' "$R_HAS_AUDIO"
        printf 'v_codec=%s\n' "$V_CODEC"
        printf 'v_width=%s\n' "$V_WIDTH"
        printf 'v_height=%s\n' "$V_HEIGHT"
        printf 'v_fps=%s\n' "$V_FPS"
        printf 'a_codec=%s\n' "$A_CODEC"
        printf 'a_channels=%s\n' "$A_CHANNELS"
        printf 'a_rate=%s\n' "$A_RATE"
        printf 'a_bitrate_kbps=%s\n' "$A_BITRATE"
    } 2>/dev/null > "$S.tmp" && mv -f "$S.tmp" "$S" 2>/dev/null
    return 0
}

fail() {
    ST_STATE=failed
    ST_ERROR="$1"
    emit
    echo "GAGAL: $1" >&2
    exit 1
}

# Ctrl+C di Terminal tercermin di layar Pusat Kontrol sebagai gagal.
trap 'ST_STATE=failed; ST_ERROR="Dibatalkan"; emit; exit 130' INT TERM

# Nama berkas tampil di layar: buang karakter kontrol agar tetap satu baris.
R_FILE=$(basename -- "$F" | tr -d '\000-\037')
R_EXT=$(printf '%s' "${F##*.}" | tr -d '\000-\037=' | tr '[:lower:]' '[:upper:]' | cut -c1-8)

emit

if ! command -v ffprobe >/dev/null 2>&1; then
    ST_STAGE=install
    emit
    if ! { apt update && apt install -y ffmpeg; }; then
        fail "Gagal memasang ffmpeg"
    fi
fi

date '+Info Video  %d/%m/%Y %H:%M'
echo "Berkas  $F"

ST_STAGE=probe
ST_PROGRESS=20
emit

[ -f "$F" ] || fail "Berkas tidak ditemukan"

# Ambil nilai satu kunci dari keluaran ffprobe berbentuk key=value.
val() {
    printf '%s\n' "$1" | awk -F= -v k="$2" '$1==k{sub(/^[^=]*=/,""); print; exit}'
}
# Isi hanya bila ada angka/teks bermakna; ffprobe memakai N/A untuk yang tidak diketahui.
clean() {
    case "$1" in
        ""|N/A) printf '%s' "-" ;;
        *) printf '%s' "$1" ;;
    esac
}

FMT=$(ffprobe -v error -show_entries format=duration,size,bit_rate -of default=noprint_wrappers=1 -i "$F" 2>/dev/null)
if [ -z "$FMT" ]; then
    fail "Berkas tidak bisa dibaca sebagai media"
fi
ST_PROGRESS=45
emit

VID=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,r_frame_rate,avg_frame_rate -of default=noprint_wrappers=1 -i "$F" 2>/dev/null)
ST_PROGRESS=65
emit
AUD=$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name,channels,sample_rate,bit_rate -of default=noprint_wrappers=1 -i "$F" 2>/dev/null)
ST_PROGRESS=85

R_DURATION=$(clean "$(val "$FMT" duration)")
R_SIZE=$(clean "$(val "$FMT" size)")
BR=$(val "$FMT" bit_rate)
R_BITRATE=$(printf '%s' "$BR" | awk '/^[0-9]+$/{printf "%.0f", $1/1000; ok=1} END{if(!ok)printf "-"}')

if [ -n "$VID" ]; then
    R_HAS_VIDEO=1
    V_CODEC=$(clean "$(val "$VID" codec_name)")
    V_WIDTH=$(clean "$(val "$VID" width)")
    V_HEIGHT=$(clean "$(val "$VID" height)")
    FR=$(val "$VID" avg_frame_rate)
    case "$FR" in ""|0/0|N/A) FR=$(val "$VID" r_frame_rate) ;; esac
    V_FPS=$(printf '%s' "$FR" | awk -F/ 'NF==2&&$2>0&&$1>0{v=$1/$2; if(v==int(v))printf "%d",v; else printf "%.2f",v; ok=1} END{if(!ok)printf "-"}')
else
    R_HAS_VIDEO=0
fi

if [ -n "$AUD" ]; then
    R_HAS_AUDIO=1
    A_CODEC=$(clean "$(val "$AUD" codec_name)")
    A_CHANNELS=$(clean "$(val "$AUD" channels)")
    A_RATE=$(clean "$(val "$AUD" sample_rate)")
    A_BITRATE=$(printf '%s' "$(val "$AUD" bit_rate)" | awk '/^[0-9]+$/{printf "%.0f", $1/1000; ok=1} END{if(!ok)printf "-"}')
else
    R_HAS_AUDIO=0
fi

if [ "$R_HAS_VIDEO" = 0 ] && [ "$R_HAS_AUDIO" = 0 ]; then
    fail "Berkas tidak berisi video maupun audio"
fi

# Versi teknis tetap tampil lengkap di Terminal.
echo
ffprobe -hide_banner -i "$F"
echo

ST_STATE=done
ST_STAGE=done
ST_PROGRESS=100
emit
