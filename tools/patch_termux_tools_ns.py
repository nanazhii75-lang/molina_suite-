#!/usr/bin/env python3
"""Patch build-bootstrap.yml: komponen app termux-tools memakai namespace Java, plus diagnosa hasil."""
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


STEP_NS = r'''- name: Patch termux-tools (komponen app memakai namespace Java)
  if: inputs.build
  run: |
    F=tp/packages/termux-tools/build.sh
    SNIP=$(mktemp)
    cat > "$SNIP" << 'SNIPEOF'
      # molina: komponen app memakai namespace Java, bukan nama paket
      local _mf _mn=0
      for _mf in termux-open termux-reset termux-wake-lock termux-wake-unlock; do
        _mn=$((_mn + $(grep -cF '@TERMUX_APP_PACKAGE@/@TERMUX_APP_PACKAGE@.app.' "$TERMUX_PKG_SRCDIR/scripts/${_mf}.in" || true)))
      done
      if [ "$_mn" != 4 ] || [ -z "${TERMUX_APP__NAMESPACE:-}" ]; then
        echo "GAGAL molina: komponen=$_mn (harus 4), namespace='${TERMUX_APP__NAMESPACE:-}'" 1>&2
        exit 1
      fi
      for _mf in termux-open termux-reset termux-wake-lock termux-wake-unlock; do
        sed -i "s|@TERMUX_APP_PACKAGE@/@TERMUX_APP_PACKAGE@\.app\.|@TERMUX_APP_PACKAGE@/${TERMUX_APP__NAMESPACE}.app.|g" "$TERMUX_PKG_SRCDIR/scripts/${_mf}.in"
      done
    SNIPEOF
    n_fn=$(grep -c '^termux_step_pre_configure() {$' "$F" || true)
    n_done=$(grep -c 'molina: komponen app' "$F" || true)
    if [ "$n_fn" != 1 ] || [ "$n_done" != 0 ]; then
      echo "GAGAL: fungsi=$n_fn (harus 1), sudah ditambal=$n_done (harus 0)"
      grep -n "termux_step_pre_configure" "$F"
      exit 1
    fi
    sed -i '/^termux_step_pre_configure() {$/r '"$SNIP" "$F"
    n_done=$(grep -c 'molina: komponen app' "$F" || true)
    if [ "$n_done" != 1 ]; then
      echo "GAGAL: hasil patch tidak sesuai ($n_done)"
      exit 1
    fi
    bash -n "$F"
    echo "OK: build.sh termux-tools ditambal"
    sed -n '/^termux_step_pre_configure() {$/,/^}/p' "$F"

'''

STEP_DIAG = r'''- name: Diagnosa komponen app di bootstrap
  if: ${{ always() && inputs.build }}
  continue-on-error: true
  run: |
    set +e
    ZIP=$(find tp -maxdepth 2 -name 'bootstrap-aarch64.zip' | head -1)
    echo "ZIP=$ZIP"
    [ -n "$ZIP" ] || exit 0
    for f in bin/termux-wake-lock bin/termux-wake-unlock bin/termux-open bin/termux-reset bin/termux-reload-settings bin/termux-setup-storage; do
      echo "=== $f ==="
      unzip -p "$ZIP" "$f" | grep -n "com\.\(molina\|termux\)"
    done
    echo "=== bin/pkg ==="
    unzip -p "$ZIP" bin/pkg | grep -n "com\.\(molina\|termux\)" | head -25
    echo "=== bin/login ==="
    unzip -p "$ZIP" bin/login | grep -n "com\.\(molina\|termux\)" | head -15
    echo "=== bin/chsh ==="
    unzip -p "$ZIP" bin/chsh | grep -n "com\.\(molina\|termux\)" | head -10
    exit 0

'''

if not WF.is_file():
    die(str(WF) + " tidak ada; jalankan dari root repo")
orig = WF.read_text(encoding="utf-8")
text = orig
done, skipped = [], []

for need in ("Patch termux-tools (teruskan nama paket ke configure)",
             "Hapus penanda built termux-tools"):
    if need not in text:
        die("step '%s' belum ada; terapkan dulu tools/patch_termux_tools_env.py --apply" % need)

if "Patch termux-tools (komponen app memakai namespace Java)" in text:
    skipped.append("step patch komponen sudah ada")
else:
    m = anchor(text, "Bangun image builder dari commit terpin")
    text = text[: m.start()] + indent(STEP_NS, m.group(1)) + text[m.start():]
    done.append("step patch komponen app (sebelum image builder)")

if "Diagnosa komponen app di bootstrap" in text:
    skipped.append("step diagnosa komponen sudah ada")
else:
    m = anchor(text, "Verifikasi hasil")
    text = text[: m.start()] + indent(STEP_DIAG, m.group(1)) + text[m.start():]
    done.append("step diagnosa komponen (sebelum Verifikasi hasil)")

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
