#!/usr/bin/env bash
# Uji Pandoc + Typst molina-suite.
# Meniru cara app memanggil Debian (PandocCommandBuilder + ScriptRunLine.wrapForDebian).
# Jalankan: bash /sdcard/Molina/scripts/test_pandoc.sh
set -u

WORK="${PANDOC_TEST_DIR:-/sdcard/Molina/pandoc-test}"
PASS=0
FAIL=0
ok()   { echo "  [OK]     $1"; PASS=$((PASS + 1)); }
bad()  { echo "  [GAGAL]  $1"; FAIL=$((FAIL + 1)); }
info() { echo "  [info]   $1"; }
err()  { head -c 300 "$WORK/t_err.txt" 2>/dev/null | tr '\n' ' '; }

if [ -f /etc/debian_version ]; then
  MODE="di dalam Debian"
  run() { "$@"; }
elif command -v proot-distro >/dev/null 2>&1; then
  MODE="host -> proot-distro login debian"
  run() { proot-distro login debian -- "$@"; }
else
  echo "Debian belum terpasang. Pasang dulu lewat Settings > Debian."
  exit 2
fi

mkdir -p "$WORK" 2>/dev/null || { echo "Tidak bisa membuat $WORK (izin storage?)"; exit 2; }
rm -f "$WORK"/t.* "$WORK"/t_* "$WORK"/"t doc"* 2>/dev/null
echo "Mode: $MODE"
echo "Folder kerja: $WORK"

echo "0. Folder kerja terlihat dan bisa ditulis dari Debian"
if run bash -c "touch '$WORK/t_probe' && test -f '$WORK/t_probe'" 2>/dev/null && [ -f "$WORK/t_probe" ]; then
  ok "$WORK terlihat dari Debian"
else
  bad "$WORK tidak terlihat/tertulis dari Debian (bind /sdcard?)"
  echo; echo "Hasil: $PASS lolos, $FAIL gagal."; exit 1
fi

cat > "$WORK/t.md" << 'MD'
# Pengujian Pandoc

Paragraf dengan **tebal**, *miring*, dan aksara: é ü ñ — selesai.

| Kolom A | Kolom B |
|---------|---------|
| satu    | dua     |

- butir pertama
- butir kedua
MD
cp "$WORK/t.md" "$WORK/t doc.md"

echo "1. Alat terpasang"
PV=$(run pandoc --version 2>&1 | head -1)
case "$PV" in pandoc*) ok "$PV" ;; *) bad "pandoc: $PV" ;; esac
TV=$(run typst --version 2>&1 | head -1)
case "$TV" in typst*) ok "$TV" ;; *) bad "typst: $TV" ;; esac
info "arsitektur: $(run uname -m 2>&1 | head -1)"
info "marker pandoc: $(run cat /opt/pandoc/.molina-version 2>&1 | head -1) | typst: $(run cat /opt/typst/.molina-version 2>&1 | head -1)"

echo "2. Markdown -> HTML (-s)"
if run pandoc -f gfm -t html -s -o "$WORK/t.html" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && grep -q "<h1" "$WORK/t.html" && grep -q "<table" "$WORK/t.html" \
   && grep -q "Pengujian Pandoc" "$WORK/t.html"; then
  ok "heading dan tabel ada"
else
  bad "HTML: $(err)"
fi

echo "3. Markdown -> DOCX"
if run pandoc -f gfm -t docx -o "$WORK/t.docx" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && head -c 2 "$WORK/t.docx" | grep -q '^PK'; then
  ok "DOCX valid (zip), $(wc -c < "$WORK/t.docx") byte"
else
  bad "DOCX: $(err)"
fi

echo "4. DOCX -> Markdown (bolak-balik)"
if run pandoc -f docx -t gfm --wrap=none -o "$WORK/t_back.md" "$WORK/t.docx" 2>"$WORK/t_err.txt" \
   && grep -q "Pengujian Pandoc" "$WORK/t_back.md" && grep -q "butir kedua" "$WORK/t_back.md"; then
  ok "isi DOCX terbaca kembali"
else
  bad "DOCX balik: $(err)"
fi

