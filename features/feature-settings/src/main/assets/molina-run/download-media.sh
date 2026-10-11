#!/bin/bash
# Quick Downloader untuk Pusat Kontrol molina-suite.
# Pemakaian: download-media.sh <berkas-status> <tautan> <folder-hasil> <berkas-cookies> <node> [argumen-yt-dlp...]
# MOLINA_PREINSTALL (opsional): perintah pemasangan alat yang dijalankan lebih dulu.
# Terminal menampilkan keluaran yt-dlp apa adanya; hanya baris progres yang dirapikan jadi satu
# baris yang diperbarui di tempat. Berkas status berisi baris key=value yang ditulis atomik dan
# dibaca layar Pusat Kontrol. Argumen yt-dlp diteruskan sebagai elemen terpisah, tanpa eval.
# Berkas .part sengaja dibiarkan bila unduhan gagal atau dibatalkan, supaya bisa dilanjutkan.

S="$1"
URL="$2"
DIR="$3"
COOKIES="$4"
NODE="$5"
if [ -z "$S" ] || [ -z "$URL" ] || [ -z "$DIR" ] || [ "$#" -lt 5 ]; then
    echo "Pemakaian: download-media.sh <berkas-status> <tautan> <folder-hasil> <berkas-cookies> <node> [argumen-yt-dlp...]" >&2
    exit 2
fi
shift 5
YT_ARGS=("$@")

ENGINE=ytdlp
for a in "${YT_ARGS[@]}"; do
    if [ "$a" = "aria2c" ]; then ENGINE=aria2; fi
done

ST_STATE=running
ST_STAGE=prepare
ST_PROGRESS=0
ST_ERROR=
R_TITLE=
R_HOST=
R_OUT=
OUT_SAFE=
R_FMT=
R_DONE=-
R_TOTAL=-
R_SPEED=-
R_ETA=-
STREAM_IDX=1
STREAM_N=1
LAST_FN=
LAST_GID=
LAST_ERROR=
PCT=0
PROG_LINE=0
LAST_EMIT=0
YTPID=
HBPID=
TMPD=
CANCELLED=0

mkdir -p "$(dirname "$S")" 2>/dev/null

# Menulis seluruh status sekaligus lewat berkas sementara lalu rename, supaya pembaca tidak pernah
# melihat berkas setengah jadi. Gagal menulis tidak menghentikan unduhan.
emit() {
    {
        printf 'state=%s\n' "$ST_STATE"
        printf 'stage=%s\n' "$ST_STAGE"
        printf 'progress=%s\n' "$ST_PROGRESS"
        printf 'error=%s\n' "$ST_ERROR"
        printf 'title=%s\n' "$R_TITLE"
        printf 'host=%s\n' "$R_HOST"
        printf 'out=%s\n' "$R_OUT"
        printf 'out_path=%s\n' "$OUT_SAFE"
        printf 'fmt=%s\n' "$R_FMT"
        printf 'engine=%s\n' "$ENGINE"
        printf 'downloaded_bytes=%s\n' "$R_DONE"
        printf 'total_bytes=%s\n' "$R_TOTAL"
        printf 'speed_bps=%s\n' "$R_SPEED"
        printf 'eta_sec=%s\n' "$R_ETA"
        printf 'stream_idx=%s\n' "$STREAM_IDX"
        printf 'stream_count=%s\n' "$STREAM_N"
    } 2>/dev/null > "$S.tmp" && mv -f "$S.tmp" "$S" 2>/dev/null
    return 0
}

