#!/usr/bin/env python3
"""Tambah modul core:core-storage (folder bersama /sdcard/Molina/) dan daftarkan ke build.

Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_core_storage.py [--apply]
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


BUILD_GRADLE = '''plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.molina.suite.core.storage"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
'''

MANIFEST = '''<?xml version="1.0" encoding="utf-8"?>
<manifest />
'''

STORAGE_KT = '''package com.molina.suite.core.storage

import android.os.Environment
import java.io.File

/**
 * Satu-satunya sumber lokasi folder bersama antar-modul molina-suite.
 * Modul lain tidak boleh menulis literal "/sdcard/Molina" sendiri.
 *
 * Membuat folder di penyimpanan eksternal pada API 26 membutuhkan izin
 * WRITE_EXTERNAL_STORAGE yang sudah diberikan; bila belum, [ensureDirectories]
 * mengembalikan [EnsureResult.Failed] dengan alasan yang jelas.
 */
object MolinaStorage {

    /** Nama folder bersama di penyimpanan eksternal: /sdcard/Molina/ (M kapital). */
    const val SHARED_DIR_NAME = "Molina"

    /** Sub-folder unduhan: /sdcard/Molina/Downloads/. */
    const val DOWNLOADS_DIR_NAME = "Downloads"

    sealed interface EnsureResult {
        data class Ready(val root: File) : EnsureResult
        data class Failed(val dir: File, val reason: String) : EnsureResult
    }

    @Suppress("DEPRECATION")
    fun sharedRoot(): File = File(Environment.getExternalStorageDirectory(), SHARED_DIR_NAME)

    fun downloadsDir(): File = File(sharedRoot(), DOWNLOADS_DIR_NAME)

    /** Membuat folder bersama beserta sub-foldernya bila belum ada. */
    fun ensureDirectories(): EnsureResult {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            return EnsureResult.Failed(
                sharedRoot(),
                "Penyimpanan eksternal tidak terpasang (status: $state)"
            )
        }
        for (dir in listOf(sharedRoot(), downloadsDir())) {
            if (dir.isDirectory) continue
            val created = try {
                dir.mkdirs()
            } catch (e: SecurityException) {
                return EnsureResult.Failed(dir, "Izin ditolak: ${e.message}")
            }
            if (!created && !dir.isDirectory) {
                return EnsureResult.Failed(
                    dir,
                    "Gagal membuat folder; izin penyimpanan belum diberikan atau penyimpanan tidak bisa ditulis"
                )
            }
        }
        return EnsureResult.Ready(sharedRoot())
    }
}
'''

NEW_FILES = {
    "core/core-storage/build.gradle.kts": BUILD_GRADLE,
    "core/core-storage/src/main/AndroidManifest.xml": MANIFEST,
    "core/core-storage/src/main/kotlin/com/molina/suite/core/storage/MolinaStorage.kt": STORAGE_KT,
}

EDITS = [
    # (path, jangkar, tambahan setelah jangkar, penanda sudah diterapkan)
    (
        "settings.gradle.kts",
        'include(":core:core-common")\n',
        'include(":core:core-storage")\n',
        'include(":core:core-storage")',
    ),
    (
        "app/build.gradle.kts",
        '    implementation(project(":core:core-common"))\n',
        '    implementation(project(":core:core-storage"))\n',
        'project(":core:core-storage")',
    ),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    pending = []  # (path, teks_baru, eol)

    for path, content in NEW_FILES.items():
        if os.path.exists(path):
            existing, _ = load(path)
            if existing == content:
                print("SUDAH: " + path)
                continue
            die(path + " sudah ada dengan isi berbeda; periksa manual")
        print("BARU: %s (%d baris)" % (path, content.count("\n")))
        pending.append((path, content, "\n"))

    for path, anchor, addition, marker in EDITS:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        if marker in text:
            print("SUDAH: " + path)
            continue
        n = text.count(anchor)
        if n != 1:
            die("%s: jangkar harus tepat 1 kali, ditemukan %d" % (path, n))
        new = text.replace(anchor, anchor + addition, 1)
        diff = difflib.unified_diff(
            text.splitlines(), new.splitlines(),
            "sebelum/" + path, "sesudah/" + path, lineterm="", n=2,
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
