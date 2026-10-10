package com.molina.suite.feature.settings.debian

import android.content.Context
import java.io.File

/** Memeriksa apakah rootfs Debian proot-distro sudah terpasang di sandbox aplikasi. */
internal object DebianStatus {

    // $PREFIX = filesDir/usr; rootfs di $PREFIX/var/lib/proot-distro/installed-rootfs/debian.
    private const val ROOTFS_RELATIVE = "usr/var/lib/proot-distro/installed-rootfs/debian"

    /** Memblokir I/O: panggil dari Dispatchers.IO. */
    fun isInstalled(context: Context): Boolean {
        val dir = File(context.filesDir, ROOTFS_RELATIVE)
        if (!dir.isDirectory) return false
        val children = dir.list() ?: return false
        return children.isNotEmpty()
    }
}
