package com.molina.suite.feature.settings.debian

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.ShellLine
import com.molina.suite.feature.settings.packages.PackageCatalog

/** Kapan sebuah aksi Debian boleh dipakai. */
internal enum class Availability {
    ALWAYS,
    WHEN_MISSING,
    WHEN_INSTALLED,

    /** Terpasang dan Terminal sedang di shell biasa (belum di dalam Debian). */
    WHEN_INSTALLED_OUTSIDE,

    /** Terminal sedang di dalam Debian (lewat molina-login). */
    WHEN_IN_DEBIAN
}

/** Satu kartu aksi pada layar Debian. [command] diketik ke Terminal bila [runnable]. */
internal data class DebianAction(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val command: String,
    val runnable: Boolean,
    val availability: Availability,
    /** Teks ringkas di kartu bila perintah lengkap terlalu panjang; null berarti memakai [command]. */
    val preview: String? = null,
    /** Pesan konfirmasi sebelum dijalankan; null berarti langsung jalan. */
    @StringRes val confirmMessage: Int? = null
)

internal object DebianActions {
    /** Rootfs Debian lalu yt-dlp (venv + curl_cffi) dalam satu baris; yt-dlp memakai definisi menu Paket. */
    private val INSTALL_COMMAND: String =
        "proot-distro install debian && " +
            ShellLine.wrapForDebian(PackageCatalog.all.first { it.id == "ytdlp" }.installCommand())

    val all: List<DebianAction> = listOf(
        DebianAction(
            title = R.string.debian_install_title,
            description = R.string.debian_install_desc,
            command = INSTALL_COMMAND,
            runnable = true,
            availability = Availability.WHEN_MISSING,
            preview = "proot-distro install debian && install yt-dlp"
        ),
        DebianAction(
            title = R.string.debian_update_title,
            description = R.string.debian_update_desc,
            command = ShellLine.wrapForDebianRoot("apt update && apt upgrade -y"),
            runnable = true,
            availability = Availability.WHEN_INSTALLED,
            preview = "apt update && apt upgrade -y"
        ),
        DebianAction(
            title = R.string.debian_login_user_title,
            description = R.string.debian_login_user_desc,
            command = ShellLine.guardAndroid("molina-login user"),
            runnable = true,
            availability = Availability.WHEN_INSTALLED_OUTSIDE,
            preview = "molina-login user"
        ),
        DebianAction(
            title = R.string.debian_login_root_title,
            description = R.string.debian_login_root_desc,
            command = ShellLine.guardAndroid("molina-login root --yes"),
            runnable = true,
            availability = Availability.WHEN_INSTALLED_OUTSIDE,
            preview = "molina-login root",
            confirmMessage = R.string.debian_login_root_confirm
        ),
        DebianAction(
            title = R.string.debian_logout_title,
            description = R.string.debian_logout_desc,
            command = "exit",
            runnable = true,
            availability = Availability.WHEN_IN_DEBIAN,
            confirmMessage = R.string.debian_logout_confirm
        )
    )
}
