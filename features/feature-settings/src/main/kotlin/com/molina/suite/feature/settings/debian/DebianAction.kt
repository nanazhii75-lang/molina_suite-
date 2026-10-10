package com.molina.suite.feature.settings.debian

import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

/** Kapan sebuah aksi Debian boleh dipakai. */
internal enum class Availability { ALWAYS, WHEN_MISSING, WHEN_INSTALLED }

/** Satu kartu aksi pada layar Debian. [command] diketik ke Terminal bila [runnable]. */
internal data class DebianAction(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val command: String,
    val runnable: Boolean,
    val availability: Availability
)

internal object DebianActions {
    val all: List<DebianAction> = listOf(
        DebianAction(
            title = R.string.debian_install_title,
            description = R.string.debian_install_desc,
            command = "proot-distro install debian",
            runnable = true,
            availability = Availability.WHEN_MISSING
        ),
        DebianAction(
            title = R.string.debian_update_title,
            description = R.string.debian_update_desc,
            command = "proot-distro login debian -- bash -c \"apt update && apt upgrade -y\"",
            runnable = true,
            availability = Availability.WHEN_INSTALLED
        ),
        DebianAction(
            title = R.string.debian_login_title,
            description = R.string.debian_login_desc,
            command = "proot-distro login debian",
            runnable = true,
            availability = Availability.WHEN_INSTALLED
        ),
        DebianAction(
            title = R.string.debian_logout_title,
            description = R.string.debian_logout_desc,
            command = "exit",
            runnable = false,
            availability = Availability.ALWAYS
        )
    )
}
