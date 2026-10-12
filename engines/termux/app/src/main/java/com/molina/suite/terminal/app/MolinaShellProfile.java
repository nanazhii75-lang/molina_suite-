package com.molina.suite.terminal.app;

import com.molina.suite.terminal.shared.logger.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import static com.molina.suite.terminal.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;

/**
 * Pilihan login Debian (user atau root) dan prompt Debian seragam untuk Molina Terminal.
 *
 * <p>Dua keluaran, keduanya ditulis ulang hanya bila isinya berbeda:
 * <ul>
 *   <li>{@code etc/profile.d/03-molina-login.sh} di prefix host: menanyakan user atau root saat
 *       sesi interaktif pertama, mengingat pilihan di {@code ~/.molina/login-mode}, lalu masuk
 *       Debian lewat proot-distro. Rootfs belum terpasang berarti shell host tetap dipakai.</li>
 *   <li>Blok prompt bertanda di {@code ~/.bashrc} rootfs (root, user, dan /etc/skel) agar root dan
 *       user tampil sama: nama hijau, path hijau muda, tanda akhir {@code $}.</li>
 * </ul>
 */
final class MolinaShellProfile {

    private static final String LOG_TAG = "MolinaShellProfile";

    private static final String ROOTFS_RELATIVE = "var/lib/proot-distro/installed-rootfs/debian";
    private static final String PROMPT_BEGIN = "# >>> molina-prompt (dikelola aplikasi; jangan diedit) >>>";
    private static final String PROMPT_END = "# <<< molina-prompt <<<";

    private static final String LOGIN_SCRIPT =
              "# Molina Terminal: pilihan login Debian (user atau root). Ditulis ulang oleh aplikasi setiap start; jangan diedit manual.\n"
            + "[ -n \"${BASH_VERSION-}\" ] || return 0\n"
            + "case $- in *i*) ;; *) return 0 ;; esac\n"
            + "[ -n \"${MOLINA_NO_LOGIN_PROMPT-}\" ] && return 0\n"
            + "\n"
            + "__molina_rootfs=\"%PREFIX%/var/lib/proot-distro/installed-rootfs/debian\"\n"
            + "__molina_mode_file=\"$HOME/.molina/login-mode\"\n"
            + "__molina_mode=\"\"\n"
            + "\n"
            + "__molina_debian_ready() {\n"
            + "\tcommand -v proot-distro >/dev/null 2>&1 && [ -d \"$__molina_rootfs/etc\" ]\n"
            + "}\n"
            + "\n"
            + "__molina_load_mode() {\n"
            + "\tlocal saved=\"\"\n"
            + "\t[ -r \"$__molina_mode_file\" ] && IFS= read -r saved < \"$__molina_mode_file\"\n"
            + "\tcase $saved in\n"
            + "\t\tuser|root) __molina_mode=$saved; return 0 ;;\n"
            + "\tesac\n"
            + "\treturn 1\n"
            + "}\n"
            + "\n"
            + "__molina_save_mode() {\n"
            + "\tmkdir -p \"$HOME/.molina\" 2>/dev/null && printf '%s\\n' \"$1\" > \"$__molina_mode_file\" 2>/dev/null\n"
            + "}\n"
            + "\n"
            + "__molina_ask_mode() {\n"
            + "\tlocal answer=\"\"\n"
            + "\tprintf '\\nLogin Debian sebagai:\\n  1) user@localhost\\n  2) root@localhost\\n'\n"
            + "\twhile :; do\n"
            + "\t\tprintf 'Pilih [1/2]: '\n"
            + "\t\tIFS= read -r answer || return 1\n"
            + "\t\tcase $answer in\n"
            + "\t\t\t1|user) __molina_mode=user; return 0 ;;\n"
            + "\t\t\t2|root) __molina_mode=root; return 0 ;;\n"
            + "\t\tesac\n"
            + "\t\tprintf 'Ketik 1 atau 2.\\n'\n"
            + "\tdone\n"
            + "}\n"
            + "\n"
            + "__molina_ensure_user() {\n"
            + "\tproot-distro login debian -- bash -c 'id -u user >/dev/null 2>&1 || useradd -m -s /bin/bash user'\n"
            + "}\n"
            + "\n"
            + "__molina_login() {\n"
            + "\tcase $1 in\n"
            + "\t\troot)\n"
            + "\t\t\tproot-distro login debian\n"
            + "\t\t\t;;\n"
            + "\t\tuser)\n"
            + "\t\t\tif ! __molina_ensure_user; then\n"
            + "\t\t\t\tprintf 'Gagal menyiapkan user di Debian. Masuk sebagai root dengan: molina-login root\\n' >&2\n"
            + "\t\t\t\treturn 1\n"
            + "\t\t\tfi\n"
            + "\t\t\tproot-distro login debian --user user\n"
            + "\t\t\t;;\n"
            + "\t\t*) return 2 ;;\n"
            + "\tesac\n"
            + "\tprintf 'Kembali ke shell Termux. Ketik molina-login untuk masuk Debian lagi.\\n'\n"
            + "}\n"
            + "\n"
            + "molina-login() {\n"
            + "\tif ! __molina_debian_ready; then\n"
            + "\t\tprintf 'Debian belum terpasang. Pasang lewat Setting.\\n' >&2\n"
            + "\t\treturn 1\n"
            + "\tfi\n"
            + "\tcase ${1-} in\n"
            + "\t\t\"\") __molina_ask_mode || return 1 ;;\n"
            + "\t\tuser|root) __molina_mode=$1 ;;\n"
            + "\t\t*) printf 'Pemakaian: molina-login [user|root]\\n' >&2; return 2 ;;\n"
            + "\tesac\n"
            + "\t__molina_save_mode \"$__molina_mode\"\n"
            + "\t__molina_login \"$__molina_mode\"\n"
            + "}\n"
            + "\n"
            + "if __molina_debian_ready; then\n"
            + "\tif __molina_load_mode; then\n"
            + "\t\t__molina_login \"$__molina_mode\"\n"
            + "\telif __molina_ask_mode; then\n"
            + "\t\t__molina_save_mode \"$__molina_mode\"\n"
            + "\t\t__molina_login \"$__molina_mode\"\n"
            + "\tfi\n"
            + "fi\n";

