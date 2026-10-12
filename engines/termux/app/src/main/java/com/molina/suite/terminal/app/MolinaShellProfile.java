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
 * <p>Dua berkas di prefix host, ditulis ulang hanya bila isinya berbeda:
 * <ul>
 *   <li>{@code etc/profile.d/03-molina-login.sh}: menanyakan user atau root pada sesi interaktif
 *       pertama, mengingat pilihan di {@code ~/.molina/login-mode}, lalu masuk Debian lewat
 *       proot-distro. Sebelum setiap login skrip ini menyisipkan blok prompt ke {@code ~/.bashrc}
 *       rootfs (root, user, /etc/skel) dan menulis penanda {@code ~/.molina/in-debian} (PID shell
 *       host) selama sesi Debian berjalan. Rootfs belum terpasang berarti shell host dipakai.</li>
 *   <li>{@code etc/molina/prompt-block.bashrc}: isi blok prompt: nama hijau, path hijau muda,
 *       tanda akhir {@code $} untuk root dan user.</li>
 * </ul>
 */
final class MolinaShellProfile {

    private static final String LOG_TAG = "MolinaShellProfile";

    private static final String LOGIN_SCRIPT =
              "# Molina Terminal: pilihan login Debian (user atau root). Ditulis ulang oleh aplikasi setiap start; jangan diedit manual.\n"
            + "[ -n \"${BASH_VERSION-}\" ] || return 0\n"
            + "case $- in *i*) ;; *) return 0 ;; esac\n"
            + "[ -n \"${MOLINA_NO_LOGIN_PROMPT-}\" ] && return 0\n"
            + "\n"
            + "__molina_rootfs=\"%PREFIX%/var/lib/proot-distro/installed-rootfs/debian\"\n"
            + "__molina_prompt_block=\"%PREFIX%/etc/molina/prompt-block.bashrc\"\n"
            + "__molina_state_dir=\"$HOME/.molina\"\n"
            + "__molina_mode_file=\"$__molina_state_dir/login-mode\"\n"
            + "__molina_inside_file=\"$__molina_state_dir/in-debian\"\n"
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
            + "\tmkdir -p \"$__molina_state_dir\" 2>/dev/null && printf '%s\\n' \"$1\" > \"$__molina_mode_file\" 2>/dev/null\n"
            + "}\n"
            + "\n"
            + "__molina_confirm_root() {\n"
            + "\tlocal answer=\"\"\n"
            + "\tprintf 'Masuk sebagai root? Root di Debian berjalan di dalam sandbox aplikasi, tetapi bisa mengubah atau menghapus apa saja di Debian.\\nSetuju? [y/N]: '\n"
            + "\tIFS= read -r answer || return 1\n"
            + "\tcase $answer in\n"
            + "\t\ty|Y|ya|Ya|YA) return 0 ;;\n"
            + "\tesac\n"
            + "\treturn 1\n"
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
            + "\t\t\t2|root)\n"
            + "\t\t\t\tif __molina_confirm_root; then\n"
            + "\t\t\t\t\t__molina_mode=root\n"
            + "\t\t\t\t\treturn 0\n"
            + "\t\t\t\tfi\n"
            + "\t\t\t\tprintf 'Dibatalkan.\\n'\n"
            + "\t\t\t\t;;\n"
            + "\t\t\t*) printf 'Ketik 1 atau 2.\\n' ;;\n"
            + "\t\tesac\n"
            + "\tdone\n"
            + "}\n"
            + "\n"
            + "# Menyisipkan atau memperbarui blok prompt di $1/.bashrc (direktori harus sudah ada).\n"
            + "__molina_upsert_block() {\n"
            + "\tlocal dir=$1 file current block begin_line end_line head tail updated\n"
            + "\t[ -d \"$dir\" ] && [ -r \"$__molina_prompt_block\" ] || return 0\n"
            + "\tfile=\"$dir/.bashrc\"\n"
            + "\tblock=$(<\"$__molina_prompt_block\") || return 1\n"
            + "\tbegin_line=${block%%$'\\n'*}\n"
            + "\tend_line=${block##*$'\\n'}\n"
            + "\tcurrent=\"\"\n"
            + "\t[ -f \"$file\" ] && current=$(<\"$file\")\n"
            + "\tcase $current in\n"
            + "\t\t*\"$begin_line\"*)\n"
            + "\t\t\thead=${current%%\"$begin_line\"*}\n"
            + "\t\t\ttail=${current#*\"$begin_line\"}\n"
            + "\t\t\tcase $tail in\n"
            + "\t\t\t\t*\"$end_line\"*)\n"
            + "\t\t\t\t\ttail=${tail#*\"$end_line\"}\n"
            + "\t\t\t\t\ttail=${tail#*$'\\n'}\n"
            + "\t\t\t\t\tupdated=\"$head$block\"\n"
            + "\t\t\t\t\t[ -n \"$tail\" ] && updated=\"$updated\"$'\\n'\"$tail\"\n"
            + "\t\t\t\t\t;;\n"
            + "\t\t\t\t*) updated=\"$head$block\" ;;\n"
            + "\t\t\tesac\n"
            + "\t\t\t;;\n"
            + "\t\t*)\n"
            + "\t\t\tupdated=$current\n"
            + "\t\t\t[ -n \"$updated\" ] && updated=\"$updated\"$'\\n'\n"
            + "\t\t\tupdated=\"$updated$block\"\n"
            + "\t\t\t;;\n"
            + "\tesac\n"
            + "\t[ \"$updated\" = \"$current\" ] && return 0\n"
            + "\tprintf '%s\\n' \"$updated\" > \"$file\" 2>/dev/null\n"
            + "}\n"
            + "\n"
            + "__molina_apply_prompt() {\n"
            + "\t__molina_upsert_block \"$__molina_rootfs/root\"\n"
            + "\t__molina_upsert_block \"$__molina_rootfs/etc/skel\"\n"
            + "\t__molina_upsert_block \"$__molina_rootfs/home/user\"\n"
            + "}\n"
            + "\n"
            + "__molina_ensure_user() {\n"
            + "\tproot-distro login debian -- bash -c 'id -u user >/dev/null 2>&1 || useradd -m -s /bin/bash user'\n"
            + "}\n"
            + "\n"
            + "# Penanda \"sedang di Debian\": PID shell host. Dibaca menu Debian di Setting.\n"
            + "__molina_enter_marker() {\n"
            + "\tmkdir -p \"$__molina_state_dir\" 2>/dev/null && printf '%s\\n' \"$$\" > \"$__molina_inside_file\" 2>/dev/null\n"
            + "}\n"
            + "\n"
            + "__molina_leave_marker() {\n"
            + "\trm -f \"$__molina_inside_file\" 2>/dev/null\n"
            + "}\n"
            + "\n"
            + "__molina_login() {\n"
            + "\t__molina_apply_prompt\n"
            + "\tcase $1 in\n"
            + "\t\troot)\n"
            + "\t\t\t__molina_enter_marker\n"
            + "\t\t\tproot-distro login debian\n"
            + "\t\t\t__molina_leave_marker\n"
            + "\t\t\t;;\n"
            + "\t\tuser)\n"
            + "\t\t\tif ! __molina_ensure_user; then\n"
            + "\t\t\t\tprintf 'Gagal menyiapkan user di Debian. Masuk sebagai root dengan: molina-login root\\n' >&2\n"
            + "\t\t\t\treturn 1\n"
            + "\t\t\tfi\n"
            + "\t\t\t__molina_apply_prompt\n"
            + "\t\t\t__molina_enter_marker\n"
            + "\t\t\tproot-distro login debian --user user\n"
            + "\t\t\t__molina_leave_marker\n"
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
            + "\t\tuser) __molina_mode=user ;;\n"
            + "\t\troot)\n"
            + "\t\t\tif [ \"${2-}\" != \"--yes\" ] && ! __molina_confirm_root; then\n"
            + "\t\t\t\tprintf 'Dibatalkan.\\n'\n"
            + "\t\t\t\treturn 1\n"
            + "\t\t\tfi\n"
            + "\t\t\t__molina_mode=root\n"
            + "\t\t\t;;\n"
            + "\t\t*) printf 'Pemakaian: molina-login [user|root]\\n' >&2; return 2 ;;\n"
            + "\tesac\n"
            + "\t__molina_save_mode \"$__molina_mode\"\n"
            + "\t__molina_login \"$__molina_mode\"\n"
            + "}\n"
            + "\n"
            + "__molina_leave_marker\n"
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
        writeFile("etc/profile.d/03-molina-login.sh",
            LOGIN_SCRIPT.replace("%PREFIX%", TERMUX_PREFIX_DIR_PATH));
        writeFile("etc/molina/prompt-block.bashrc", PROMPT_BLOCK);
    }

    private static void writeFile(String relativePath, String content) {
        try {
            File file = new File(TERMUX_PREFIX_DIR_PATH, relativePath);
            File dir = file.getParentFile();
            if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return;
            byte[] expected = content.getBytes(StandardCharsets.UTF_8);
            if (file.isFile()) {
                if (Arrays.equals(Files.readAllBytes(file.toPath()), expected)) return;
                file.setWritable(true, true);
            }
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(expected);
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Gagal menulis " + relativePath, e);
        }
    }
}
