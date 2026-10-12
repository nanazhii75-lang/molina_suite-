#!/usr/bin/env python3
"""Pasang bootstrap molina (release repo sendiri) ke engines/termux/app/build.gradle."""
import argparse
import difflib
import pathlib
import re
import sys

ap = argparse.ArgumentParser()
ap.add_argument("--tag", required=True)
ap.add_argument("--sha256", required=True)
ap.add_argument("--apply", action="store_true")
a = ap.parse_args()

BG = pathlib.Path("engines/termux/app/build.gradle")
CHANGES = pathlib.Path("engines/termux/MOLINA-CHANGES.md")
REPO = "nanazhii75-lang/molina_suite-"
OLD_URL = 'def remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-" + version + "/bootstrap-" + arch + ".zip"'
NEW_URL = 'def remoteUrl = "https://github.com/%s/releases/download/" + version + "/bootstrap-" + arch + ".zip"' % REPO
NOTE_MARK = "Fase 1b: sumber bootstrap"
NOTE = (
    "\n## Fase 1b: sumber bootstrap\n"
    "- `app/build.gradle`: bootstrap diunduh dari release repo molina_suite- (prefix com.molina.suite), "
    "bukan dari termux-packages. Baru aarch64; arsitektur lain di-comment dan `abiFilters` hanya `arm64-v8a` "
    "sampai bootstrap arm, i686, dan x86_64 tersedia.\n"
)


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


tag = a.tag.split("=")[-1].strip()
sha = a.sha256.split()[0].strip().lower()
if not re.fullmatch(r"bootstrap-molina-\d{4}\.\d{2}\.\d{2}-r\d+", tag):
    die("tag tidak sesuai pola bootstrap-molina-YYYY.MM.DD-rN: " + tag)
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    die("sha256 harus 64 karakter heksadesimal: " + sha)
if not BG.is_file():
    die(str(BG) + " tidak ada; jalankan dari root repo")

with open(BG, encoding="utf-8", newline="") as f:
    orig = f.read()
text = orig
done, skipped = [], []


def sub1(pattern, repl, label):
    global text
    new, n = re.subn(pattern, repl, text, flags=re.M)
    if n != 1:
        die("%s: ditemukan %d, harus 1" % (label, n))
    text = new


# 1. URL unduhan
if OLD_URL in text:
    text = text.replace(OLD_URL, NEW_URL)
    done.append("URL unduhan ke release " + REPO)
elif NEW_URL in text:
    skipped.append("URL unduhan sudah ke repo molina")
else:
    die("baris remoteUrl tidak ditemukan dalam bentuk yang kuharapkan")

# 2. version dan checksum aarch64
sub1(r'^([ \t]*)def version = "[^"]*"[ \t]*$',
     lambda m: m.group(1) + 'def version = "%s"' % tag, "baris version")
sub1(r'^([ \t]*)downloadBootstrap\("aarch64", "[0-9a-f]{64}", version\)[ \t]*$',
     lambda m: m.group(1) + 'downloadBootstrap("aarch64", "%s", version)' % sha, "baris aarch64")
done.append("version=%s, checksum aarch64=%s" % (tag, sha[:8] + "..."))

# 3. arsitektur lain di-comment
active = re.findall(r'^[ \t]*downloadBootstrap\("(?:arm|i686|x86_64)",', text, re.M)
if len(active) == 3:
    text = re.sub(r'^([ \t]*)(downloadBootstrap\("(?:arm|i686|x86_64)",.*)$',
                  lambda m: m.group(1) + "// " + m.group(2), text, flags=re.M)
    text = re.sub(r'^([ \t]*)(// downloadBootstrap\("arm",)',
                  lambda m: m.group(1) + "// Fase 1b: bootstrap molina baru tersedia untuk aarch64; arsitektur lain menyusul.\n"
                  + m.group(1) + m.group(2), text, count=1, flags=re.M)
    done.append("arm, i686, x86_64 di-comment")
elif len(active) == 0 and len(re.findall(r'^[ \t]*// downloadBootstrap\("', text, re.M)) == 3:
    skipped.append("arsitektur lain sudah di-comment")
else:
    die("panggilan downloadBootstrap arm/i686/x86_64 aktif=%d, harus 3 atau 0" % len(active))

# 4. abiFilters
OLD_ABI = "abiFilters 'x86', 'x86_64', 'armeabi-v7a', 'arm64-v8a'"
if OLD_ABI in text and text.count(OLD_ABI) == 1:
    text = text.replace(OLD_ABI, "abiFilters 'arm64-v8a'")
    done.append("abiFilters hanya arm64-v8a")
elif "abiFilters 'arm64-v8a'" in text:
    skipped.append("abiFilters sudah arm64-v8a")
else:
    die("baris abiFilters tidak ditemukan dalam bentuk yang kuharapkan")

if text.count("{") != orig.count("{") or text.count("}") != orig.count("}"):
    die("jumlah kurung kurawal berubah; periksa manual")

changes = {BG: (orig, text)}
if CHANGES.is_file():
    with open(CHANGES, encoding="utf-8", newline="") as f:
        c = f.read()
    if NOTE_MARK in c:
        skipped.append("catatan MOLINA-CHANGES.md sudah ada")
    else:
        changes[CHANGES] = (c, c + ("" if c.endswith("\n") else "\n") + NOTE)
        done.append("catatan di MOLINA-CHANGES.md")

for s in skipped:
    print("LEWAT: " + s)
if all(x == y for x, y in changes.values()):
    print("Tidak ada perubahan.")
    sys.exit(0)
for p, (x, y) in changes.items():
    print("".join(difflib.unified_diff(x.splitlines(True), y.splitlines(True),
                                       "sebelum/" + str(p), "sesudah/" + str(p), n=2)))
for d in done:
    print("PERUBAHAN: " + d)
if a.apply:
    for p, (x, y) in changes.items():
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(y)
    print("DITERAPKAN")
else:
    print("DRY-RUN: jalankan lagi dengan --apply")
