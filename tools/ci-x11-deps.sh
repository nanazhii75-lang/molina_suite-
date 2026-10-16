#!/usr/bin/env bash
set -euo pipefail
B=engines/termux-x11/lorie/src/main/cpp
fetch() { rm -rf "$B/$1"; mkdir -p "$B/$1"; git -C "$B/$1" init -q
  git -C "$B/$1" fetch -q --depth 1 "$2" "$3"; git -C "$B/$1" checkout -q FETCH_HEAD; }
G=https://gitlab.freedesktop.org/xorg
fetch xserver $G/xserver.git 65d790bd208ec380b196eb98f144abb0b32e334d
fetch libx11 $G/lib/libx11.git 6c75545a1deb51f5903992c52af6bc35cc9bc103
fetch xorgproto $G/proto/xorgproto.git 6d3907b6d5dc4aa7d7ee6ee5d7d8714e7466630b
fetch xkbcomp $G/app/xkbcomp.git 2c7789785981ad1fce3858c726615b49293f7de0
fetch libxau $G/lib/libxau.git a9c65683e68b3a4349afee5d7673b393fb924d2e
fetch libxdmcp $G/lib/libxdmcp.git 1192d3bc407348ff316bd3bffc791b3ac73f591b
fetch libxfont $G/lib/libxfont.git 975cc6526e892c6fad8fa49d0fef58565fbaa003
fetch libfontenc $G/lib/libfontenc.git 780d1f6f192a331de7b298a96e23ebc44d2a884b
fetch libxkbfile $G/lib/libxkbfile.git 42e5dedd7fd3c7c73f3870a8751893c03c1afc69
fetch libxshmfence $G/lib/libxshmfence.git 89f064748554e11832a3ec783945e1f4c7fe846e
fetch libxtrans $G/lib/libxtrans.git cf05ba4a10c90da2c63805a5375e983b174e28b0
fetch libxcvt $G/lib/libxcvt.git dd8631c61465cc0de5e476c7a98e56528d62b163
fetch pixman https://gitlab.freedesktop.org/pixman/pixman.git 9cc163c9da0fb4da430641715313d95a6ec466d9
fetch libepoxy https://github.com/anholt/libepoxy c84bc9459357a40e46e2fec0408d04fbdde2c973
fetch libtirpc https://github.com/alisw/libtirpc 5ca4ca92f629d9d83e83544b9239abaaacf0a527
fetch bzip2 https://gitlab.com/bzip2/bzip2.git 6a8690fc8d26c815e798c588f796eabe9d684cf0
