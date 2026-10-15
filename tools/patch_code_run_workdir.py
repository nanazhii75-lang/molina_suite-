#!/usr/bin/env python3
"""Aksi Jalankan (tab Code) memakai tab Terminal per folder berkas.

- Folder yang sama dan shell idle: tab itu dipakai ulang dan di-cd ke folder berkas.
- Folder berbeda, atau tab untuk folder itu sedang menjalankan proses: tab baru dibuat
  di folder berkas dan tab lama tidak disentuh.
Idempotent: aman dijalankan berulang. Jalankan dari akar repo.
"""
import sys
from pathlib import Path

ROOT = Path.cwd()
CHANGED = []


def edit(rel, marker, fn):
    path = ROOT / rel
    if not path.is_file():
        sys.exit("Berkas tidak ada: " + rel)
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print("lewati (sudah ada): " + rel)
        return
    new = fn(text)
    if new == text:
        sys.exit("Tidak ada perubahan untuk: " + rel)
    path.write_text(new, encoding="utf-8")
    CHANGED.append(rel)
    print("diubah: " + rel)


def replace_once(text, old, new):
    if text.count(old) != 1:
        sys.exit("Jangkar tidak unik/tidak ditemukan: " + old[:60])
    return text.replace(old, new)


# 1) Kontrak core-common
SENDER = "core/core-common/src/main/kotlin/com/molina/suite/core/common/TerminalInputSender.kt"


def patch_contract(text):
    old = "    fun sendLine(line: String): TerminalInputResult\n}"
    new = (
        "    fun sendLine(line: String): TerminalInputResult\n\n"
        "    /**\n"
        "     * Mengetik [line] ke tab terminal yang terikat ke folder [workdir]. Bila sudah ada tab\n"
        "     * untuk folder itu dan shell-nya idle, tab itu dipakai ulang; bila tidak ada, atau sedang\n"
        "     * menjalankan proses, tab baru berlabel [label] dibuat di [workdir] tanpa menyentuh tab\n"
        "     * lain. Implementasi bawaan jatuh ke [sendLine] (sesi aktif).\n"
        "     */\n"
        "    fun sendLineInWorkdir(workdir: String, label: String, line: String): TerminalInputResult =\n"
        "        sendLine(line)\n}"
    )
    return replace_once(text, old, new)


edit(SENDER, "sendLineInWorkdir", patch_contract)

# 2) Adapter feature-terminal
ADAPTER = "features/feature-terminal/src/main/kotlin/com/molina/suite/feature/terminal/TermuxTerminalInputSender.kt"


def patch_adapter(text):
    old = "        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)\n    }\n}"
    new = (
        "        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)\n    }\n\n"
        "    override fun sendLineInWorkdir(workdir: String, label: String, line: String): TerminalInputResult {\n"
        "        if (line.isEmpty()) return TerminalInputResult.Rejected(\"Baris kosong\")\n"
        "        if (line.any { it == '\\n' || it == '\\r' }) {\n"
        "            return TerminalInputResult.Rejected(\"Teks harus satu baris\")\n"
        "        }\n"
        "        if (workdir.isEmpty() || workdir.any { it == '\\n' || it == '\\r' }) {\n"
        "            return TerminalInputResult.Rejected(\"Folder kerja tidak valid\")\n"
        "        }\n"
        "        val tab = TerminalTabRegistry.current()\n"
        "            ?: return TerminalInputResult.Rejected(\"Tab Terminal belum dibuka\")\n"
        "        val reason = tab.writeLineInWorkdir(workdir, label, line)\n"
        "        return if (reason == null) TerminalInputResult.Sent else TerminalInputResult.Rejected(reason)\n"
        "    }\n}"
    )
    return replace_once(text, old, new)


edit(ADAPTER, "sendLineInWorkdir", patch_adapter)

# 3) Engine: TermuxFragment
FRAG = "engines/termux/app/src/main/java/com/molina/suite/terminal/app/TermuxFragment.java"

