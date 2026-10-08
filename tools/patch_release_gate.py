#!/usr/bin/env python3
"""Patch build-bootstrap.yml: kunci rilis, tambal trap exit palsu, betulkan path verifikasi."""
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


if not WF.is_file():
    die(str(WF) + " tidak ada; jalankan dari root repo")

orig = WF.read_text(encoding="utf-8")
text = orig
done, skipped = [], []

# 1. input release
if re.search(r"^ +release:\s*$", text, re.M):
    skipped.append("input release sudah ada")
else:
    ms = list(re.finditer(r"^( +)use_cache:\n((?:\1 +.*\n)+)", text, re.M))
    if len(ms) != 1:
        die("blok input use_cache harus ada tepat 1, ditemukan %d" % len(ms))
    m = ms[0]
    block = (
        "release:\n"
        '  description: "Izinkan publikasi ke release repo (biarkan false sampai semua diagnosa tuntas)"\n'
        "  type: boolean\n"
        "  required: true\n"
        "  default: false\n"
    )
    text = text[: m.end()] + indent(block, m.group(1)) + text[m.end():]
    done.append("input release (default false)")

# 2. patch trap build-bootstraps
TRAP_STEP = r'''- name: Patch trap build-bootstraps (kode keluar palsu)
  if: inputs.build
  run: |
    F=tp/scripts/build-bootstraps.sh
    old='build_bootstrap_killtree "$1" $$;'
    new='build_bootstrap_killtree "$1" $$ || true;'
    n_old=$(grep -cF "$old" "$F" || true)
    n_new=$(grep -cF "$new" "$F" || true)
    if [ "$n_old" != 1 ] || [ "$n_new" != 0 ]; then
      echo "GAGAL: harus 1 baris lama dan 0 baris baru, ditemukan $n_old dan $n_new"
      grep -n "build_bootstrap_killtree" "$F"
      exit 1
    fi
    sed -i 's#build_bootstrap_killtree "\$1" \$\$;#build_bootstrap_killtree "$1" $$ || true;#' "$F"
    n_old=$(grep -cF "$old" "$F" || true)
    n_new=$(grep -cF "$new" "$F" || true)
    if [ "$n_old" != 0 ] || [ "$n_new" != 1 ]; then
      echo "GAGAL: hasil patch tidak sesuai ($n_old dan $n_new)"
      exit 1
    fi
    bash -n "$F"
    echo "OK: trap ditambal"
    grep -n -B2 -A3 'killtree "\$1" \$\$' "$F"

'''
if "Patch trap build-bootstraps" in text:
    skipped.append("step patch trap sudah ada")
else:
    m = anchor(text, "Bangun image builder dari commit terpin")
    text = text[: m.start()] + indent(TRAP_STEP, m.group(1)) + text[m.start():]
    done.append("step patch trap (sebelum image builder)")

# 3. unggah zip sebagai artifact sementara
UPLOAD_STEP = r'''- name: Unggah zip bootstrap (sementara, bukan rilis)
  if: ${{ always() && inputs.build }}
  uses: actions/upload-artifact@v4
  with:
    name: bootstrap-aarch64-uji
    path: |
      bootstrap-aarch64.zip
      bootstrap-aarch64.zip.sha256
    retention-days: 3
    if-no-files-found: ignore

'''
if "bootstrap-aarch64-uji" in text:
    skipped.append("artifact zip sudah ada")
else:
    m = anchor(text, "Publikasikan ke release repo")
    text = text[: m.start()] + indent(UPLOAD_STEP, m.group(1)) + text[m.start():]
    done.append("artifact zip sementara")

# 4. kunci rilis
if "inputs.build && inputs.release" in text:
    skipped.append("gerbang rilis sudah ada")
else:
    pat = re.compile(r"(- name: Publikasikan ke release repo\n +if: )\$\{\{ inputs\.build \}\}")
    if len(pat.findall(text)) != 1:
        die("kondisi step Publikasikan harus tepat 1 baris '${{ inputs.build }}'")
    text = pat.sub(r"\g<1>${{ inputs.build && inputs.release }}", text)
    done.append("gerbang rilis: inputs.build && inputs.release")

# 5. path second-stage di Verifikasi hasil
OLD = "unzip -p bootstrap-aarch64.zip etc/termux/bootstrap/termux-bootstrap-second-stage.sh"
NEW = "unzip -p bootstrap-aarch64.zip etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
n_old, n_new = text.count(OLD), text.count(NEW)
if n_old == 0 and n_new == 1:
    skipped.append("path verifikasi sudah benar")
elif n_old == 1 and n_new == 0:
    text = text.replace(OLD, NEW)
    done.append("path second-stage di Verifikasi hasil")
else:
    die("path verifikasi tidak sesuai harapan (lama=%d, baru=%d)" % (n_old, n_new))

# validasi YAML bila PyYAML tersedia
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

print("".join(difflib.unified_diff(
    orig.splitlines(True), text.splitlines(True), "sebelum", "sesudah", n=2)))
for d in done:
    print("PERUBAHAN: " + d)

if APPLY:
    WF.write_text(text, encoding="utf-8")
    print("DITERAPKAN ke " + str(WF))
else:
    print("DRY-RUN: tidak ada yang ditulis. Jalankan lagi dengan --apply")
