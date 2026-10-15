#!/usr/bin/env python3
"""Lanjutan patch_code_run_workdir.py: folder yang sama tidak pernah membuka tab baru.

Bila tab untuk folder itu masih menjalankan proses, tab tersebut tetap dipakai: Ctrl+C
dikirim untuk menghentikan proses lama, lalu perintah baru diketik begitu shell idle
(runner mengulang tiap 500 ms sampai 10 detik). Folder berbeda tetap membuka tab baru.
Jalankan SETELAH patch_code_run_workdir.py, dari akar repo. Idempotent.
"""
import sys
from pathlib import Path

rel = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/TermuxFragment.java"
path = Path.cwd() / rel
if not path.is_file():
    sys.exit("Berkas tidak ada: " + rel)
text = path.read_text(encoding="utf-8")
if "busy = candidate" in text:
    print("lewati (sudah ada): " + rel)
    sys.exit(0)
if "writeLineInWorkdir" not in text:
    sys.exit("Jalankan dulu patch_code_run_workdir.py")


def once(src, old, new):
    if src.count(old) != 1:
        sys.exit("Jangkar tidak unik/tidak ditemukan: " + old[:60])
    return src.replace(old, new)


text = once(
    text,
    "        TerminalSession reusable = null;\n        boolean pendingStart = false;\n",
    "        TerminalSession reusable = null;\n        TerminalSession busy = null;\n        boolean pendingStart = false;\n",
)
text = once(
    text,
    "                } else if (reusable == null && isShellInForeground(candidate.getPid())) {\n"
    "                    reusable = candidate;\n"
    "                }\n",
    "                } else if (isShellInForeground(candidate.getPid())) {\n"
    "                    if (reusable == null) reusable = candidate;\n"
    "                } else if (busy == null) {\n"
    "                    busy = candidate;\n"
    "                }\n",
)
text = once(
    text,
    "        if (reusable == null && pendingStart) return \"Tab terminal baru belum siap\";\n",
    "        if (reusable == null && pendingStart) return \"Tab terminal baru belum siap\";\n"
    "        if (reusable == null && busy != null) {\n"
    "            // Folder sama: tetap di tab ini. Hentikan proses lama (Ctrl+C), lalu pemanggil mengulang.\n"
    "            mTermuxTerminalSessionClient.setCurrentSession(busy);\n"
    "            busy.write(new byte[]{3}, 0, 1);\n"
    "            return \"Menghentikan proses di tab ini\";\n"
    "        }\n",
)
path.write_text(text, encoding="utf-8")
print("diubah: " + rel)
