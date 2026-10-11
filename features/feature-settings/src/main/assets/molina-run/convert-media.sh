#!/bin/bash
# Konverter Media untuk Pusat Kontrol molina-suite.
# Pemakaian: convert-media.sh <berkas-status> <berkas-sumber> <berkas-hasil> "<argumen-ffmpeg>"
# Terminal menampilkan keluaran ffmpeg apa adanya (versi teknis). Berkas status berisi baris
# key=value yang ditulis atomik dan dibaca layar Pusat Kontrol untuk progres, kecepatan, ukuran,
# dan perkiraan sisa waktu. Argumen ffmpeg dipecah per spasi tanpa eval dan tanpa globbing.

S="$1"
IN="$2"
OUT="$3"
ARGS_TEXT="$4"
if [ -z "$S" ] || [ -z "$IN" ] || [ -z "$OUT" ] || [ -z "$ARGS_TEXT" ]; then
    echo "Pemakaian: convert-media.sh <berkas-status> <berkas-sumber> <berkas-hasil> \"<argumen-ffmpeg>\"" >&2
    exit 2
fi

read -r -a FF_ARGS <<< "$ARGS_TEXT"

ST_STATE=running
ST_STAGE=prepare
ST_PROGRESS=0
ST_ERROR=
R_SRC=
R_OUT=
R_FMT=
R_TIME=-
R_DURATION=-
R_SPEED=-
R_SIZE=-
R_ETA=-
FFPID=
CREATED=0
LOG="$S.ffprogress"

mkdir -p "$(dirname "$S")" 2>/dev/null

# Menulis seluruh status sekaligus lewat berkas sementara lalu rename, supaya pembaca tidak pernah
# melihat berkas setengah jadi. Gagal menulis tidak menghentikan konversi.
emit() {
    {
        printf 'state=%s\n' "$ST_STATE"
        printf 'stage=%s\n' "$ST_STAGE"
        printf 'progress=%s\n' "$ST_PROGRESS"
        printf 'error=%s\n' "$ST_ERROR"
        printf 'src=%s\n' "$R_SRC"
        printf 'out=%s\n' "$R_OUT"
        printf 'out_path=%s\n' "$OUT_SAFE"
        printf 'fmt=%s\n' "$R_FMT"
        printf 'time_sec=%s\n' "$R_TIME"
        printf 'duration=%s\n' "$R_DURATION"
        printf 'speed=%s\n' "$R_SPEED"
        printf 'size_bytes=%s\n' "$R_SIZE"
        printf 'eta_sec=%s\n' "$R_ETA"
    } 2>/dev/null > "$S.tmp" && mv -f "$S.tmp" "$S" 2>/dev/null
    return 0
}

# Berkas hasil dihapus hanya bila skrip ini yang membuatnya (ffmpeg dijalankan dengan -n).
discard_output() {
    rm -f -- "$LOG" 2>/dev/null
    if [ "$CREATED" = 1 ]; then rm -f -- "$OUT" 2>/dev/null; fi
}

fail() {
    ST_STATE=failed
    ST_ERROR="$1"
    ST_ETA=-
    discard_output
    emit
    echo "GAGAL: $1" >&2
    exit 1
}

# Ctrl+C di Terminal menghentikan ffmpeg dan tercermin di layar Pusat Kontrol sebagai gagal.
cancel() {
    if [ -n "$FFPID" ]; then kill "$FFPID" 2>/dev/null; wait "$FFPID" 2>/dev/null; fi
    ST_STATE=failed
    ST_ERROR="Dibatalkan"
    R_ETA=-
    discard_output
    emit
    exit 130
}
trap cancel INT TERM

clean_text() { printf '%s' "$1" | tr -d '\000-\037=' ; }
R_SRC=$(clean_text "$(basename -- "$IN")")
R_OUT=$(clean_text "$(basename -- "$OUT")")
OUT_SAFE=$(clean_text "$OUT")
R_FMT=$(printf '%s' "${OUT##*.}" | tr -d '\000-\037=' | tr '[:lower:]' '[:upper:]' | cut -c1-8)

emit

if ! command -v ffmpeg >/dev/null 2>&1 || ! command -v ffprobe >/dev/null 2>&1; then
    ST_STAGE=install
    emit
    if ! { apt update && apt install -y ffmpeg; }; then
        fail "Gagal memasang ffmpeg"
    fi
fi

date '+Konverter Media  %d/%m/%Y %H:%M'
echo "Sumber  $IN"
echo "Hasil   $OUT"

[ -f "$IN" ] || fail "Berkas sumber tidak ditemukan"
[ -e "$OUT" ] && fail "Berkas hasil sudah ada"
mkdir -p -- "$(dirname -- "$OUT")" 2>/dev/null || fail "Folder hasil tidak bisa dibuat"

# Durasi sumber dipakai untuk menghitung persen dan sisa waktu.
DUR=$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 -i "$IN" 2>/dev/null | head -n 1)
R_DURATION=$(printf '%s' "$DUR" | awk '$1+0>0{printf "%.3f",$1; ok=1} END{if(!ok)printf "-"}')

: > "$LOG" 2>/dev/null
ST_STAGE=convert
emit

CREATED=1
ffmpeg -hide_banner -nostdin -n -progress "$LOG" -i "$IN" "${FF_ARGS[@]}" "$OUT" &
FFPID=$!

# Isi nilai terakhir satu kunci dari blok progres ffmpeg.
val() {
    printf '%s\n' "$1" | awk -F= -v k="$2" '$1==k{v=substr($0,length(k)+2)} END{print v}'
}

while kill -0 "$FFPID" 2>/dev/null; do
    BLOCK=$(tail -n 24 "$LOG" 2>/dev/null)
    US=$(val "$BLOCK" out_time_us)
    SP=$(val "$BLOCK" speed)
    SZ=$(val "$BLOCK" total_size)
    R_TIME=$(printf '%s' "$US" | awk '/^[0-9]+$/{printf "%.1f",$1/1000000; ok=1} END{if(!ok)printf "-"}')
    R_SPEED=$(printf '%s' "$SP" | awk '{gsub(/x/,""); gsub(/ /,"")} $1+0>0{printf "%.2f",$1; ok=1} END{if(!ok)printf "-"}')
    R_SIZE=$(printf '%s' "$SZ" | awk '/^[0-9]+$/{printf "%d",$1; ok=1} END{if(!ok)printf "-"}')
    ST_PROGRESS=$(awk -v t="$R_TIME" -v d="$R_DURATION" 'BEGIN{p=0; if(t+0>0&&d+0>0){p=int(t/d*100)}; if(p>99)p=99; if(p<0)p=0; print p}')
    R_ETA=$(awk -v t="$R_TIME" -v d="$R_DURATION" -v s="$R_SPEED" 'BEGIN{if(t+0>0&&d+0>t+0&&s+0>0)printf "%d",(d-t)/s; else printf "-"}')
    emit
    sleep 0.5
done

wait "$FFPID"
RC=$?
FFPID=
rm -f -- "$LOG" 2>/dev/null

if [ "$RC" -ne 0 ]; then
    fail "Konversi gagal (kode $RC). Buka Terminal untuk melihat penyebabnya"
fi
if [ ! -s "$OUT" ]; then
    fail "Berkas hasil kosong"
fi

R_SIZE=$(stat -c %s -- "$OUT" 2>/dev/null || echo -)
R_TIME=$R_DURATION
R_ETA=0
ST_STATE=done
ST_STAGE=done
ST_PROGRESS=100
emit
echo "Selesai  $OUT"
