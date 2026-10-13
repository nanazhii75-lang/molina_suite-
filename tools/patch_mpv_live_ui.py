#!/usr/bin/env python3
"""UI siaran live di mpv: tombol putar layar di bilah atas, dan geser horizontal membuka daftar channel.

Idempotent: setiap perubahan diperiksa dulu; jangkar yang hilang menghentikan skrip tanpa menulis
apa pun. Jalankan dari akar repo.
"""
import sys
from pathlib import Path

MAIN = Path("engines/mpv-android/app/src/main")
ACTIVITY = MAIN / "java/com/molina/suite/mpv/MPVActivity.kt"
LAYOUT = MAIN / "res/layout/player.xml"
STRINGS = MAIN / "res/values/strings.xml"
ICON = MAIN / "res/drawable/ic_screen_rotation_24dp.xml"

ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M16.48,2.52c3.27,1.55 5.61,4.72 5.97,8.48h1.5C23.44,4.84 18.29,0 12,0l-0.66,0.03 3.81,3.81 1.33,-1.32zM10.23,1.75c-0.59,-0.59 -1.54,-0.59 -2.12,0L1.75,8.11c-0.59,0.59 -0.59,1.54 0,2.12l12.02,12.02c0.59,0.59 1.54,0.59 2.12,0l6.36,-6.36c0.59,-0.59 0.59,-1.54 0,-2.12L10.23,1.75zM14.83,21.19L2.81,9.17l6.36,-6.36 12.02,12.02 -6.36,6.36zM7.52,21.48C4.25,19.94 1.91,16.76 1.55,13L0.05,13C0.56,19.16 5.71,24 12,24l0.66,-0.03 -3.81,-3.81 -1.33,1.32z" />
</vector>
"""

ROTATE_BUTTON = """            <ImageButton
                android:id="@+id/topRotateBtn"
                style="?buttonBarButtonStyle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:contentDescription="@string/ui_rotate_screen"
                android:minWidth="40dp"
                android:minHeight="40dp"
                android:padding="4dp"
                app:srcCompat="@drawable/ic_screen_rotation_24dp"
                app:tint="@color/tint_normal" />

"""
LOCK_ANCHOR = """            <ImageButton
                android:id="@+id/topLockBtn\""""

STRING_ANCHOR = '    <string name="switch_orientation">Switch Orientation</string>\n'
STRING_NEW = (
    '    <string name="ui_rotate_screen" translatable="false">Putar layar</string>\n'
    '    <string name="ui_release_open_playlist" translatable="false">Lepas untuk membuka daftar channel</string>\n'
)

