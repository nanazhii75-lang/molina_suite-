#!/usr/bin/env python3
"""Refactor namespace + branding untuk satu engine yang sudah di-vendor.

Default = DRY-RUN (tidak mengubah apa pun). Tambahkan --apply untuk menulis.
File lisensi (LICENSE/COPYRIGHT/NOTICE/COPYING) dan baris komentar hak cipta/lisensi
tidak pernah disentuh.
"""
import argparse
import collections
import os
import re
import shutil
import sys

TEXT_EXT = {
    ".java", ".kt", ".kts", ".gradle", ".xml", ".c", ".cc", ".cpp", ".h", ".hpp",
    ".mk", ".txt", ".sh", ".properties", ".pro", ".cmake", ".in", ".json",
}
SKIP_DIRS = {".git", "build", ".gradle", ".idea"}
SKIP_NAMES = {"LICENSE", "LICENSE.md", "LICENSE.txt", "COPYRIGHT", "COPYING", "NOTICE",
              "NOTICE.md", "UPSTREAM.txt"}
COMMENT_START = ("*", "//", "/*", "#", "<!--")
PROTECT = re.compile(r"(?i)copyright|licen[cs]e|\u00a9|spdx-|@author")
URL = re.compile(r"https?://[^\s\"'<>)\\]+")
STRUCT = re.compile(
    r"sharedUserId|applicationId|com\.android\.application|android\.intent\.category\.LAUNCHER"
    r"|android:name=\"\.app\.\w*Application\"|TERMUX_PACKAGE_NAME\s*\+|\$\{?TERMUX_PACKAGE_NAME"
)


def build_rules(a):
    old = re.escape(a.old_pkg)
    old_us = "Java_" + a.old_pkg.replace(".", "_") + "_"
    new_us = "Java_" + a.new_pkg.replace(".", "_") + "_"
    return [
        ("jni_symbol", re.compile(re.escape(old_us)), new_us),
        ("jni_slash", re.compile(r"(?<!\.)" + re.escape(a.old_pkg.replace(".", "/")) + "/"),
         a.new_pkg.replace(".", "/") + "/"),
        ("package_decl", re.compile(r"^(\s*package\s+)" + old + r"(\s*;)"),
         lambda m: m.group(1) + a.new_pkg + m.group(2)),
        ("manifest_package", re.compile(r'(package=")' + old + r'(")'),
         lambda m: m.group(1) + a.new_pkg + m.group(2)),
        ("runtime_literal", re.compile(r"([\"'])" + old + r"\1"),
         lambda m: m.group(1) + a.runtime_pkg + m.group(1)),
        ("java_package", re.compile(old + r"(?=\.[A-Za-z_])"), a.new_pkg),
        ("runtime_path", re.compile(old + r"(?![\w.])"), a.runtime_pkg),
    ]


def is_protected(line):
    s = line.lstrip()
    return s.startswith(COMMENT_START) and bool(PROTECT.search(line))


def read(path):
    with open(path, "r", encoding="utf-8", errors="surrogateescape", newline="") as f:
        return f.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", errors="surrogateescape", newline="") as f:
        f.write(text)


def iter_files(root):
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for n in files:
            if n in SKIP_NAMES or os.path.splitext(n)[1].lower() not in TEXT_EXT:
                continue
            yield os.path.join(d, n)


