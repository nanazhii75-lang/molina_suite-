#!/usr/bin/env python3
"""Log privat + catat perintah yang diketik ke Terminal (poin 1, lapis 2).

1. Ekspor logcat dan salinan crash tidak lagi ke /sdcard/Molina/Logs. Semuanya tinggal di folder
   privat aplikasi (files/logs untuk ekspor, files/crash untuk laporan crash) yang hanya bisa
   dibaca molina-suite; ekspor lama dipangkas (20 terbaru). MolinaStorage.logsDir dihapus.
2. TerminalCommandRunner mencatat tiap perintah yang diketik ke Terminal dengan label pemanggilnya
   (tag MolinaShell). Isi baris tidak pernah dicatat.

Prasyarat: tools/patch_shell_log.py sudah diterapkan (memakai LoggingShellCommandRunner.LOG_TAG).
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_log_private_terminal.py [--apply]
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
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


S = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/"
REQUIRED = "core/core-common/src/main/kotlin/com/molina/suite/core/common/LoggingShellCommandRunner.kt"

# (berkas, [(lama, baru, jumlah_harus_ada)], penanda)
# Penanda "!X" berarti: sudah diterapkan bila X tidak ada lagi di berkas.
EDITS = [
    (
        S + "TerminalCommandRunner.kt",
        [
            (
                "import android.app.Activity\n",
                "import android.app.Activity\nimport android.util.Log\n",
                1,
            ),
            (
                "import com.molina.suite.core.common.HostTabSwitcher\n",
                "import com.molina.suite.core.common.HostTabSwitcher\n"
                "import com.molina.suite.core.common.LoggingShellCommandRunner\n",
                1,
            ),
            (
                " * lebih dulu, dan menunggu sesi siap sampai 10 detik bila tab baru dibuka.\n */\n",
                " * lebih dulu, dan menunggu sesi siap sampai 10 detik bila tab baru dibuka. Setiap pengiriman\n"
                " * dicatat ke logcat dengan label pemanggilnya; isi baris sengaja tidak dicatat karena bisa\n"
                " * memuat data pribadi.\n */\n",
                1,
            ),
            (
                "    private const val RETRY_DELAY_MS = 500L\n",
                "    private const val RETRY_DELAY_MS = 500L\n"
                "    private const val MAX_LABEL_CHARS = 80\n"
                "    private const val LOG_TAG = LoggingShellCommandRunner.LOG_TAG\n",
                1,
            ),
            (
                "    /** Mengembalikan null bila terkirim, atau alasan kegagalan. Panggil dari main thread. */\n"
                "    suspend fun run(activity: Activity, line: String): String? {\n",
                "    /**\n"
                "     * Mengetik [line] ke Terminal dan mencatat hasilnya dengan [label]. Mengembalikan null bila\n"
                "     * terkirim, atau alasan kegagalan. Panggil dari main thread.\n"
                "     */\n"
                "    suspend fun run(activity: Activity, label: String, line: String): String? {\n"
                "        val name = logName(label)\n"
                "        val reason = deliver(activity, line)\n"
                "        if (reason == null) {\n"
                "            Log.i(LOG_TAG, \"[$name] diketik ke Terminal\")\n"
                "        } else {\n"
                "            Log.w(LOG_TAG, \"[$name] tidak terkirim ke Terminal: $reason\")\n"
                "        }\n"
                "        return reason\n"
                "    }\n"
                "\n"
                "    /** Label satu baris, dipotong, supaya log tetap rapi. */\n"
                "    private fun logName(label: String): String =\n"
                "        label.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()\n"
                "            .take(MAX_LABEL_CHARS)\n"
                "            .ifEmpty { \"tanpa label\" }\n"
                "\n"
                "    private suspend fun deliver(activity: Activity, line: String): String? {\n",
                1,
            ),
        ],
        "private suspend fun deliver",
    ),
    (
        S + "tools/ToolLauncher.kt",
        [
            (
                "                is ToolReadiness.Ready, is ToolReadiness.Unknown -> send(command, onStart, onFailed)\n",
                "                is ToolReadiness.Ready, is ToolReadiness.Unknown -> send(command, title, onStart, onFailed)\n",
                1,
            ),
            (
                "                send(installWrapper?.invoke(install) ?: \"$install && $command\", onStart, onFailed)\n",
                "                send(installWrapper?.invoke(install) ?: \"$install && $command\", title, onStart, onFailed)\n",
                1,
            ),
            (
                "    private fun send(inner: String, onStart: (() -> Unit)?, onFailed: (() -> Unit)?) {\n"
                "        if (runJob?.isActive == true) return\n"
                "        val host = fragment.activity ?: return\n",
                "    private fun send(\n"
                "        inner: String,\n"
                "        @StringRes title: Int,\n"
                "        onStart: (() -> Unit)?,\n"
                "        onFailed: (() -> Unit)?\n"
                "    ) {\n"
                "        if (runJob?.isActive == true) return\n"
                "        val host = fragment.activity ?: return\n"
                "        val label = fragment.getString(title)\n",
                1,
            ),
            (
                "            val reason = TerminalCommandRunner.run(host, line)\n",
                "            val reason = TerminalCommandRunner.run(host, label, line)\n",
                1,
            ),
        ],
        "send(command, title, onStart, onFailed)",
    ),
    (
        S + "debian/DebianFragment.kt",
        [
            (
                "        val host = activity ?: return\n"
                "        runJob = viewLifecycleOwner.lifecycleScope.launch {\n"
                "            val reason = TerminalCommandRunner.run(host, action.command)\n",
                "        val host = activity ?: return\n"
                "        val label = getString(action.title)\n"
                "        runJob = viewLifecycleOwner.lifecycleScope.launch {\n"
                "            val reason = TerminalCommandRunner.run(host, label, action.command)\n",
                1,
            ),
        ],
        "val label = getString(action.title)",
    ),
    (
        S + "library/LibraryFragment.kt",
        [
            (
                "            val reason = TerminalCommandRunner.run(host, line)\n",
                "            val reason = TerminalCommandRunner.run(host, \"Library: \" + entry.name, line)\n",
                1,
            ),
        ],
        "\"Library: \" + entry.name",
    ),
    (
        S + "packages/PackagesFragment.kt",
        [
            (
                "            install.setOnClickListener { runInTerminal(spec.installCommand()) }\n"
                "            update.setOnClickListener { runInTerminal(spec.updateCommand()) }\n",
                "            install.setOnClickListener {\n"
                "                runInTerminal(\"Paket: pasang \" + getString(spec.title), spec.installCommand())\n"
                "            }\n"
                "            update.setOnClickListener {\n"
                "                runInTerminal(\"Paket: perbarui \" + getString(spec.title), spec.updateCommand())\n"
                "            }\n",
                1,
            ),
            (
                "                runInTerminal(spec.removeCommand(protectedApt))\n",
                "                runInTerminal(\"Paket: hapus $name\", spec.removeCommand(protectedApt))\n",
                1,
            ),
            (
                "        runInTerminal(command)\n",
                "        runInTerminal(\"Paket: pasang semua yang kurang\", command)\n",
                1,
            ),
            (
                "    private fun runInTerminal(command: String) {\n",
                "    private fun runInTerminal(label: String, command: String) {\n",
                1,
            ),
            (
                "            val reason = TerminalCommandRunner.run(host, line)\n",
                "            val reason = TerminalCommandRunner.run(host, label, line)\n",
                1,
            ),
        ],
        "private fun runInTerminal(label: String",
    ),
    (
        S + "logcat/LogcatOutput.kt",
        [
            ("import com.molina.suite.core.storage.MolinaStorage\n", "", 1),
            (
                "import kotlinx.coroutines.Dispatchers\n",
                "import java.io.File\nimport kotlinx.coroutines.Dispatchers\n",
                1,
            ),
            (
                " * Keluaran layar Logcat: menyimpan ke /sdcard/Molina/Logs, menyalin baris terakhir, dan menampilkan\n"
                " * crash terakhir. Layar hanya memanggil metode di sini dan menampilkan pesan yang dikembalikan.\n",
                " * Keluaran layar Logcat: menyimpan ke folder log privat aplikasi (files/logs, hanya bisa dibaca\n"
                " * molina-suite), menyalin baris terakhir, dan menampilkan crash terakhir. Layar hanya memanggil\n"
                " * metode di sini dan menampilkan pesan yang dikembalikan.\n",
                1,
            ),
            (
                "        when (val ensured = MolinaStorage.ensureDirectories()) {\n"
                "            is MolinaStorage.EnsureResult.Failed ->\n"
                "                appContext.getString(R.string.logcat_save_failed, ensured.reason)\n"
                "            is MolinaStorage.EnsureResult.Ready ->\n"
                "                when (val result = LogcatExporter.save(entries, MolinaStorage.logsDir())) {\n"
                "                    is LogcatExporter.Result.Saved -> appContext.getString(\n"
                "                        R.string.logcat_saved,\n"
                "                        result.lines,\n"
                "                        MolinaStorage.SHARED_DIR_NAME + \"/\" + MolinaStorage.LOGS_DIR_NAME + \"/\" + result.file.name\n"
                "                    )\n"
                "                    is LogcatExporter.Result.Failed ->\n"
                "                        appContext.getString(R.string.logcat_save_failed, result.reason)\n"
                "                }\n"
                "        }\n",
                "        when (val result = LogcatExporter.save(entries, File(appContext.filesDir, EXPORT_DIR_NAME))) {\n"
                "            is LogcatExporter.Result.Saved -> appContext.getString(\n"
                "                R.string.logcat_saved,\n"
                "                result.lines,\n"
                "                result.file.absolutePath\n"
                "            )\n"
                "            is LogcatExporter.Result.Failed ->\n"
                "                appContext.getString(R.string.logcat_save_failed, result.reason)\n"
                "        }\n",
                1,
            ),
            (
                "        const val RECENT_LINES = 200\n",
                "        const val RECENT_LINES = 200\n        const val EXPORT_DIR_NAME = \"logs\"\n",
                1,
            ),
        ],
        "EXPORT_DIR_NAME",
    ),
    (
        S + "logcat/CrashRecorder.kt",
        [
            ("import com.molina.suite.core.storage.MolinaStorage\n", "", 1),
            (
                " * ke folder privat aplikasi (selalu bisa ditulis) dan disalin ke /sdcard/Molina/Logs bila\n"
                " * penyimpanan bersama siap. Setelah itu crash diteruskan ke penangan sebelumnya, jadi perilaku\n"
                " * aplikasi tidak berubah. Crash native (mis. di libmpv) tidak lewat sini.\n",
                " * ke folder privat aplikasi yang hanya bisa dibaca molina-suite. Setelah itu crash diteruskan\n"
                " * ke penangan sebelumnya, jadi perilaku aplikasi tidak berubah. Crash native (mis. di libmpv)\n"
                " * tidak lewat sini.\n",
                1,
            ),
            (
                "        val saved = CrashLogStore.write(crashDir(context), now, report)\n"
                "        copyToShared(saved?.name ?: CrashReport.fileName(now), report)\n",
                "        CrashLogStore.write(crashDir(context), now, report)\n",
                1,
            ),
            (
                "    /** Salinan best-effort ke folder bersama supaya mudah diambil; kegagalannya tidak dilaporkan. */\n"
                "    private fun copyToShared(name: String, report: String) {\n"
                "        try {\n"
                "            if (MolinaStorage.ensureDirectories() is MolinaStorage.EnsureResult.Ready) {\n"
                "                File(MolinaStorage.logsDir(), name).writeText(report, Charsets.UTF_8)\n"
                "            }\n"
                "        } catch (ignored: Exception) {\n"
                "            // Berkas di folder privat sudah cukup.\n"
                "        }\n"
                "    }\n"
                "\n",
                "",
                1,
            ),
        ],
        "yang hanya bisa dibaca molina-suite",
    ),
    (
        S + "logcat/LogcatExporter.kt",
        [
            (
                "    private const val FILE_PREFIX = \"logcat-\"\n",
                "    private const val FILE_PREFIX = \"logcat-\"\n"
                "\n"
                "    /** Jumlah ekspor terbaru yang dipertahankan di folder; yang lebih lama dihapus. */\n"
                "    private const val KEEP = 20\n",
                1,
            ),
            (
                "            file.writeText(text(entries, time), Charsets.UTF_8)\n",
                "            file.writeText(text(entries, time), Charsets.UTF_8)\n"
                "            prune(dir)\n",
                1,
            ),
            (
                "    private fun stamp(time: Date): String",
                "    private fun prune(dir: File) {\n"
                "        val exports = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(\".txt\") }\n"
                "            ?: return\n"
                "        exports\n"
                "            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })\n"
                "            .drop(KEEP)\n"
                "            .forEach { it.delete() }\n"
                "    }\n"
                "\n"
                "    private fun stamp(time: Date): String",
                1,
            ),
        ],
        "private fun prune(dir: File)",
    ),
    (
        S + "logcat/LogcatFragment.kt",
        [
            (
                "    /** Menyimpan baris yang sedang tampil ke /sdcard/Molina/Logs. */\n",
                "    /** Menyimpan baris yang sedang tampil ke folder log privat aplikasi. */\n",
                1,
            ),
        ],
        "folder log privat aplikasi",
    ),
    (
        "core/core-storage/src/main/kotlin/com/molina/suite/core/storage/MolinaStorage.kt",
        [
            (
                "    /** Sub-folder log: /sdcard/Molina/Logs/ (ekspor logcat dan salinan laporan crash). */\n"
                "    const val LOGS_DIR_NAME = \"Logs\"\n"
                "\n",
                "",
                1,
            ),
            ("    fun logsDir(): File = File(sharedRoot(), LOGS_DIR_NAME)\n\n", "", 1),
            (
                "listOf(sharedRoot(), downloadsDir(), logsDir())",
                "listOf(sharedRoot(), downloadsDir())",
                1,
            ),
        ],
        "!LOGS_DIR_NAME",
    ),
]


def applied(marker, text):
    if marker.startswith("!"):
        return marker[1:] not in text
    return marker in text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    if not os.path.exists(REQUIRED):
        die("jalankan tools/patch_shell_log.py dulu (tidak ada " + REQUIRED + ")")

    pending = []
    for path, edits, marker in EDITS:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        if applied(marker, text):
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
