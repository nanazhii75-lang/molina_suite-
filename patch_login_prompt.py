#!/usr/bin/env python3
# Patch: pilihan login user@/root@ + prompt Debian seragam (hijau / hijau muda) untuk Molina Terminal.
# Idempotent: aman dijalankan berulang. Jalankan dari akar repo (/storage/emulated/0/molina-suite).
import os
import sys

ENGINE_APP = "engines/termux/app/src/main/java/com/molina/suite/terminal/app"
JAVA_FILE = os.path.join(ENGINE_APP, "MolinaShellProfile.java")
INSTALLER = os.path.join(ENGINE_APP, "TermuxInstaller.java")
CHANGES = "engines/termux/MOLINA-CHANGES.md"
CHANGES_MARKER = "## Pilihan login dan prompt Debian"

# --- Skrip shell host: etc/profile.d/03-molina-login.sh ---------------------------------------
LOGIN_SH = r"""# Molina Terminal: pilihan login Debian (user atau root). Ditulis ulang oleh aplikasi setiap start; jangan diedit manual.
[ -n "${BASH_VERSION-}" ] || return 0
case $- in *i*) ;; *) return 0 ;; esac
[ -n "${MOLINA_NO_LOGIN_PROMPT-}" ] && return 0

__molina_rootfs="%PREFIX%/var/lib/proot-distro/installed-rootfs/debian"
__molina_mode_file="$HOME/.molina/login-mode"
__molina_mode=""

__molina_debian_ready() {
	command -v proot-distro >/dev/null 2>&1 && [ -d "$__molina_rootfs/etc" ]
}

__molina_load_mode() {
	local saved=""
	[ -r "$__molina_mode_file" ] && IFS= read -r saved < "$__molina_mode_file"
	case $saved in
		user|root) __molina_mode=$saved; return 0 ;;
	esac
	return 1
}

__molina_save_mode() {
	mkdir -p "$HOME/.molina" 2>/dev/null && printf '%s\n' "$1" > "$__molina_mode_file" 2>/dev/null
}

__molina_ask_mode() {
	local answer=""
	printf '\nLogin Debian sebagai:\n  1) user@localhost\n  2) root@localhost\n'
	while :; do
		printf 'Pilih [1/2]: '
		IFS= read -r answer || return 1
		case $answer in
			1|user) __molina_mode=user; return 0 ;;
			2|root) __molina_mode=root; return 0 ;;
		esac
		printf 'Ketik 1 atau 2.\n'
	done
}

__molina_ensure_user() {
	proot-distro login debian -- bash -c 'id -u user >/dev/null 2>&1 || useradd -m -s /bin/bash user'
}

__molina_login() {
	case $1 in
		root)
			proot-distro login debian
			;;
		user)
			if ! __molina_ensure_user; then
				printf 'Gagal menyiapkan user di Debian. Masuk sebagai root dengan: molina-login root\n' >&2
				return 1
			fi
			proot-distro login debian --user user
			;;
		*) return 2 ;;
	esac
	printf 'Kembali ke shell Termux. Ketik molina-login untuk masuk Debian lagi.\n'
}

molina-login() {
	if ! __molina_debian_ready; then
		printf 'Debian belum terpasang. Pasang lewat Setting.\n' >&2
		return 1
	fi
	case ${1-} in
		"") __molina_ask_mode || return 1 ;;
		user|root) __molina_mode=$1 ;;
		*) printf 'Pemakaian: molina-login [user|root]\n' >&2; return 2 ;;
	esac
	__molina_save_mode "$__molina_mode"
	__molina_login "$__molina_mode"
}

if __molina_debian_ready; then
	if __molina_load_mode; then
		__molina_login "$__molina_mode"
	elif __molina_ask_mode; then
		__molina_save_mode "$__molina_mode"
		__molina_login "$__molina_mode"
	fi
fi
"""

# --- Blok prompt Debian: ditambahkan ke ~/.bashrc di rootfs ----------------------------------
PROMPT_BEGIN = "# >>> molina-prompt (dikelola aplikasi; jangan diedit) >>>"
PROMPT_END = "# <<< molina-prompt <<<"
PROMPT_SH = PROMPT_BEGIN + "\n" + r"""if [ -n "${PS1-}" ]; then
	PS1='\[\e[32m\]\u@localhost\[\e[0m\]:\[\e[92m\]\w\[\e[0m\] $ '
	__molina_banner() {
		[ -n "${MOLINA_NO_BANNER_REDRAW-}" ] && return 0
		[ -f "$HOME/.hushlogin" ] && return 0
		printf '\033[1;38;5;120mWelcome Molina Terminal\033[0m\n'
	}
	__molina_clear_screen() {
		command clear
		__molina_banner
	}
	clear() {
		if [ $# -eq 0 ]; then
			__molina_clear_screen
		else
			command clear "$@"
		fi
	}
	bind -x '"\C-l": __molina_clear_screen' 2>/dev/null
fi
""" + PROMPT_END + "\n"


