#!/usr/bin/env python3
"""Log perintah shell yang dijalankan aplikasi sendiri (poin 1, lapis 1).

Menambah LoggingShellCommandRunner dan ShellLogFormat di core-common, lalu membungkus
ShellCommandRunner di MolinaApplication. Setiap perintah dicatat ke logcat (tag MolinaShell):
mulai, kode keluar, lama, dan ekor keluaran bila gagal; argumen tidak pernah dicatat.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_shell_log.py [--apply]
"""
import argparse
import difflib
import os
import sys


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def load(path):
    with open(path, "rb") as f:
        raw = f.read().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n"), eol


def save(path, text, eol):
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


APPLICATION = "app/src/main/kotlin/com/molina/suite/MolinaApplication.kt"

NEW_FILES = {
    "core/core-common/src/main/kotlin/com/molina/suite/core/common/LoggingShellCommandRunner.kt": r'''package com.molina.suite.core.common

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pembungkus [ShellCommandRunner] yang mencatat setiap perintah ke logcat dengan tag
 * [LOG_TAG], sehingga ikut terkumpul di layar Logcat bersama log mpv, Terminal, dan Code.
 * Perilaku dan hasil perintah tidak diubah.
 *
 * Yang dicatat: mulai, kode keluar, lama berjalan, dan (bila kode keluar bukan nol) beberapa baris
 * terakhir keluaran. Isi argumen tidak pernah dicatat karena bisa memuat skrip panjang atau data
 * pribadi; pengenalnya adalah [ShellCommand.label].
 *
 * [write] menerima prioritas [Log] dan satu baris pesan; diganti hanya saat diuji.
 */
class LoggingShellCommandRunner(
    private val delegate: ShellCommandRunner,
    private val write: (priority: Int, message: String) -> Unit = { priority, message ->
        Log.println(priority, LOG_TAG, message)
    }
) : ShellCommandRunner {

    override suspend fun runInBackground(command: ShellCommand): ShellResult {
        val name = ShellLogFormat.name(command)
        write(Log.INFO, "[$name] mulai: ${command.executable.substringAfterLast('/')}")
        val startedAt = System.nanoTime()
        val result = try {
            delegate.runInBackground(command)
        } catch (e: CancellationException) {
            write(Log.WARN, "[$name] dibatalkan; proses yang sudah berjalan tidak dihentikan")
            throw e
        }
        val seconds = ShellLogFormat.seconds(System.nanoTime() - startedAt)
        when (result) {
            is ShellResult.Failed ->
                write(Log.ERROR, "[$name] gagal dijalankan setelah $seconds: ${result.reason}")
            is ShellResult.Finished -> if (result.exitCode == 0) {
                write(Log.INFO, "[$name] selesai kode 0 ($seconds)")
            } else {
                write(Log.WARN, "[$name] selesai kode ${result.exitCode} ($seconds)")
                val output = ShellLogFormat.tail(result.stderr.ifBlank { result.stdout })
                output.forEach { write(Log.WARN, "[$name] | $it") }
            }
        }
        return result
    }

    companion object {
        /** Tag logcat semua perintah shell yang dijalankan aplikasi sendiri. */
        const val LOG_TAG = "MolinaShell"
    }
}
''',
    "core/core-common/src/main/kotlin/com/molina/suite/core/common/ShellLogFormat.kt": r'''package com.molina.suite.core.common

import java.util.Locale

/** Penyusun teks log perintah shell; murni, tanpa Android, supaya mudah diuji. */
internal object ShellLogFormat {

    const val MAX_LINES = 12
    const val MAX_LINE_CHARS = 300
    private const val UNNAMED = "tanpa label"

    private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /** Pengenal perintah di log: labelnya satu baris, atau nama berkas executable bila label kosong. */
    fun name(command: ShellCommand): String {
        val label = command.label.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (label.isNotEmpty()) return label.take(MAX_LINE_CHARS)
        return command.executable.substringAfterLast('/').ifEmpty { UNNAMED }
    }

    /** Lama dalam detik dengan satu desimal, mis. "1.2 dtk"; selalu memakai titik desimal. */
    fun seconds(nanos: Long): String =
        String.format(Locale.ROOT, "%.1f dtk", nanos.coerceAtLeast(0L) / 1_000_000_000.0)

    /**
     * Sampai [MAX_LINES] baris terakhir [text] yang tidak kosong, tanpa kode warna ANSI dan
     * carriage return, tiap baris dipotong di [MAX_LINE_CHARS] karakter.
     */
    fun tail(text: String): List<String> =
        text.replace("\r", "\n")
            .lineSequence()
            .map { ANSI.replace(it, "").trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(MAX_LINES)
            .map { if (it.length > MAX_LINE_CHARS) it.take(MAX_LINE_CHARS) + "…" else it }
}
''',
}

EDITS = [
    (
        APPLICATION,
        [
            (
                "import com.molina.suite.core.common.EngineRegistry\n",
                "import com.molina.suite.core.common.EngineRegistry\n"
                "import com.molina.suite.core.common.LoggingShellCommandRunner\n",
                1,
            ),
            (
                "    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai. */\n"
                "    val shell: ShellCommandRunner by lazy { TerminalEngineModule.createShellRunner(this) }\n",
                "    /** Pelaksana perintah shell untuk modul lain; dibuat saat pertama dipakai, setiap perintahnya dicatat ke logcat. */\n"
                "    val shell: ShellCommandRunner by lazy {\n"
                "        LoggingShellCommandRunner(TerminalEngineModule.createShellRunner(this))\n"
                "    }\n",
                1,
            ),
        ],
        "LoggingShellCommandRunner(",
    ),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    pending = []

    for path, content in NEW_FILES.items():
        if os.path.exists(path):
            existing, _ = load(path)
            if existing == content:
                print("SUDAH: " + path)
                continue
            die(path + " sudah ada dengan isi berbeda; periksa manual")
        print("BARU: %s (%d baris)" % (path, content.count("\n")))
        pending.append((path, content, "\n"))

    for path, edits, marker in EDITS:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        if marker in text:
            print("SUDAH: " + path)
            continue
        new = text
        for old, rep, count in edits:
            found = new.count(old)
            if found != count:
                die("%s: potongan harus %d kali, ditemukan %d:\n%s" % (path, count, found, old))
            new = new.replace(old, rep)
        diff = difflib.unified_diff(
            text.splitlines(), new.splitlines(),
            "sebelum/" + path, "sesudah/" + path, lineterm="", n=1,
        )
        print("\n".join(diff))
        pending.append((path, new, eol))

    if not pending:
        print("Tidak ada perubahan (sudah diterapkan).")
        return

    if not args.apply:
        print("DRY-RUN: jalankan lagi dengan --apply")
        return

    for path, text, eol in pending:
        save(path, text, eol)
    print("DITERAPKAN: %d berkas" % len(pending))


if __name__ == "__main__":
    main()
