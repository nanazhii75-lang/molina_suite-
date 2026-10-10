#!/usr/bin/env bash
# Langkah build native mpv (arm64) untuk CI.
# Pemakaian: ci-mpv-native.sh <sdk|deps|build|verify|jni>
set -eo pipefail

BS="$(cd "$(dirname "${BASH_SOURCE[0]}")/../engines/mpv-android/buildscripts" && pwd)"
cd "$BS"
export IN_CI=1

# Penyimpanan bersama Termux tidak menyimpan bit executable, jadi git mencatat
# skrip upstream sebagai 100644. Pulihkan di sini agar buildall.sh bisa memanggilnya.
find "$BS" -type f -name '*.sh' -exec chmod +x {} +

pin_sources() {
  : "${MPV_PIN_DATE:?MPV_PIN_DATE belum diset}"
  local d rev
  for d in dav1d libass libplacebo mpv; do
    rev="$(git -C "deps/$d" rev-list -n 1 --before="$MPV_PIN_DATE" HEAD)"
    [ -n "$rev" ] || { echo "Tidak ada commit sebelum $MPV_PIN_DATE di $d" >&2; exit 1; }
    git -C "deps/$d" checkout --quiet "$rev"
    git -C "deps/$d" submodule update --init --recursive
    echo "$d -> $rev"
  done
}

case "$1" in
  sdk)
    ./include/download-sdk.sh ;;
  deps)
    ./include/download-deps.sh
    pin_sources ;;
  build)
    ./buildall.sh --arch arm64 mpv ;;
  verify)
    for lib in libmpv libavcodec libswscale libswresample libavutil libavformat libavfilter libavdevice libpostproc; do
      [ -f "prefix/arm64/lib/$lib.so" ] || { echo "Hilang: $lib.so" >&2; exit 1; }
    done
    ls -lh prefix/arm64/lib/*.so ;;
  jni)
    . ./include/path.sh
    PREFIX64="$PWD/prefix/arm64" ndk-build -C ../app/src/main -j"$cores"
    find ../app/src/main/libs -name '*.so' | sort ;;
  *)
    echo "Langkah tidak dikenal: $1" >&2
    exit 2 ;;
esac