def java_literal(text, indent="            "):
    """Ubah teks menjadi rangkaian literal String Java (tanpa text block, kompatibel Java 8)."""
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()
        trailing = True
    else:
        trailing = False
    parts = []
    for i, line in enumerate(lines):
        esc = line.replace("\\", "\\\\").replace('"', '\\"').replace("\t", "\\t")
        nl = "\\n" if (i < len(lines) - 1 or trailing) else ""
        parts.append('"' + esc + nl + '"')
    return ("\n" + indent + "+ ").join(parts)


JAVA_TEMPLATE = """package com.molina.suite.terminal.app;

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
    private static final String PROMPT_BEGIN = %BEGIN%;
    private static final String PROMPT_END = %END%;

    private static final String LOGIN_SCRIPT =
              %LOGIN%;

    private static final String PROMPT_BLOCK =
              %PROMPT%;

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
            String prefix = (current.isEmpty() || current.endsWith("\\n")) ? current : current + "\\n";
            return prefix + block;
        }
        int end = current.indexOf(PROMPT_END, start);
        if (end < 0) return current.substring(0, start) + block;
        int lineEnd = current.indexOf('\\n', end);
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
"""

CHANGES_TEXT = """
## Pilihan login dan prompt Debian
- `app/.../MolinaShellProfile.java` (baru): dipanggil dari `TermuxInstaller` bersama `applyMolinaBanner()`.
- `etc/profile.d/03-molina-login.sh`: pada sesi bash interaktif pertama menanyakan login Debian sebagai `user@localhost` atau `root@localhost`, menyimpan pilihan di `~/.molina/login-mode`, lalu `proot-distro login debian` (user: `--user user`, dibuat otomatis dengan `useradd -m`). Rootfs belum terpasang: shell host tetap dipakai tanpa pertanyaan. Ganti pilihan dengan `molina-login [user|root]`; lewati dengan `MOLINA_NO_LOGIN_PROMPT=1`.
- Prompt Debian: blok bertanda `molina-prompt` ditambahkan ke `~/.bashrc` root, user, dan `/etc/skel`. Format `root@localhost:path $` dan `user@localhost:path $`: nama hijau (SGR 32), path hijau muda (SGR 92), tanda akhir `$` untuk root maupun user. `clear` dan Ctrl+L di Debian mencetak ulang banner yang sama dengan host.
"""


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def build_java():
    return (
        JAVA_TEMPLATE
        .replace("%BEGIN%", '"' + PROMPT_BEGIN.replace('"', '\\"') + '"')
        .replace("%END%", '"' + PROMPT_END.replace('"', '\\"') + '"')
        .replace("%LOGIN%", java_literal(LOGIN_SH))
        .replace("%PROMPT%", java_literal(PROMPT_SH))
    )


def patch_java():
    text = build_java()
    if os.path.isfile(JAVA_FILE) and read(JAVA_FILE) == text:
        print("= MolinaShellProfile.java sudah terbaru")
        return
    write(JAVA_FILE, text)
    print("+ MolinaShellProfile.java ditulis")


def patch_installer():
    src = read(INSTALLER)
    call = "MolinaShellProfile.apply();"
    if call in src:
        print("= TermuxInstaller sudah memanggil MolinaShellProfile")
        return
    lines = src.split("\n")
    out = []
    count = 0
    for line in lines:
        out.append(line)
        if line.strip() == "applyMolinaBanner();":
            indent = line[: len(line) - len(line.lstrip())]
            out.append(indent + call)
            count += 1
    if count != 2:
        sys.exit("GAGAL: ditemukan %d panggilan applyMolinaBanner(), diharapkan 2" % count)
    write(INSTALLER, "\n".join(out))
    print("+ TermuxInstaller: %d panggilan ditambahkan" % count)


def patch_changes():
    src = read(CHANGES)
    if CHANGES_MARKER in src:
        print("= MOLINA-CHANGES.md sudah memuat bagian ini")
        return
    if not src.endswith("\n"):
        src += "\n"
    write(CHANGES, src + CHANGES_TEXT)
    print("+ MOLINA-CHANGES.md diperbarui")


def main():
    for path in (INSTALLER, CHANGES):
        if not os.path.isfile(path):
            sys.exit("GAGAL: %s tidak ditemukan. Jalankan dari akar repo molina-suite." % path)
    patch_java()
    patch_installer()
    patch_changes()


main()
