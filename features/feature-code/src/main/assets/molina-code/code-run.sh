#!/bin/bash
# Skrip start code-server milik Molina Code.
# Dijalankan di dalam Debian (PRoot) oleh CodeServerController; jangan dijalankan manual.
# Placeholder @...@ diisi aplikasi saat skrip ditulis ke rootfs.

BASE=/opt/molina
PIDFILE="$BASE/server.pid"
LOG="$BASE/server.log"
PASSWORD_FILE="$BASE/server.password"
BIN=/opt/code-server/bin/code-server
WORKSPACE="@WORKSPACE@"

: > "$LOG"
exec >>"$LOG" 2>&1

if [ ! -x "$BIN" ]; then
    echo "GAGAL: $BIN tidak ditemukan"
    exit 1
fi
if [ ! -s "$PASSWORD_FILE" ]; then
    echo "GAGAL: berkas kata sandi kosong"
    exit 1
fi

PASSWORD="$(cat "$PASSWORD_FILE")"
export PASSWORD

if [ -d "$WORKSPACE" ]; then
    set -- "$WORKSPACE"
else
    set --
fi

echo $$ > "$PIDFILE"
echo "[$(date -u +%H:%M:%S)] memulai code-server @VERSION@ di @BIND@"
exec "$BIN" \
    --bind-addr "@BIND@" \
    --auth password \
    --disable-telemetry \
    --disable-update-check \
    --app-name "@APP_NAME@" \
    "$@"
