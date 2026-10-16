import pathlib, shutil, sys
R = pathlib.Path("engines/termux-x11")
L = R / "lorie"
if not L.is_dir():
    sys.exit("GAGAL: engines/termux-x11/lorie tidak ada")
for b in ("java", "aidl"):
    base = L / "src/main" / b
    old = base / "com/termux/x11"
    new = base / "com/molina/suite/x11"
    if old.is_dir():
        new.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(old), str(new))
        (base / "com/termux").rmdir()
        print("pindah", old, "->", new)
PAIRS = [
    ('strcmp(__progname, "com.termux.x11")',
     'strncmp(__progname, "com.molina", 10)'),
    ("com.termux.x11", "com.molina.suite.x11"),
    ("com/termux/x11", "com/molina/suite/x11"),
    ("/data/data/com.termux/", "/data/data/com.molina.suite/"),
    ('<!ENTITY TERMUX_PACKAGE_NAME "com.termux">',
     '<!ENTITY TERMUX_PACKAGE_NAME "com.molina.suite">'),
    ('<!ENTITY TERMUX_APP_NAME "Termux">',
     '<!ENTITY TERMUX_APP_NAME "Molina Terminal">'),
    ("Termux:X11", "Molina X11"),
]
EXT = {".java", ".aidl", ".xml", ".cpp", ".h", ".c",
       ".gradle", ".pro", ".txt", ".cmake"}
changed = 0
for p in sorted(R.rglob("*")):
    if not p.is_file() or p.suffix not in EXT:
        continue
    s = p.read_text(encoding="utf-8")
    t = s
    for a, b in PAIRS:
        t = t.replace(a, b)
    if t != s:
        p.write_text(t, encoding="utf-8")
        changed += 1
print("berkas diubah:", changed)
for p in sorted(R.rglob("*")):
    if p.is_file() and p.suffix in EXT:
        for i, ln in enumerate(p.read_text().splitlines(), 1):
            if "com.termux" in ln:
                print("SISA", p, i, ln.strip()[:90])
