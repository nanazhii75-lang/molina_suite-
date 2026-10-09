#!/bin/bash
# Skrip pemasangan code-server milik Molina Code.
# Dijalankan di dalam Debian (PRoot) oleh CodeServerInstaller; jangan dijalankan manual.
# Placeholder @...@ diisi aplikasi saat skrip ditulis ke rootfs.

VERSION="@VERSION@"
URL="@URL@"
EXPECTED_SIZE="@SIZE@"
EXPECTED_SHA256="@SHA256@"

BASE=/opt/molina
STATUS="$BASE/install.status"
LOG="$BASE/install.log"
TARBALL="$BASE/download/code-server.tar.gz"
TARGET=/opt/code-server
STAGING=/opt/code-server.staging

mkdir -p "$BASE/download"
: > "$LOG"
exec >>"$LOG" 2>&1

phase() {
    echo "$1" > "$STATUS"
    echo "[$(date -u +%H:%M:%S)] fase: $1"
}

fail() {
    echo "GAGAL: $1"
    phase failed
    exit 1
}

file_size() {
    stat -c %s "$1" 2>/dev/null || echo 0
}

phase prepare
if ! command -v curl >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update || fail "apt-get update gagal"
    apt-get install -y curl ca-certificates || fail "pemasangan curl gagal"
fi

phase download
if [ -f "$TARBALL" ] && [ "$(file_size "$TARBALL")" -gt "$EXPECTED_SIZE" ]; then
    rm -f "$TARBALL"
fi
if [ "$(file_size "$TARBALL")" -ne "$EXPECTED_SIZE" ]; then
    curl -fLsS --retry 3 --retry-delay 2 -C - -o "$TARBALL" "$URL" || fail "unduhan gagal"
fi

phase verify
if [ "$(file_size "$TARBALL")" -ne "$EXPECTED_SIZE" ]; then
    rm -f "$TARBALL"
    fail "ukuran berkas tidak cocok"
fi
ACTUAL_SHA256=$(sha256sum "$TARBALL" | cut -d' ' -f1)
if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    rm -f "$TARBALL"
    fail "checksum tidak cocok: $ACTUAL_SHA256"
fi

phase extract
rm -rf "$STAGING"
mkdir -p "$STAGING"
if ! tar -xzf "$TARBALL" -C "$STAGING" --strip-components=1; then
    rm -rf "$STAGING"
    fail "ekstraksi gagal"
fi
if [ ! -x "$STAGING/bin/code-server" ]; then
    rm -rf "$STAGING"
    fail "bin/code-server tidak ditemukan setelah ekstraksi"
fi

phase finish
rm -rf "$TARGET"
mv "$STAGING" "$TARGET" || fail "gagal memindahkan hasil ekstraksi"
echo "$VERSION" > "$TARGET/.molina-version"
rm -f "$TARBALL"
phase done
echo "code-server $VERSION terpasang di $TARGET"
