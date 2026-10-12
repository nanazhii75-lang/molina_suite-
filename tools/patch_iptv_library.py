#!/usr/bin/env python3
"""Membundel playlist IPTV Indonesia (iptv-org) sebagai entri bawaan Library "IPTV Indonesia".

Idempotent: playlist diunduh, dibuat jadi IptvIndonesia.kt (ditulis ulang hanya bila berubah),
lalu BuiltinLibrary.entries ditambah satu referensi bila belum ada. Jalankan dari akar repo.
"""
import re
import sys
import urllib.request
from pathlib import Path

SOURCES = [
    "https://iptv-org.github.io/iptv/countries/id.m3u",
    "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/id.m3u",
]
LIB = Path("features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/library")
BUILTIN = LIB / "BuiltinLibrary.kt"
TARGET = LIB / "IptvIndonesia.kt"
ANCHOR = "val entries: List<LibraryEntry> = listOf(\n"
MARK = "IptvIndonesia.entry"


def fetch() -> str:
    errors = []
    for url in SOURCES:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "molina-suite-patch/1.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                text = resp.read().decode("utf-8")
        except Exception as e:  # jaringan, DNS, TLS, decode
            errors.append(f"{url}: {e}")
            continue
        text = text.replace("\r\n", "\n").replace("\r", "\n").strip("\n")
        if text.startswith("#EXTM3U") and text.count("#EXTINF") > 0:
            print(f"Sumber: {url} ({text.count('#EXTINF')} channel)")
            return text
        errors.append(f"{url}: bukan playlist M3U yang valid")
    sys.exit("Gagal mengunduh playlist:\n  " + "\n  ".join(errors))


def kt(line: str) -> str:
    return '"' + line.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


TEMPLATE = '''package com.molina.suite.feature.settings.library

import com.molina.suite.core.storage.MolinaStorage

/**
 * Entri bawaan "IPTV Indonesia": menulis playlist iptv-org ke folder bersama lalu membukanya
 * di mpv. Isi playlist dihasilkan tools/patch_iptv_library.py; boleh disunting lewat editor.
 */
internal object IptvIndonesia {

    const val ENTRY_ID = "builtin-iptv-indonesia"
    private const val MPV_PACKAGE = "com.molina.suite"
    private const val DELIMITER = "MOLINA_IPTV_EOF"
    private val dir = "/sdcard/${MolinaStorage.SHARED_DIR_NAME}/iptv"
    private val file = "$dir/indonesia.m3u"

    private val playlist: List<String> = listOf(
@@BODY@@
    )

    private val script: String = buildString {
        append("mkdir -p '").append(dir).append("' || exit 1\\n")
        append("cat > '").append(file).append("' << '").append(DELIMITER).append("'\\n")
        playlist.forEach { append(it).append('\\n') }
        append(DELIMITER).append('\\n')
        append("[ -s '").append(file).append("' ] || { echo 'Gagal menulis playlist'; exit 1; }\\n")
        append("echo 'IPTV Indonesia: ").append(playlist.count { it.startsWith("#EXTINF") })
        append(" channel di ").append(file).append("'\\n")
        append("am start -a android.intent.action.VIEW -d 'file://").append(file)
        append("' -t 'video/*' -p ").append(MPV_PACKAGE)
    }

    val entry: LibraryEntry = LibraryEntry(
        id = ENTRY_ID,
        name = "IPTV Indonesia",
        content = script,
        note = "Daftar channel TV Indonesia (iptv-org) dibuka di mpv; pindah channel lewat playlist mpv. " +
            "Sebagian channel butuh referrer atau user-agent khusus dan mungkin tidak tersambung.",
        runIn = RunMode.ANDROID,
        builtin = true
    )
}
'''


def render(lines) -> str:
    body = ",\n".join("        " + kt(l) for l in lines if l.strip())
    return TEMPLATE.replace("@@BODY@@", body)



def main() -> None:
    if not BUILTIN.is_file():
        sys.exit(f"Tidak ditemukan: {BUILTIN} (jalankan dari akar repo)")
    code = render(fetch().split("\n"))
    if not TARGET.is_file() or TARGET.read_text(encoding="utf-8") != code:
        TARGET.write_text(code, encoding="utf-8")
        print(f"Ditulis: {TARGET}")
    else:
        print(f"Tidak berubah: {TARGET}")

    src = BUILTIN.read_text(encoding="utf-8")
    if MARK in src:
        print("BuiltinLibrary sudah memuat IptvIndonesia.entry")
        return
    if src.count(ANCHOR) != 1:
        sys.exit("Jangkar 'val entries ... listOf(' tidak ditemukan tepat satu kali di BuiltinLibrary.kt")
    BUILTIN.write_text(src.replace(ANCHOR, ANCHOR + "        IptvIndonesia.entry,\n"), encoding="utf-8")
    print("BuiltinLibrary diperbarui")


main()