echo "5. Markdown -> EPUB"
if run pandoc -f gfm -t epub -o "$WORK/t.epub" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && head -c 2 "$WORK/t.epub" | grep -q '^PK'; then
  ok "EPUB valid (zip)"
else
  bad "EPUB: $(err)"
fi

echo "6. Markdown -> RST dan teks biasa"
if run pandoc -f gfm -t rst --wrap=none -o "$WORK/t.rst" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && run pandoc -f gfm -t plain --wrap=none -o "$WORK/t.txt" "$WORK/t.md" 2>>"$WORK/t_err.txt" \
   && grep -q "Pengujian Pandoc" "$WORK/t.rst" && grep -q "Pengujian Pandoc" "$WORK/t.txt"; then
  ok "RST dan plain terbentuk"
else
  bad "RST/plain: $(err)"
fi

echo "7. HTML -> Markdown (sumber HTML)"
if run pandoc -f html -t gfm --wrap=none -o "$WORK/t_html.md" "$WORK/t.html" 2>"$WORK/t_err.txt" \
   && grep -q "Pengujian Pandoc" "$WORK/t_html.md"; then
  ok "HTML terbaca sebagai sumber"
else
  bad "HTML sumber: $(err)"
fi

echo "8. Typst murni: Markdown -> .typ -> PDF"
if run pandoc -f gfm -t typst -o "$WORK/t.typ" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && run typst compile "$WORK/t.typ" "$WORK/t_typst.pdf" 2>>"$WORK/t_err.txt" \
   && head -c 4 "$WORK/t_typst.pdf" | grep -q '^%PDF'; then
  ok "typst compile, $(wc -c < "$WORK/t_typst.pdf") byte"
  grep -qi "warning" "$WORK/t_err.txt" && info "typst peringatan: $(err)"
else
  bad "Typst: $(err)"
fi

echo "9. Markdown -> PDF langsung (--pdf-engine=typst)"
if run pandoc -f gfm -t pdf --pdf-engine=typst -o "$WORK/t_direct.pdf" "$WORK/t.md" 2>"$WORK/t_err.txt" \
   && head -c 4 "$WORK/t_direct.pdf" | grep -q '^%PDF' \
   && [ "$(wc -c < "$WORK/t_direct.pdf")" -gt 1000 ]; then
  ok "PDF langsung, $(wc -c < "$WORK/t_direct.pdf") byte"
else
  bad "PDF langsung: $(err)"
fi

echo "10. Pola skrip app (.part lalu mv, cd, nama berspasi, timeout)"
APP_SCRIPT="P='$WORK/t_app.part.pdf' && trap 'rm -f \"\$P\"' EXIT && mkdir -p '$WORK' && cd '$WORK' && timeout 300 pandoc -f 'gfm' -t 'pdf' --pdf-engine=typst -o \"\$P\" 't doc.md' && mv -f \"\$P\" '$WORK/t_app.pdf'"
if run bash -c "$APP_SCRIPT" 2>"$WORK/t_err.txt" \
   && head -c 4 "$WORK/t_app.pdf" | grep -q '^%PDF' \
   && [ ! -e "$WORK/t_app.part.pdf" ]; then
  ok "t_app.pdf terbentuk, berkas .part tidak tersisa"
else
  bad "Pola app: $(err)"
fi

echo "11. Kegagalan bersih (sumber tidak ada tidak meninggalkan hasil)"
FAIL_SCRIPT="P='$WORK/t_fail.part.docx' && trap 'rm -f \"\$P\"' EXIT && cd '$WORK' && pandoc -f gfm -t docx -o \"\$P\" 'tidak-ada.md' && mv -f \"\$P\" '$WORK/t_fail.docx'"
if run bash -c "$FAIL_SCRIPT" >/dev/null 2>&1; then
  bad "seharusnya gagal tetapi sukses"
elif [ ! -e "$WORK/t_fail.docx" ] && [ ! -e "$WORK/t_fail.part.docx" ]; then
  ok "gagal tanpa meninggalkan berkas"
else
  bad "berkas sisa tertinggal setelah gagal"
fi

echo
echo "Hasil: $PASS lolos, $FAIL gagal. Berkas uji ada di $WORK"
[ "$FAIL" -eq 0 ]