    private static final String PROMPT_BLOCK =
              "# >>> molina-prompt (dikelola aplikasi; jangan diedit) >>>\n"
            + "if [ -n \"${PS1-}\" ]; then\n"
            + "\tPS1='\\[\\e[32m\\]\\u@localhost\\[\\e[0m\\]:\\[\\e[92m\\]\\w\\[\\e[0m\\] $ '\n"
            + "\t__molina_banner() {\n"
            + "\t\t[ -n \"${MOLINA_NO_BANNER_REDRAW-}\" ] && return 0\n"
            + "\t\t[ -f \"$HOME/.hushlogin\" ] && return 0\n"
            + "\t\tprintf '\\033[1;38;5;120mWelcome Molina Terminal\\033[0m\\n'\n"
            + "\t}\n"
            + "\t__molina_clear_screen() {\n"
            + "\t\tcommand clear\n"
            + "\t\t__molina_banner\n"
            + "\t}\n"
            + "\tclear() {\n"
            + "\t\tif [ $# -eq 0 ]; then\n"
            + "\t\t\t__molina_clear_screen\n"
            + "\t\telse\n"
            + "\t\t\tcommand clear \"$@\"\n"
            + "\t\tfi\n"
            + "\t}\n"
            + "\tbind -x '\"\\C-l\": __molina_clear_screen' 2>/dev/null\n"
            + "fi\n"
            + "# <<< molina-prompt <<<\n";

    private MolinaShellProfile() {}

    /** Dipanggil bersama TermuxInstaller.applyMolinaBanner pada setiap aplikasi dimulai. */
    static void apply() {
        applyLoginScript();
        applyDebianPrompt();
    }

    private static void applyLoginScript() {
        try {
            File dir = new File(TERMUX_PREFIX_DIR_PATH, "etc/profile.d");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            byte[] expected = LOGIN_SCRIPT
                .replace("%PREFIX%", TERMUX_PREFIX_DIR_PATH)
                .getBytes(StandardCharsets.UTF_8);
            writeIfDifferent(new File(dir, "03-molina-login.sh"), expected);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Gagal menulis skrip login Molina", e);
        }
    }

    private static void applyDebianPrompt() {
        File rootfs = new File(TERMUX_PREFIX_DIR_PATH, ROOTFS_RELATIVE);
        if (!new File(rootfs, "etc").isDirectory()) return;
        upsertBlock(new File(rootfs, "root"));
        upsertBlock(new File(rootfs, "etc/skel"));
        upsertBlock(new File(rootfs, "home/user"));
    }

    /** Menyisipkan atau memperbarui blok prompt di {@code <dir>/.bashrc}; dir harus sudah ada. */
    private static void upsertBlock(File dir) {
        if (!dir.isDirectory()) return;
        File bashrc = new File(dir, ".bashrc");
        try {
            String current = bashrc.isFile()
                ? new String(Files.readAllBytes(bashrc.toPath()), StandardCharsets.UTF_8)
                : "";
            String updated = replaceBlock(current, PROMPT_BLOCK);
            if (updated.equals(current)) return;
            writeIfDifferent(bashrc, updated.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Gagal menulis prompt Molina di " + bashrc, e);
        }
    }

    /** Mengganti blok bertanda bila ada (juga yang rusak tanpa penutup), selain itu menambahkan di akhir. */
    static String replaceBlock(String current, String block) {
        int start = current.indexOf(PROMPT_BEGIN);
        if (start < 0) {
            String prefix = (current.isEmpty() || current.endsWith("\n")) ? current : current + "\n";
            return prefix + block;
        }
        int end = current.indexOf(PROMPT_END, start);
        if (end < 0) return current.substring(0, start) + block;
        int lineEnd = current.indexOf('\n', end);
        int after = lineEnd < 0 ? current.length() : lineEnd + 1;
        return current.substring(0, start) + block + current.substring(after);
    }

    private static void writeIfDifferent(File file, byte[] expected) throws Exception {
        if (file.isFile()) {
            if (Arrays.equals(Files.readAllBytes(file.toPath()), expected)) return;
            file.setWritable(true, true);
        }
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(expected);
        }
    }
}
