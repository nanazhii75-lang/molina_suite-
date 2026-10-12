#!/usr/bin/env python3
"""Tambah step diagnosa sumber com.termux di termux-tools/termux-am/termux-exec."""
import difflib
import pathlib
import re
import sys

APPLY = "--apply" in sys.argv
WF = pathlib.Path(".github/workflows/build-bootstrap.yml")


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def indent(block, ind):
    return "".join((ind + ln if ln.strip() else ln) for ln in block.splitlines(True))


STEP = r'''- name: Diagnosa sumber com.termux di termux-tools
  if: always()
  continue-on-error: true
  run: |
    set +e
    cd tp
    echo "=== isi packages/termux-tools ==="
    ls -la packages/termux-tools
    echo "=== packages/termux-tools/build.sh ==="
    cat packages/termux-tools/build.sh
    echo "=== baris com.termux/com.molina di scripts/properties.sh (maks 40) ==="
    grep -n "TERMUX_APP__PACKAGE_NAME\|TERMUX_APP__NAMESPACE\|com\.termux\|com\.molina" scripts/properties.sh | head -40
    echo "=== termux-am dan termux-exec: baris relevan di build.sh ==="
    grep -n "com\.termux\|TERMUX_APP\|sed \|SRCURL\|VERSION=" packages/termux-am/build.sh packages/termux-exec/build.sh | head -40
    cd ..
    rm -rf dg && mkdir dg && cd dg
    curl -fsSL -o tt.tar.gz https://github.com/termux/termux-tools/archive/refs/tags/v1.45.0.tar.gz
    ls -l tt.tar.gz
    tar xzf tt.tar.gz || { echo "tar gagal"; exit 0; }
    cd termux-tools-1.45.0 || { echo "folder hasil ekstrak tidak ada"; ls; exit 0; }
    echo "=== daftar berkas di tarball (maks 60) ==="
    find . -type f | sort | head -60
    echo "=== berkas yang memuat com.termux di tarball asli ==="
    grep -rlF "com.termux" . | sort
    echo "=== placeholder @...@ di tarball (jumlah per jenis) ==="
    grep -rhoE "@[A-Z_]+@" . | sort | uniq -c | sort -rn | head -30
    echo "=== contoh baris com.termux (maks 50) ==="
    grep -rnF "com.termux" . | head -50
    echo "=== berkas hasil build di zip untuk pembanding: baris com.termux di pkg ==="
    exit 0

'''

if not WF.is_file():
    die(str(WF) + " tidak ada; jalankan dari root repo")
orig = WF.read_text(encoding="utf-8")
if "Diagnosa sumber com.termux di termux-tools" in orig:
    print("LEWAT: step sudah ada")
    sys.exit(0)
ms = list(re.finditer(r"^( +)- name: Bangun image builder dari commit terpin\n", orig, re.M))
if len(ms) != 1:
    die("anchor 'Bangun image builder' harus tepat 1, ditemukan %d" % len(ms))
m = ms[0]
text = orig[: m.start()] + indent(STEP, m.group(1)) + orig[m.start():]
try:
    import yaml
    yaml.safe_load(text)
    print("YAML valid")
except ImportError:
    print("(PyYAML tidak ada, validasi dilewati)")
except Exception as e:
    die("bukan YAML valid: %s" % e)
print("".join(difflib.unified_diff(orig.splitlines(True), text.splitlines(True), "sebelum", "sesudah", n=2)))
if APPLY:
    WF.write_text(text, encoding="utf-8")
    print("DITERAPKAN")
else:
    print("DRY-RUN: jalankan lagi dengan --apply")
