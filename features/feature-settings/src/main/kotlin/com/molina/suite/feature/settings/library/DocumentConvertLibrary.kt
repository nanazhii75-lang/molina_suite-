package com.molina.suite.feature.settings.library

/**
 * Entri Library bawaan "Konversi dokumen (universal)": satu skrip bash yang jalan di Debian dan
 * mengonversi md, txt, html, docx, epub, rst, dan pdf ke format lain. Pandoc (dan Typst untuk
 * PDF) dipasang dari menu Paket; teks PDF diekstrak dengan pdftotext (poppler-utils) yang
 * dipasang otomatis oleh skrip bila belum ada, jadi PDF ke txt/md tidak butuh Pandoc. Hasil
 * selalu ke /sdcard/Molina/Export dan tidak pernah menimpa berkas lama.
 *
 * Skrip ditulis dengan tanda [DOLLAR_MARK] sebagai pengganti tanda dolar supaya tidak dianggap
 * template Kotlin; tanda itu diganti saat entri dibuat. Placeholder {FILE} dan {FORMAT} diisi
 * lewat dialog Library dan di-quote otomatis.
 */
internal object DocumentConvertLibrary {

    const val ENTRY_ID = "builtin-doc-convert"

    private const val DOLLAR_MARK = '§'

    private val script: String = """
# Konversi dokumen universal molina-suite. Hasil selalu ke /sdcard/Molina/Export dan tidak
# pernah menimpa berkas yang sudah ada (nama bentrok diberi akhiran -2, -3, dst).
IN={FILE}
FMT={FORMAT}
SHARED="§{MOLINA_SHARED:-/sdcard}"
OUTDIR="§SHARED/Molina/Export"

die() { echo "GAGAL: §1" >&2; exit 1; }

case "§IN" in
  *§'\n'*|*§'\r'*) die "Nama berkas tidak valid." ;;
  /storage/emulated/0/*) IN="§SHARED/§{IN#/storage/emulated/0/}" ;;
  "§SHARED"/*) ;;
  *) die "Berkas harus ada di penyimpanan bersama (§SHARED)." ;;
esac
case "/§IN/" in */../*) die "Jalur tidak boleh memuat .." ;; esac
[ -f "§IN" ] || die "Berkas tidak ditemukan: §IN"

FMT="§(printf '%s' "§FMT" | tr 'A-Z' 'a-z')"
FMT="§{FMT#.}"
case "§FMT" in
  md|markdown) FMT=md; WRITER=gfm ;;
  txt|text) FMT=txt; WRITER=plain ;;
  html|htm) FMT=html; WRITER=html ;;
  docx) WRITER=docx ;;
  epub) WRITER=epub ;;
  rst) WRITER=rst ;;
  pdf) WRITER=pdf ;;
  *) die "Format tujuan tidak dikenal: §FMT. Pilih: md, txt, html, docx, epub, rst, pdf." ;;
esac

NAME="§{IN##*/}"
BASE="§{NAME%.*}"
[ "§BASE" = "§NAME" ] && die "Berkas harus punya ekstensi: §NAME"
[ -n "§BASE" ] || BASE="dokumen"
EXT="§(printf '%s' "§{NAME##*.}" | tr 'A-Z' 'a-z')"
case "§EXT" in
  md|markdown) EXT=md; READER=gfm ;;
  txt|text) EXT=txt; READER="markdown+hard_line_breaks" ;;
  html|htm) EXT=html; READER=html ;;
  docx) READER=docx ;;
  epub) READER=epub ;;
  rst) READER=rst ;;
  pdf) READER=pdf ;;
  *) die "Sumber .§EXT belum didukung. Sumber: md, txt, html, docx, epub, rst, pdf." ;;
esac
[ "§EXT" = "§FMT" ] && die "Sumber dan tujuan sama (.§FMT)."

mkdir -p "§OUTDIR" || die "Tidak bisa membuat §OUTDIR"
OUT="§OUTDIR/§BASE.§FMT"
N=2
while [ -e "§OUT" ]; do OUT="§OUTDIR/§BASE-§N.§FMT"; N=§((N + 1)); done
PART="§OUTDIR/.§BASE.part.§§.§FMT"
trap 'rm -f "§PART"' EXIT
cd "§(dirname "§IN")" || die "Tidak bisa masuk ke folder sumber."
SRC="./§NAME"

if [ "§EXT" = pdf ]; then
  case "§FMT" in
    txt|md) ;;
    *) die "PDF hanya bisa dikonversi ke txt atau md." ;;
  esac
  command -v pdftotext >/dev/null 2>&1 || {
    echo "Memasang poppler-utils (pdftotext)..."
    apt update && apt install -y poppler-utils || die "Gagal memasang poppler-utils."
  }
  echo "Mengekstrak teks dari §NAME..."
  timeout 300 pdftotext -enc UTF-8 "§SRC" "§PART" || die "pdftotext gagal membaca PDF (terenkripsi atau rusak?)."
  grep -q '[^[:space:]]' "§PART" || die "PDF tidak memuat teks (mungkin hasil scan; butuh OCR)."
  if [ "§FMT" = md ]; then
    TEXT="§(cat "§PART")" || die "Gagal membaca hasil ekstraksi."
    TEXT="§{TEXT//§'\f'/§'\n\n---\n\n'}"
    TEXT="§{TEXT%§'\n\n---\n\n'}"
    printf '%s\n' "§TEXT" > "§PART" || die "Gagal menyusun markdown."
  fi
else
  command -v pandoc >/dev/null 2>&1 || die "Pandoc belum terpasang. Pasang lewat Setting, bagian Paket."
  OPTS=(-f "§READER" -t "§WRITER")
  LIMIT=180
  case "§FMT" in
    html) OPTS+=(-s --metadata "pagetitle=§BASE") ;;
    epub) OPTS+=(--metadata "title=§BASE") ;;
    md|rst|txt) OPTS+=(--wrap=none) ;;
    pdf)
      command -v typst >/dev/null 2>&1 || die "Typst belum terpasang. Pasang lewat Setting, bagian Paket."
      OPTS+=(--pdf-engine=typst)
      LIMIT=300
      ;;
  esac
  echo "Mengonversi §NAME ke .§FMT..."
  timeout "§LIMIT" pandoc "§{OPTS[@]}" -o "§PART" "§SRC" || die "Pandoc gagal atau waktu habis (§LIMIT detik)."
fi

[ -s "§PART" ] || die "Hasil kosong."
mv -f "§PART" "§OUT" || die "Gagal menulis §OUT"
echo "Selesai: §OUT"
""".trim().replace(DOLLAR_MARK, '$') + "\n"

    val entry: LibraryEntry = LibraryEntry(
        id = ENTRY_ID,
        name = "Konversi dokumen (universal)",
        content = script,
        note = "Isi FILE (path di /sdcard, mis. /sdcard/Molina/a.docx) dan FORMAT tujuan: " +
            "md, txt, html, docx, epub, rst, atau pdf. Hasil di /sdcard/Molina/Export dan tidak " +
            "menimpa berkas lama. PDF hanya ke txt atau md.",
        runIn = RunMode.DEBIAN,
        builtin = true
    )
}
