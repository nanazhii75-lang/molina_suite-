#!/usr/bin/env python3
"""Patch build-bootstrap.yml: teruskan nama paket ke configure termux-tools dan paksa paket itu dibangun ulang."""
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


def anchor(text, name):
    ms = list(re.finditer(r"^( +)- name: " + re.escape(name) + r"\n", text, re.M))
    if len(ms) != 1:
        die("step '%s' harus ada tepat 1, ditemukan %d" % (name, len(ms)))
    return ms[0]


STEP_ENV = r'''- name: Patch termux-tools (teruskan nama paket ke configure)
  if: inputs.build
  run: |
    F=tp/packages/termux-tools/build.sh
    P=tp/scripts/properties.sh
    LINE='TERMUX_PKG_EXTRA_CONFIGURE_ARGS="TERMUX_APP_PACKAGE=${TERMUX_APP__PACKAGE_NAME}"'
    n_pkg=$(grep -c '^TERMUX_APP__PACKAGE_NAME="com.molina.suite"$' "$P" || true)
    n_old=$(grep -c '^TERMUX_PKG_EXTRA_CONFIGURE_ARGS' "$F" || true)
    n_anchor=$(grep -c '^TERMUX_PKG_ESSENTIAL=true$' "$F" || true)
    if [ "$n_pkg" != 1 ] || [ "$n_old" != 0 ] || [ "$n_anchor" != 1 ]; then
      echo "GAGAL: properties=$n_pkg (harus 1), EXTRA_CONFIGURE_ARGS=$n_old (harus 0), anchor=$n_anchor (harus 1)"
      grep -n "TERMUX_PKG_EXTRA_CONFIGURE_ARGS\|TERMUX_PKG_ESSENTIAL" "$F"
      exit 1
    fi
    sed -i '/^TERMUX_PKG_ESSENTIAL=true$/a TERMUX_PKG_EXTRA_CONFIGURE_ARGS="TERMUX_APP_PACKAGE=${TERMUX_APP__PACKAGE_NAME}"' "$F"
    n_new=$(grep -cF "$LINE" "$F" || true)
    if [ "$n_new" != 1 ]; then
      echo "GAGAL: hasil patch tidak sesuai ($n_new)"
      exit 1
    fi
    bash -n "$F"
    echo "OK: termux-tools meneruskan TERMUX_APP_PACKAGE ke configure"
    sed -n '1,12p' "$F"

'''

STEP_RM = r'''- name: Hapus penanda built termux-tools (paksa bangun ulang)
  if: ${{ inputs.build }}
  run: |
    docker exec termux-package-builder sudo rm -f /data/data/.built-packages/termux-tools
    docker exec termux-package-builder sudo rm -rf /home/builder/.termux-build/termux-tools
    if docker exec termux-package-builder test -e /data/data/.built-packages/termux-tools; then
      echo "GAGAL: penanda termux-tools masih ada"
      exit 1
    fi
    echo "OK: penanda dan direktori build termux-tools dihapus"
    echo "=== paket bertanda selesai (seharusnya tanpa termux-tools) ==="
    docker exec termux-package-builder ls /data/data/.built-packages | head -60 || true

'''

if not WF.is_file():
    die(str(WF) + " tidak ada; jalankan dari root repo")
orig = WF.read_text(encoding="utf-8")
text = orig
done, skipped = [], []

if "Patch termux-tools (teruskan nama paket" in text:
    skipped.append("step patch termux-tools sudah ada")
else:
    m = anchor(text, "Bangun image builder dari commit terpin")
    text = text[: m.start()] + indent(STEP_ENV, m.group(1)) + text[m.start():]
    done.append("step patch termux-tools (sebelum image builder)")

if "Hapus penanda built termux-tools" in text:
    skipped.append("step hapus penanda sudah ada")
else:
    m = anchor(text, "Build bootstrap aarch64")
    text = text[: m.start()] + indent(STEP_RM, m.group(1)) + text[m.start():]
    done.append("step hapus penanda built (sebelum Build bootstrap aarch64)")

try:
    import yaml
    yaml.safe_load(text)
    print("YAML valid")
except ImportError:
    print("(PyYAML tidak ada, validasi YAML dilewati)")
except Exception as e:
    die("hasil bukan YAML valid: %s" % e)

for s in skipped:
    print("LEWAT: " + s)
if text == orig:
    print("Tidak ada perubahan.")
    sys.exit(0)
print("".join(difflib.unified_diff(orig.splitlines(True), text.splitlines(True), "sebelum", "sesudah", n=2)))
for d in done:
    print("PERUBAHAN: " + d)
if APPLY:
    WF.write_text(text, encoding="utf-8")
    print("DITERAPKAN ke " + str(WF))
else:
    print("DRY-RUN: jalankan lagi dengan --apply")