# (nama, jangkar, pengganti, penanda bahwa sudah diterapkan)
EDITS = [
    (
        "klik tombol putar layar",
        "            topPiPBtn.setOnClickListener { goIntoPiP() }\n",
        "            topPiPBtn.setOnClickListener { goIntoPiP() }\n"
        "            topRotateBtn.setOnClickListener {\n"
        "                autoRotationMode = \"manual\"\n"
        "                cycleOrientation()\n"
        "            }\n",
        "topRotateBtn.setOnClickListener",
    ),
    (
        "sembunyikan tombol putar di perangkat tanpa mode potret",
        "        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))\n"
        "            binding.topLockBtn.visibility = View.GONE\n",
        "        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))\n"
        "            binding.topLockBtn.visibility = View.GONE\n"
        "        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))\n"
        "            binding.topRotateBtn.visibility = View.GONE\n",
        "binding.topRotateBtn.visibility",
    ),
    (
        "status geser live",
        "    private var pausedForSeek = 0\n",
        "    private var pausedForSeek = 0\n"
        "    // geser horizontal pada siaran live (tanpa durasi) sudah melewati ambang pembuka daftar channel\n"
        "    private var liveSwipeOpensPlaylist = false\n",
        "private var liveSwipeOpensPlaylist",
    ),
    (
        "reset status saat gestur mulai",
        "                pausedForSeek = 0\n\n                fadeHandler.removeCallbacks(fadeRunnable3)\n",
        "                pausedForSeek = 0\n                liveSwipeOpensPlaylist = false\n\n"
        "                fadeHandler.removeCallbacks(fadeRunnable3)\n",
        "liveSwipeOpensPlaylist = false\n\n                fadeHandler",
    ),
    (
        "geser live",
        "                val duration = (psc.duration / 1000f)\n"
        "                if (duration == 0f || initialSeek < 0)\n"
        "                    return\n",
        "                val duration = (psc.duration / 1000f)\n"
        "                if (duration == 0f && psc.playlistCount > 1) {\n"
        "                    // siaran live: seek tidak berarti, geser horizontal membuka daftar channel\n"
        "                    liveSwipeOpensPlaylist = kotlin.math.abs(diff) >= LIVE_SWIPE_PLAYLIST_DIFF\n"
        "                    gestureTextView.text = if (liveSwipeOpensPlaylist)\n"
        "                        getString(R.string.ui_release_open_playlist)\n"
        "                    else\n"
        "                        \"\"\n"
        "                    return\n"
        "                }\n"
        "                if (duration == 0f || initialSeek < 0)\n"
        "                    return\n",
        "LIVE_SWIPE_PLAYLIST_DIFF\n",
    ),
    (
        "buka daftar saat gestur selesai",
        "                    player.paused = false\n                gestureTextView.visibility = View.GONE\n",
        "                    player.paused = false\n                gestureTextView.visibility = View.GONE\n"
        "                if (liveSwipeOpensPlaylist) {\n"
        "                    liveSwipeOpensPlaylist = false\n"
        "                    openPlaylistMenu(pauseForDialog())\n"
        "                }\n",
        "liveSwipeOpensPlaylist = false\n                    openPlaylistMenu",
    ),
    (
        "konstanta ambang",
        '        private const val TAG = "mpv"\n',
        '        private const val TAG = "mpv"\n'
        "        // jarak geser (dalam satuan gestur seek, 150 = lebar layar penuh) pembuka daftar channel live\n"
        "        private const val LIVE_SWIPE_PLAYLIST_DIFF = 30f\n",
        "private const val LIVE_SWIPE_PLAYLIST_DIFF",
    ),
]


def write_if_changed(path: Path, text: str) -> None:
    if not path.is_file() or path.read_text(encoding="utf-8") != text:
        path.write_text(text, encoding="utf-8")
        print(f"Ditulis: {path}")


def main() -> None:
    for p in (ACTIVITY, LAYOUT, STRINGS):
        if not p.is_file():
            sys.exit(f"Tidak ditemukan: {p} (jalankan dari akar repo)")

    activity = ACTIVITY.read_text(encoding="utf-8")
    layout = LAYOUT.read_text(encoding="utf-8")
    strings = STRINGS.read_text(encoding="utf-8")

    for name, anchor, new, marker in EDITS:
        if marker in activity:
            print(f"Sudah ada: {name}")
            continue
        if activity.count(anchor) != 1:
            sys.exit(f"Jangkar '{name}' tidak ditemukan tepat satu kali di {ACTIVITY.name}")
        activity = activity.replace(anchor, new)
        print(f"Diterapkan: {name}")

    if "@+id/topRotateBtn" not in layout:
        if layout.count(LOCK_ANCHOR) != 1:
            sys.exit("Jangkar topLockBtn tidak ditemukan tepat satu kali di player.xml")
        layout = layout.replace(LOCK_ANCHOR, ROTATE_BUTTON + LOCK_ANCHOR)
        print("Diterapkan: tombol putar di player.xml")

    if 'name="ui_rotate_screen"' not in strings:
        if strings.count(STRING_ANCHOR) != 1:
            sys.exit("Jangkar switch_orientation tidak ditemukan tepat satu kali di strings.xml")
        strings = strings.replace(STRING_ANCHOR, STRING_ANCHOR + STRING_NEW)
        print("Diterapkan: string baru")

    write_if_changed(ICON, ICON_XML)
    write_if_changed(ACTIVITY, activity)
    write_if_changed(LAYOUT, layout)
    write_if_changed(STRINGS, strings)


main()
