#!/usr/bin/env python3
"""Perbaikan: tab terminal baru harus dipasang ke tampilan sebelum menunggu pid, supaya shell
mulai. Idempotent. Jalankan dari akar repo (molina-suite)."""
import os
import sys

REL = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/TermuxFragment.java"
p = os.path.join(os.getcwd(), REL)
if not os.path.isfile(p):
    sys.exit("GAGAL: berkas tidak ditemukan: " + REL + " (jalankan dari akar repo)")
with open(p, encoding="utf-8") as f:
    text = f.read()

MARKER = "dipasang ke tampilan dulu supaya shell mulai"
old = (
    "        if (target.getPid() <= 0) return \"Tab terminal baru belum siap\";\n"
    "        mTermuxTerminalSessionClient.setCurrentSession(target);\n"
)
new = (
    "        // Sesi baru harus dipasang ke tampilan dulu supaya shell mulai (pid baru ada setelah itu).\n"
    "        mTermuxTerminalSessionClient.setCurrentSession(target);\n"
    "        if (target.getPid() <= 0) return \"Tab terminal baru belum siap\";\n"
)
if MARKER in text:
    print("lewati (sudah diperbaiki)")
    sys.exit(0)
if text.count(old) != 1:
    sys.exit("GAGAL: pola tidak unik/tidak ketemu; kirim isi writeLineInWorkdir ke Claude")
text = text.replace(old, new).replace(
    "Sesi baru harus dipasang ke tampilan dulu supaya shell mulai",
    "Sesi baru harus " + MARKER)
with open(p, "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("diubah:", REL)