def plan_dir_moves(root, a):
    old_parts = a.old_pkg.split(".")
    new_parts = a.new_pkg.split(".")
    moves = []
    for d, dirs, _ in os.walk(root):
        dirs[:] = [x for x in dirs if x not in SKIP_DIRS]
        for x in list(dirs):
            full = os.path.join(d, x)
            tail = full.replace(os.sep, "/").split("/")
            if tail[-len(old_parts):] == old_parts and tail[-len(old_parts) - 1] in ("java", "kotlin"):
                base = os.sep.join(tail[:-len(old_parts)])
                moves.append((full, os.path.join(base, *new_parts)))
                dirs.remove(x)
    return moves


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--dir", default="engines/termux")
    p.add_argument("--old-pkg", default="com.termux")
    p.add_argument("--new-pkg", default="com.molina.suite.terminal")
    p.add_argument("--runtime-pkg", default="com.molina.suite",
                   help="applicationId sebenarnya (menentukan /data/data/<id>/)")
    p.add_argument("--brand-from", default="Termux")
    p.add_argument("--brand-to", default="Molina Terminal")
    p.add_argument("--apply", action="store_true")
    a = p.parse_args()

    root = os.path.abspath(a.dir)
    if not os.path.isdir(root):
        sys.exit("GAGAL: folder tidak ada: " + root)

    rules = build_rules(a)
    brand = re.compile(r"(?<![\w/.:-])" + re.escape(a.brand_from) + r"(?![\w:-])")
    counts = collections.Counter()
    samples = collections.defaultdict(list)
    urls = collections.defaultdict(set)
    struct = []
    leftovers = []
    changed_files = 0

    moves = plan_dir_moves(root, a)
    for src, dst in moves:
        if os.path.exists(dst):
            sys.exit("GAGAL: tujuan sudah ada, selesaikan manual: " + dst)

    for path in iter_files(root):
        rel = os.path.relpath(path, root)
        text = read(path)
        is_strings = bool(re.search(r"res/values[^/]*/strings[^/]*\.xml$", path.replace(os.sep, "/")))
        is_manifest = os.path.basename(path) == "AndroidManifest.xml"
        out = []
        file_changed = False
        for n, line in enumerate(text.splitlines(keepends=True), 1):
            new = line
            if not is_protected(line):
                for name, rx, rep in rules:
                    if name == "jni_slash" and "://" in new:
                        continue  # jangan rusak path URL di komentar/string
                    new, k = rx.subn(rep, new)
                    if k:
                        counts[name] += k
                if is_strings or (is_manifest and "android:label" in line):
                    new, k = brand.subn(a.brand_to, new)
                    if k:
                        counts["branding"] += k
            if new != line:
                file_changed = True
                if len(samples[rel]) < 3:
                    samples[rel].append("%d: %s" % (n, new.strip()[:110]))
            for u in URL.findall(new):
                if "termux" in u.lower():
                    urls[u.rstrip(".,;")].add(rel)
            if STRUCT.search(new):
                struct.append("%s:%d: %s" % (rel, n, new.strip()[:110]))
            if a.old_pkg in new and not is_protected(new):
                leftovers.append("%s:%d: %s" % (rel, n, new.strip()[:110]))
            out.append(new)
        if file_changed:
            changed_files += 1
            if a.apply:
                write(path, "".join(out))

    if a.apply:
        for src, dst in moves:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.move(src, dst)

    lines = []
    lines.append("=== MODE: %s | dir: %s ===" % ("APPLY" if a.apply else "DRY-RUN", root))
    lines.append("Pindah direktori paket: %d" % len(moves))
    for s, d in moves:
        lines.append("  %s -> %s" % (os.path.relpath(s, root), os.path.relpath(d, root)))
    lines.append("File berubah: %d" % changed_files)
    for k, v in sorted(counts.items()):
        lines.append("  %-18s %d" % (k, v))
    lines.append("\n--- CONTOH PERUBAHAN ---")
    for rel, ss in list(samples.items())[:25]:
        lines.append(rel)
        lines.extend("    " + s for s in ss)
    lines.append("\n--- REVIEW STRUKTURAL (perlu keputusan manual) ---")
    lines.extend(sorted(set(struct)) or ["(kosong)"])
    lines.append("\n--- REVIEW URL berisi 'termux' (jangan ganti buta; ada yang fungsional) ---")
    for u, fs in sorted(urls.items()):
        lines.append("%s  [%s%s]" % (u, ", ".join(sorted(fs)[:2]), " ..." if len(fs) > 2 else ""))
    if not urls:
        lines.append("(kosong)")
    lines.append("\n--- SISA '%s' SETELAH REFACTOR ---" % a.old_pkg)
    lines.extend(leftovers[:60] or ["(kosong)"])
    report = "\n".join(lines)
    print(report)
    if a.apply:
        rd = os.path.join(os.getcwd(), "docs", "refactor-reports")
        os.makedirs(rd, exist_ok=True)
        write(os.path.join(rd, os.path.basename(root) + ".txt"), report + "\n")


if __name__ == "__main__":
    main()
