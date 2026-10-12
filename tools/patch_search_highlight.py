import os, sys

KT = os.path.join(
    "features", "feature-code", "src", "main", "kotlin", "com", "molina", "suite",
    "feature", "code",
)
PALETTE = os.path.join(KT, "CodePalette.kt")
TEXTMATE = os.path.join(KT, "CodeTextMate.kt")

PALETTE_OLD = "    val FOLDER: Int = 0xFF7FB8E8.toInt()\n"
PALETTE_NEW = (
    PALETTE_OLD
    + "\n    /** Blok latar semua kecocokan pencarian; gelap supaya teks terang di atasnya tetap terbaca. */\n"
    + "    val MATCH_BLOCK: Int = 0xFF7A4A12.toInt()\n"
)
TEXTMATE_OLD = "        val scope = fileName?.let { scopeFor(it) }\n"
TEXTMATE_NEW = (
    "        scheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, CodePalette.MATCH_BLOCK)\n"
    + TEXTMATE_OLD
)


def die(msg):
    print("GAGAL: " + msg + " (tidak ada file yang diubah)")
    sys.exit(1)


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)


for p in (PALETTE, TEXTMATE):
    if not os.path.exists(p):
        die("tidak ada " + p + "; jalankan dari /storage/emulated/0/molina-suite")

changed = []

palette = read(PALETTE)
if "MATCH_BLOCK" not in palette:
    if PALETTE_OLD not in palette:
        die("baris FOLDER di CodePalette.kt tidak ditemukan")
    write(PALETTE, palette.replace(PALETTE_OLD, PALETTE_NEW, 1))
    changed.append(PALETTE)

textmate = read(TEXTMATE)
if "MATCHED_TEXT_BACKGROUND" not in textmate:
    if TEXTMATE_OLD not in textmate:
        die("baris 'val scope' di CodeTextMate.kt tidak ditemukan; jalankan git pull dulu")
    write(TEXTMATE, textmate.replace(TEXTMATE_OLD, TEXTMATE_NEW, 1))
    changed.append(TEXTMATE)

print("selesai: %d berkas diubah" % len(changed))
for p in changed:
    print("  " + p)
