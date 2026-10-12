#!/usr/bin/env python3
"""Selaraskan engine termux dengan bootstrap berprefix com.molina.suite (Fase 1b)."""
import difflib
import pathlib
import sys

APPLY = "--apply" in sys.argv
ROOT = pathlib.Path("engines/termux")
INSTALLER = ROOT / "app/src/main/java/com/molina/suite/terminal/app/TermuxInstaller.java"
SHELLUTILS = ROOT / "termux-shared/src/main/java/com/molina/suite/terminal/shared/shell/TermuxShellUtils.java"
CHANGES = ROOT / "MOLINA-CHANGES.md"

OLD = "etc/termux/bootstrap/termux-bootstrap-second-stage.sh"
NEW = "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
ANCHOR = "if (TERMUX_VERSION_NAME != null)"
MARK = "TERMUX__USER_ID"
NOTE_MARK = "Fase 1b: selaraskan engine dengan bootstrap"
NOTE = (
    "\n## Fase 1b: selaraskan engine dengan bootstrap berprefix com.molina.suite\n"
    "- `app/.../TermuxInstaller.java`: path second stage diganti ke "
    "`etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh` "
    "(chmod saat ekstraksi dan eksekusi), sesuai bootstrap hasil build termux-packages terbaru.\n"
    "- `termux-shared/.../shell/TermuxShellUtils.java`: environment shell mengekspor "
    "`TERMUX__USER_ID` (`Process.myUid() / 100000`), karena skrip termux-tools terbaru memakai "
    "`am --user \"$TERMUX__USER_ID\"`.\n"
)


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def read(p):
    if not p.is_file():
        die(str(p) + " tidak ada; jalankan dari root repo")
    with open(p, encoding="utf-8", newline="") as f:
        return f.read()


changes = {}
done, skipped = [], []

# 1. path second stage di TermuxInstaller.java
t = read(INSTALLER)
n_old, n_new = t.count(OLD), t.count(NEW)
if n_old == 0 and n_new == 2:
    skipped.append("path second stage sudah baru")
elif n_old == 2 and n_new == 0:
    changes[INSTALLER] = (t, t.replace(OLD, NEW))
    done.append("TermuxInstaller.java: 2 path second stage")
else:
    die("path second stage tidak sesuai harapan (lama=%d, baru=%d)" % (n_old, n_new))

# 2. ekspor TERMUX__USER_ID di TermuxShellUtils.java
t = read(SHELLUTILS)
if MARK in t:
    skipped.append("TERMUX__USER_ID sudah ada di TermuxShellUtils.java")
else:
    lines = t.splitlines(True)
    idx = [i for i, ln in enumerate(lines) if ln.strip() == ANCHOR]
    if len(idx) != 1:
        die("baris jangkar '%s' harus tepat 1, ditemukan %d" % (ANCHOR, len(idx)))
    i = idx[0]
    if i + 1 >= len(lines) or "environment.add(" not in lines[i + 1]:
        die("baris setelah jangkar bukan environment.add(...); periksa manual")
    ln = lines[i]
    indent = ln[: len(ln) - len(ln.lstrip())]
    nl = "\r\n" if ln.endswith("\r\n") else "\n"
    lines[i:i] = [
        indent + "// molina: skrip termux-tools terbaru memakai TERMUX__USER_ID untuk am --user" + nl,
        indent + 'environment.add("TERMUX__USER_ID=" + (android.os.Process.myUid() / 100000));' + nl,
    ]
    changes[SHELLUTILS] = (t, "".join(lines))
    done.append("TermuxShellUtils.java: ekspor TERMUX__USER_ID")

# 3. catatan perubahan
if CHANGES.is_file():
    t = read(CHANGES)
    if NOTE_MARK in t:
        skipped.append("catatan MOLINA-CHANGES.md sudah ada")
    else:
        sep = "" if t.endswith("\n") else "\n"
        changes[CHANGES] = (t, t + sep + NOTE)
        done.append("MOLINA-CHANGES.md: catatan Fase 1b")
else:
    skipped.append("MOLINA-CHANGES.md tidak ada, catatan dilewati")

for s in skipped:
    print("LEWAT: " + s)
if not changes:
    print("Tidak ada perubahan.")
    sys.exit(0)

for p, (a, b) in changes.items():
    print("".join(difflib.unified_diff(
        a.splitlines(True), b.splitlines(True), "sebelum/" + str(p), "sesudah/" + str(p), n=2)))
for d in done:
    print("PERUBAHAN: " + d)

if APPLY:
    for p, (a, b) in changes.items():
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(b)
    print("DITERAPKAN")
else:
    print("DRY-RUN: jalankan lagi dengan --apply")