FRAG_METHODS = '''
    /** Batas tab; sama dengan batas di TermuxTerminalSessionClient. */
    private static final int MAX_WORKDIR_SESSIONS = 8;

    /** Handle sesi ke folder kerja yang mengikatnya. Hidup selama proses, seperti sesi di service. */
    private static final java.util.Map<String, String> WORKDIR_BY_HANDLE = new java.util.HashMap<>();

    /**
     * Mengetik [line] ke tab untuk folder [workdir]. Tab itu dipakai ulang bila shell-nya idle;
     * jika belum ada atau sedang menjalankan proses, tab baru dibuat di [workdir] dan tab lain
     * tidak disentuh. Hanya dipanggil dari main thread. Null bila berhasil, atau alasan gagal;
     * pemanggil boleh mengulang bila sesi baru belum siap.
     */
    @Nullable
    public String writeLineInWorkdir(@NonNull String workdir, @NonNull String label, @NonNull String line) {
        if (mTerminalView == null || mTermuxService == null || mTermuxTerminalSessionClient == null) {
            return "Terminal belum siap";
        }

        java.util.List<TermuxSession> sessions = new java.util.ArrayList<>(mTermuxService.getTermuxSessions());
        java.util.Set<String> liveHandles = new java.util.HashSet<>();
        TerminalSession reusable = null;
        boolean pendingStart = false;
        synchronized (WORKDIR_BY_HANDLE) {
            for (TermuxSession termuxSession : sessions) {
                liveHandles.add(termuxSession.getTerminalSession().mHandle);
            }
            WORKDIR_BY_HANDLE.keySet().retainAll(liveHandles);
            for (TermuxSession termuxSession : sessions) {
                TerminalSession candidate = termuxSession.getTerminalSession();
                if (!workdir.equals(WORKDIR_BY_HANDLE.get(candidate.mHandle))) continue;
                if (!candidate.isRunning()) continue;
                if (candidate.getPid() <= 0) {
                    pendingStart = true; // tab sudah dibuat, shell belum mulai
                } else if (reusable == null && isShellInForeground(candidate.getPid())) {
                    reusable = candidate;
                }
            }
        }
        if (reusable == null && pendingStart) return "Tab terminal baru belum siap";

        TerminalSession target = reusable;
        if (target == null) {
            if (sessions.size() >= MAX_WORKDIR_SESSIONS) return "Batas 8 tab terminal tercapai";
            TermuxSession created = mTermuxService.createTermuxSession(null, null, null, workdir, false, label);
            if (created == null) return "Gagal membuat tab terminal baru";
            target = created.getTerminalSession();
            synchronized (WORKDIR_BY_HANDLE) {
                WORKDIR_BY_HANDLE.put(target.mHandle, workdir);
            }
        }

        mTermuxTerminalSessionClient.setCurrentSession(target);
        if (target.getPid() <= 0) return "Tab terminal baru belum siap";
        byte[] data = (line + "\\r").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        target.write(data, 0, data.length);
        return null;
    }

    /**
     * True bila shell [pid] sedang menjadi proses foreground di terminalnya (tidak ada perintah
     * yang berjalan): grup proses shell sama dengan grup foreground tty. Gagal baca dianggap
     * sibuk supaya perintah tidak pernah diketik ke program yang sedang berjalan.
     */
    private static boolean isShellInForeground(int pid) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream("/proc/" + pid + "/stat"),
                    java.nio.charset.StandardCharsets.UTF_8))) {
            String stat = reader.readLine();
            if (stat == null) return false;
            int close = stat.lastIndexOf(')');
            if (close < 0 || close + 2 > stat.length()) return false;
            // Setelah ")": state ppid pgrp session tty_nr tpgid
            String[] fields = stat.substring(close + 2).split(" ");
            if (fields.length < 6) return false;
            return fields[2].equals(fields[5]);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
'''


def patch_fragment(text):
    text = replace_once(
        text,
        "import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;\n",
        "import com.molina.suite.terminal.shared.settings.preferences.TermuxAppSharedPreferences;\n"
        "import com.molina.suite.terminal.shared.shell.TermuxSession;\n",
    )
    anchor = (
        "        session.write(data, 0, data.length);\n"
        "        return null;\n"
        "    }\n"
    )
    return replace_once(text, anchor, anchor + FRAG_METHODS)


edit(FRAG, "writeLineInWorkdir", patch_fragment)

# 4) Runner tab Code
RUNNER = "features/feature-code/src/main/kotlin/com/molina/suite/feature/code/CodeScriptRunner.kt"


def patch_runner(text):
    text = replace_once(
        text,
        "        val host = switcher ?: return CodeRunOutcome.NoSwitcher\n\n"
        "        var result = sender.sendLine(line)\n",
        "        val host = switcher ?: return CodeRunOutcome.NoSwitcher\n\n"
        "        // Tab terminal mengikuti folder berkas: folder sama dipakai ulang bila idle, folder\n"
        "        // lain atau tab yang sibuk membuka tab baru tanpa menimpa tab sebelumnya.\n"
        "        val workdir = ScriptRunLine.normalizePath(file.absolutePath).substringBeforeLast('/', \"\").ifEmpty { \"/\" }\n"
        "        val label = workdir.substringAfterLast('/').ifEmpty { \"/\" }\n"
        "        val typed = \"cd \" + ScriptRunLine.quote(workdir) + \" 2>/dev/null; \" + line\n"
        "        var result = sender.sendLineInWorkdir(workdir, label, typed)\n",
    )
    text = replace_once(
        text,
        "            result = sender.sendLine(line)\n",
        "            result = sender.sendLineInWorkdir(workdir, label, typed)\n",
    )
    return text


edit(RUNNER, "sendLineInWorkdir", patch_runner)

print("\nSelesai. Berkas berubah: %d" % len(CHANGED))
for rel in CHANGED:
    print(" - " + rel)
