#!/usr/bin/env python3
"""Patch build-bootstrap.yml: rilis uji berstatus pre-release dan cetak URL serta sha256."""
import difflib
import pathlib
import sys

APPLY = "--apply" in sys.argv
WF = pathlib.Path(".github/workflows/build-bootstrap.yml")


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


if not WF.is_file():
    die(str(WF) + " tidak ada; jalankan dari root repo")
orig = WF.read_text(encoding="utf-8")
lines = orig.splitlines(True)
done, skipped = [], []

if "--prerelease" in orig:
    skipped.append("--prerelease sudah ada")
else:
    idx = [i for i, ln in enumerate(lines) if ln.strip() == '--repo "$GITHUB_REPOSITORY" \\']
    if len(idx) != 1:
        die('baris --repo "$GITHUB_REPOSITORY" harus tepat 1, ditemukan %d' % len(idx))
    i = idx[0]
    ind = lines[i][: len(lines[i]) - len(lines[i].lstrip())]
    lines.insert(i + 1, ind + "--prerelease \\\n")
    done.append("--prerelease pada gh release create")

text_now = "".join(lines)
if 'echo "URL=https://github.com/$GITHUB_REPOSITORY/releases/download/' in text_now:
    skipped.append("cetak URL sudah ada")
else:
    idx = [i for i, ln in enumerate(lines) if ln.strip() == 'echo "RELEASE_TAG=$TAG"']
    if len(idx) != 1:
        die('baris echo "RELEASE_TAG=$TAG" harus tepat 1, ditemukan %d' % len(idx))
    i = idx[0]
    ind = lines[i][: len(lines[i]) - len(lines[i].lstrip())]
    lines.insert(i + 1, ind + 'echo "URL=https://github.com/$GITHUB_REPOSITORY/releases/download/$TAG/bootstrap-aarch64.zip"\n')
    lines.insert(i + 2, ind + "cat bootstrap-aarch64.zip.sha256\n")
    done.append("cetak URL dan sha256 di log")

new = "".join(lines)
try:
    import yaml
    yaml.safe_load(new)
    print("YAML valid")
except ImportError:
    print("(PyYAML tidak ada, validasi YAML dilewati)")
except Exception as e:
    die("bukan YAML valid: %s" % e)

for s in skipped:
    print("LEWAT: " + s)
if new == orig:
    print("Tidak ada perubahan.")
    sys.exit(0)
print("".join(difflib.unified_diff(orig.splitlines(True), new.splitlines(True), "sebelum", "sesudah", n=2)))
for d in done:
    print("PERUBAHAN: " + d)
if APPLY:
    WF.write_text(new, encoding="utf-8")
    print("DITERAPKAN")
else:
    print("DRY-RUN: jalankan lagi dengan --apply")