# Memperbarui status paling sering 2,5 kali per detik.
tick() {
    local now=${EPOCHREALTIME//[.,]/}
    [ -z "$now" ] && now=$((SECONDS * 1000000))
    if [ $((now - LAST_EMIT)) -ge 400000 ]; then
        LAST_EMIT=$now
        emit
    fi
}

clean_text() { printf '%s' "$1" | tr -d '\000-\037=' | cut -c1-"${2:-200}"; }

# Menjaga waktu ubah berkas status tetap baru selama skrip hidup, supaya layar tidak menganggap
# proses macet saat yt-dlp lama tanpa keluaran (mengurai tautan, menggabung berkas besar).
heartbeat() {
    local parent=$1
    while kill -0 "$parent" 2>/dev/null; do
        sleep 5
        [ -f "$S" ] && touch -c -- "$S" 2>/dev/null
    done
}

end_progress_line() {
    if [ "$PROG_LINE" = 1 ]; then printf '\n'; PROG_LINE=0; fi
}

cleanup() {
    if [ -n "$HBPID" ]; then kill "$HBPID" 2>/dev/null; fi
    if [ -n "$TMPD" ]; then rm -rf -- "$TMPD" 2>/dev/null; fi
}
trap cleanup EXIT

fail() {
    ST_STATE=failed
    ST_ERROR=$(clean_text "$1" 180)
    R_ETA=-
    R_SPEED=-
    end_progress_line
    emit
    echo "GAGAL: $1" >&2
    exit 1
}

stop_download() {
    if [ -n "$YTPID" ]; then
        pkill -TERM -P "$YTPID" 2>/dev/null
        kill "$YTPID" 2>/dev/null
        wait "$YTPID" 2>/dev/null
    fi
}

# Ctrl+C di Terminal menghentikan yt-dlp dan tercermin di layar Pusat Kontrol sebagai gagal.
cancel() {
    CANCELLED=1
    stop_download
    ST_STATE=failed
    ST_ERROR="Dibatalkan"
    R_ETA=-
    R_SPEED=-
    end_progress_line
    emit
    echo "Dibatalkan. Berkas sementara dibiarkan supaya unduhan bisa dilanjutkan."
    exit 130
}
trap cancel INT TERM

# Ubah teks seperti "2.7MiB" jadi byte (BYTES). Satuan tak dikenal dianggap byte.
to_bytes() {
    local n=$1 u=$2 m=1 w f=
    case $u in
        KiB) m=1024 ;;
        MiB) m=1048576 ;;
        GiB) m=1073741824 ;;
        TiB) m=1099511627776 ;;
        KB) m=1000 ;;
        MB) m=1000000 ;;
        GB) m=1000000000 ;;
    esac
    w=${n%%.*}
    if [[ $n == *.* ]]; then f=${n#*.}; fi
    f=${f}00
    f=${f:0:2}
    BYTES=$((10#${w:-0} * m + 10#$f * m / 100))
}

# Ubah teks ETA aria2 seperti "1h2m3s" jadi detik (SECS_OUT).
eta_to_sec() {
    SECS_OUT=-
    if [[ $1 =~ ^(([0-9]+)h)?(([0-9]+)m)?(([0-9]+)s)?$ ]] && [ -n "$1" ]; then
        SECS_OUT=$((10#${BASH_REMATCH[2]:-0} * 3600 + 10#${BASH_REMATCH[4]:-0} * 60 + 10#${BASH_REMATCH[6]:-0}))
    fi
}

# Ambil bilangan bulat dari nilai yt-dlp ("NA", pecahan, atau bilangan); INT_OUT kosong bila tak ada.
to_int() {
    INT_OUT=
    local v=${1%%.*}
    case $v in
        ''|*[!0-9]*) return 1 ;;
    esac
    INT_OUT=$((10#$v))
}

hs() {
    local b=$1 x u
    if [ "$b" -ge 1073741824 ]; then x=$((b * 100 / 1073741824)); u=GiB
    elif [ "$b" -ge 1048576 ]; then x=$((b * 100 / 1048576)); u=MiB
    elif [ "$b" -ge 1024 ]; then x=$((b * 100 / 1024)); u=KiB
    else HS="${b}B"; return
    fi
    printf -v HS '%d.%02d%s' $((x / 100)) $((x % 100)) "$u"
}

hms() {
    local t=$1
    if [ "$t" -ge 3600 ]; then
        printf -v HMS '%d:%02d:%02d' $((t / 3600)) $((t % 3600 / 60)) $((t % 60))
    else
        printf -v HMS '%02d:%02d' $((t / 60)) $((t % 60))
    fi
}

# Satu baris progres yang diperbarui di tempat: "[download]  45% of 12.40MiB at 1.20MiB/s ETA 00:05".
show_progress() {
    local line
    printf -v line '[download] %3d%%' "$PCT"
    if [ "$R_TOTAL" != "-" ]; then hs "$R_TOTAL"; line="$line of $HS"; fi
    if [ "$R_SPEED" != "-" ]; then hs "$R_SPEED"; line="$line at $HS/s"; fi
    if [ "$R_ETA" != "-" ]; then hms "$R_ETA"; line="$line ETA $HMS"; fi
    printf '\r\033[K%s' "$line"
    PROG_LINE=1
}

# Menghitung persen keseluruhan dari persen aliran sekarang. Video dan audio terpisah diunduh
# berurutan, jadi tiap aliran dapat bagian sama besar. Unduhan menempati 0-95%, sisanya olah akhir.
set_overall() {
    local n=$STREAM_N idx=$STREAM_IDX overall
    [ "$idx" -gt "$n" ] && n=$idx
    overall=$(((idx - 1) * 100 + PCT))
    overall=$((overall / n))
    [ "$overall" -gt 100 ] && overall=100
    ST_PROGRESS=$((overall * 95 / 100))
}

# MPROG|status|diunduh|total|perkiraan|kecepatan|eta|berkas|format|fragmen|jumlah-fragmen
handle_ytdlp_progress() {
    local status dl tot est spd eta fn fid fi fc plus total=0 p
    IFS='|' read -r status dl tot est spd eta fn fid fi fc <<< "$1"
    case $status in downloading|finished) ;; *) return ;; esac
    ST_STAGE=download
    if [ -n "$fn" ] && [ "$fn" != "$LAST_FN" ]; then
        [ -n "$LAST_FN" ] && STREAM_IDX=$((STREAM_IDX + 1))
        LAST_FN=$fn
        PCT=0
    fi
    if [ "$fid" != NA ] && [ -n "$fid" ]; then
        plus=${fid//[^+]/}
        STREAM_N=$((${#plus} + 1))
    fi
    if to_int "$dl"; then R_DONE=$INT_OUT; fi
    if to_int "$tot"; then total=$INT_OUT; elif to_int "$est"; then total=$INT_OUT; fi
    if [ "$total" -gt 0 ]; then R_TOTAL=$total; else R_TOTAL=-; fi
    if [ "$total" -gt 0 ] && [ "$R_DONE" != "-" ]; then
        p=$((R_DONE * 100 / total))
    elif to_int "$fi" && [ -n "$INT_OUT" ]; then
        p=$INT_OUT
        if to_int "$fc" && [ "$INT_OUT" -gt 0 ]; then p=$((p * 100 / INT_OUT)); else p=$PCT; fi
    else
        p=$PCT
    fi
    [ "$status" = finished ] && p=100
    [ "$p" -gt 100 ] && p=100
    PCT=$p
    if to_int "$spd"; then R_SPEED=$INT_OUT; else R_SPEED=-; fi
    if to_int "$eta"; then R_ETA=$INT_OUT; else R_ETA=-; fi
    set_overall
    show_progress
    tick
}

# aria2c mencetak "[#gid 512KiB/2.7MiB(18%) CN:1 DL:257KiB ETA:9s]"; hook progres yt-dlp hanya
# melapor saat selesai, jadi progres untuk mesin ini dibaca dari baris tersebut.
ARIA_RE='^\[#([0-9a-f]+) +([0-9.]+)([A-Za-z]*)/([0-9.]+)([A-Za-z]*)\(([0-9]+)%\)[^]]*DL:([0-9.]+)([A-Za-z]*)( ETA:([0-9hms]+))?\]'
handle_aria_progress() {
    local gid
    [[ $1 =~ $ARIA_RE ]] || return 1
    gid=${BASH_REMATCH[1]}
    ST_STAGE=download
    if [ "$gid" != "$LAST_GID" ]; then
        [ -n "$LAST_GID" ] && STREAM_IDX=$((STREAM_IDX + 1))
        LAST_GID=$gid
    fi
    to_bytes "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}"; R_DONE=$BYTES
    to_bytes "${BASH_REMATCH[4]}" "${BASH_REMATCH[5]}"
    if [ "$BYTES" -gt 0 ]; then R_TOTAL=$BYTES; else R_TOTAL=-; fi
    PCT=$((10#${BASH_REMATCH[6]}))
    to_bytes "${BASH_REMATCH[7]}" "${BASH_REMATCH[8]}"; R_SPEED=$BYTES
    eta_to_sec "${BASH_REMATCH[10]}"; R_ETA=$SECS_OUT
    set_overall
    show_progress
    tick
    return 0
}

# MPOST|status|pengolah: penggabungan, ekstraksi audio, dan sejenisnya setelah unduhan selesai.
handle_postprocess() {
    local status pp
    IFS='|' read -r status pp <<< "$1"
    # MoveFiles selalu jalan di akhir dan hampir seketika; bukan olah akhir yang perlu ditampilkan.
    if [ "$status" = started ] && [ "$pp" != MoveFiles ]; then
        ST_STAGE=process
        ST_PROGRESS=97
        R_SPEED=-
        R_ETA=-
        emit
    fi
}

# Host dari tautan, untuk kartu judul sebelum judul video diketahui.
H=${URL#*://}
H=${H%%/*}
H=${H##*@}
H=${H%%:*}
H=${H#www.}
R_HOST=$(clean_text "$H" 80)

emit
heartbeat "$$" &
HBPID=$!

if [ -n "$MOLINA_PREINSTALL" ]; then
    ST_STAGE=install
    emit
    if ! bash -c "$MOLINA_PREINSTALL"; then
        fail "Gagal memasang alat yang dibutuhkan"
    fi
fi

date '+Quick Downloader  %d/%m/%Y %H:%M'
echo "Tautan  $URL"
echo "Folder  $DIR"

command -v yt-dlp >/dev/null 2>&1 || fail "yt-dlp belum terpasang. Pasang lewat menu Paket"
command -v ffmpeg >/dev/null 2>&1 || fail "ffmpeg belum terpasang. Pasang lewat menu Paket"
if [ "$ENGINE" = aria2 ]; then
    command -v aria2c >/dev/null 2>&1 || fail "aria2 belum terpasang. Pasang lewat menu Paket"
fi
mkdir -p -- "$DIR" 2>/dev/null || fail "Folder hasil tidak bisa dibuat"

EXTRA=()
if [ -n "$COOKIES" ] && [ -f "$COOKIES" ]; then EXTRA+=(--cookies "$COOKIES"); fi
if [ -n "$NODE" ] && [ -x "$NODE" ]; then EXTRA+=(--js-runtimes "node:$NODE"); fi

TMPD=$(mktemp -d "${TMPDIR:-/tmp}/molina-dl.XXXXXX" 2>/dev/null) || fail "Folder sementara tidak bisa dibuat"
mkfifo "$TMPD/out" 2>/dev/null || fail "Pipa sementara tidak bisa dibuat"

# aria2c menahan keluarannya di buffer bila bukan terminal, sehingga progres baru tampil saat selesai.
# stdbuf membuatnya dikirim per baris; dipasang hanya untuk mesin ini.
RUNNER=()
if [ "$ENGINE" = aria2 ] && command -v stdbuf >/dev/null 2>&1; then RUNNER=(stdbuf -oL); fi

ST_STAGE=download
emit

"${RUNNER[@]}" yt-dlp --no-playlist --newline --no-colors -P "$DIR" \
    --progress-template 'download:MPROG|%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s|%(progress.filename)s|%(info.format_id)s|%(progress.fragment_index)s|%(progress.fragment_count)s' \
    --progress-template 'postprocess:MPOST|%(progress.status)s|%(progress.postprocessor)s' \
    --print-to-file 'pre_process:%(title)s' "$TMPD/title" \
    --print-to-file 'after_move:%(filepath)s' "$TMPD/path" \
    "${YT_ARGS[@]}" "${EXTRA[@]}" "$URL" > "$TMPD/out" 2>&1 &
YTPID=$!

exec 3< "$TMPD/out"
while IFS= read -r line <&3 || [ -n "$line" ]; do
    case $line in
        MPROG\|*)
            handle_ytdlp_progress "${line#MPROG|}"
            ;;
        MPOST\|*)
            handle_postprocess "${line#MPOST|}"
            ;;
        "[info] Writing '%("*)
            # Baris internal milik skrip ini (judul dan path hasil); tidak perlu tampil.
            ;;
        *)
            if [ "$ENGINE" = aria2 ] && handle_aria_progress "$line"; then
                :
            else
                end_progress_line
                printf '%s\n' "$line"
                case $line in
                    "ERROR: "*) LAST_ERROR=${line#ERROR: } ;;
                esac
                if [ -z "$R_TITLE" ] && [ -s "$TMPD/title" ]; then
                    R_TITLE=$(clean_text "$(head -n 1 "$TMPD/title")" 200)
                    emit
                fi
            fi
            ;;
    esac
done
exec 3<&-

wait "$YTPID"
RC=$?
YTPID=
[ "$CANCELLED" = 1 ] && exit 130
end_progress_line

if [ -z "$R_TITLE" ] && [ -s "$TMPD/title" ]; then
    R_TITLE=$(clean_text "$(head -n 1 "$TMPD/title")" 200)
fi
if [ "$RC" -ne 0 ]; then
    fail "${LAST_ERROR:-Unduhan gagal (kode $RC). Buka Terminal untuk melihat penyebabnya}"
fi

FINAL=
[ -s "$TMPD/path" ] && FINAL=$(tail -n 1 "$TMPD/path")
if [ -z "$FINAL" ] || [ ! -s "$FINAL" ]; then
    fail "Berkas hasil tidak ditemukan"
fi

SIZE=$(stat -c %s -- "$FINAL" 2>/dev/null || echo -)
R_OUT=$(clean_text "$(basename -- "$FINAL")" 200)
OUT_SAFE=$(clean_text "$FINAL" 400)
R_FMT=$(printf '%s' "${FINAL##*.}" | tr -d '\000-\037=' | tr '[:lower:]' '[:upper:]' | cut -c1-8)
R_DONE=$SIZE
R_TOTAL=$SIZE
R_SPEED=-
R_ETA=0
STREAM_IDX=$STREAM_N
ST_STATE=done
ST_STAGE=done
ST_PROGRESS=100
emit
echo "Selesai  $FINAL"
