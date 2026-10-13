#!/usr/bin/env python3
"""Panel playlist mpv semi-transparan di tepi kanan, dan tombol putar layar mengambang.

- Daftar channel berupa panel gelap semi-transparan setinggi layar di sisi kanan; video tetap
  terlihat dan tidak diredupkan. Ketuk di luar panel menutupnya; geser lagi membukanya kembali.
- Pada siaran live panel dibuka tanpa menjeda video.
- Tombol putar layar dipindah dari bilah atas menjadi tombol bulat semi-transparan tepat di bawah
  ikon setting, ikut tampil dan memudar bersama kontrol.

Prasyarat: patch_mpv_live_ui.py sudah dijalankan. Idempotent; jangkar yang hilang menghentikan
skrip tanpa menulis apa pun. Jalankan dari akar repo.
"""
import sys
from pathlib import Path

MAIN = Path("engines/mpv-android/app/src/main")
ACTIVITY = MAIN / "java/com/molina/suite/mpv/MPVActivity.kt"
LAYOUT = MAIN / "res/layout/player.xml"

NEW_FILES = {
    MAIN / "res/drawable/playlist_panel_bg.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#B3101418" />
    <corners
        android:bottomLeftRadius="16dp"
        android:topLeftRadius="16dp" />
</shape>
""",
    MAIN / "res/drawable/molina_float_button_bg.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#99000000" />
</shape>
""",
    MAIN / "res/anim/playlist_panel_in.xml": """<?xml version="1.0" encoding="utf-8"?>
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="200"
    android:fromXDelta="100%p"
    android:interpolator="@android:interpolator/decelerate_quad"
    android:toXDelta="0" />
""",
    MAIN / "res/anim/playlist_panel_out.xml": """<?xml version="1.0" encoding="utf-8"?>
<translate xmlns:android="http://schemas.android.com/apk/res/android"
    android:duration="160"
    android:fromXDelta="0"
    android:interpolator="@android:interpolator/accelerate_quad"
    android:toXDelta="100%p" />
""",
    MAIN / "res/values/molina_playlist_panel.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="PlaylistPanelAnimation">
        <item name="android:windowEnterAnimation">@anim/playlist_panel_in</item>
        <item name="android:windowExitAnimation">@anim/playlist_panel_out</item>
    </style>

    <!-- Panel gelap semi-transparan: tema dialog gelap agar teks dan ikon otomatis terang. -->
    <style name="PlaylistPanelTheme" parent="Theme.AppCompat.Dialog.Alert">
        <item name="android:windowBackground">@drawable/playlist_panel_bg</item>
        <item name="android:windowAnimationStyle">@style/PlaylistPanelAnimation</item>
        <item name="android:backgroundDimEnabled">false</item>
        <item name="android:windowCloseOnTouchOutside">true</item>
        <item name="android:windowMinWidthMajor">0dp</item>
        <item name="android:windowMinWidthMinor">0dp</item>
        <item name="colorAccent">@color/accent</item>
    </style>
</resources>
""",
}

OLD_ROTATE_IN_TOPBAR = """            <ImageButton
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
FLOAT_ROTATE = """        <!-- tombol putar layar: bulat, semi-transparan, tepat di bawah ikon setting -->
        <ImageButton
            android:id="@+id/topRotateBtn"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:layout_alignParentEnd="true"
            android:layout_below="@id/top_controls"
            android:layout_marginTop="12dp"
            android:layout_marginEnd="8dp"
            android:alpha="0.65"
            android:background="@drawable/molina_float_button_bg"
            android:contentDescription="@string/ui_rotate_screen"
            android:padding="10dp"
            android:scaleType="fitCenter"
            app:srcCompat="@drawable/ic_screen_rotation_24dp"
            app:tint="@color/tint_normal" />

"""
LAYOUT_ANCHOR = "        <!-- mismatching width/height so that the button appears exactly square?! -->\n"

DOCK_FUN = """    /** Menempelkan dialog playlist di tepi kanan sebagai panel setinggi layar. */
    private fun dockPlaylistPanel(dialog: AlertDialog) {
        val window = dialog.window ?: return
        val metrics = resources.displayMetrics
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = (metrics.widthPixels * (if (landscape) 0.45f else 0.8f)).toInt()
            .coerceIn((260 * metrics.density).toInt(), (440 * metrics.density).toInt())
            .coerceAtMost(metrics.widthPixels)
        window.setGravity(Gravity.END)
        window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
    }

"""

# (nama, jangkar, pengganti, penanda bahwa sudah diterapkan)
EDITS = [
    (
        "buka panel live tanpa menjeda video",
        "                    liveSwipeOpensPlaylist = false\n                    openPlaylistMenu(pauseForDialog())\n",
        "                    liveSwipeOpensPlaylist = false\n                    openPlaylistMenu({})\n",
        "openPlaylistMenu({})",
    ),
    (
        "dialog playlist bertema panel",
        "            override fun onItemPicked(item: MPVView.PlaylistItem) {\n"
        "                MPVLib.setPropertyInt(\"playlist-pos\", item.index)\n"
        "                dialog.dismiss()\n"
        "            }\n"
        "        }\n\n"
        "        dialog = with (AlertDialog.Builder(this)) {\n"
        "            val inflater = LayoutInflater.from(context)\n"
        "            setView(impl.buildView(inflater))\n"
        "            setOnDismissListener { restore() }\n"
        "            create()\n"
        "        }\n"
        "        dialog.show()\n",
        "            override fun onItemPicked(item: MPVView.PlaylistItem) {\n"
        "                MPVLib.setPropertyInt(\"playlist-pos\", item.index)\n"
        "                dialog.dismiss()\n"
        "            }\n"
        "        }\n\n"
        "        dialog = with (AlertDialog.Builder(this, R.style.PlaylistPanelTheme)) {\n"
        "            val inflater = LayoutInflater.from(context)\n"
        "            setView(impl.buildView(inflater))\n"
        "            setOnDismissListener { restore() }\n"
        "            create()\n"
        "        }\n"
        "        dialog.show()\n"
        "        dockPlaylistPanel(dialog)\n",
        "dockPlaylistPanel(dialog)\n",
    ),
    (
        "fungsi dockPlaylistPanel",
        "    private fun pickDecoder() {\n",
        DOCK_FUN + "    private fun pickDecoder() {\n",
        "private fun dockPlaylistPanel",
    ),
    (
        "ketersediaan tombol putar",
        "    private var lockedUI = false\n",
        "    private var lockedUI = false\n"
        "    // perangkat tanpa mode potret (Android TV) tidak memakai tombol putar layar\n"
        "    private val rotateButtonAvailable by lazy {\n"
        "        packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT)\n"
        "    }\n",
        "private val rotateButtonAvailable",
    ),
    (
        "konstanta alpha tombol putar",
        "        private const val LIVE_SWIPE_PLAYLIST_DIFF = 30f\n",
        "        private const val LIVE_SWIPE_PLAYLIST_DIFF = 30f\n"
        "        // transparansi tombol putar layar saat kontrol tampil penuh\n"
        "        private const val ROTATE_BTN_ALPHA = 0.65f\n",
        "ROTATE_BTN_ALPHA = 0.65f",
    ),
    (
        "tombol putar ikut memudar",
        "            binding.topControls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n",
        "            binding.topControls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n"
        "            binding.topRotateBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n",
        "binding.topRotateBtn.animate().alpha(0f)",
    ),
    (
        "batalkan animasi tombol putar",
        "        binding.topControls.animate().cancel()\n",
        "        binding.topControls.animate().cancel()\n"
        "        binding.topRotateBtn.animate().cancel()\n",
        "binding.topRotateBtn.animate().cancel()",
    ),
    (
        "alpha tombol putar saat kontrol tampil",
        "        binding.topControls.alpha = 1f\n",
        "        binding.topControls.alpha = 1f\n"
        "        binding.topRotateBtn.alpha = ROTATE_BTN_ALPHA\n",
        "binding.topRotateBtn.alpha = ROTATE_BTN_ALPHA",
    ),
    (
        "tampilkan tombol putar bersama kontrol",
        "            binding.topControls.visibility = View.VISIBLE\n",
        "            binding.topControls.visibility = View.VISIBLE\n"
        "            binding.topRotateBtn.visibility = if (rotateButtonAvailable) View.VISIBLE else View.GONE\n",
        "if (rotateButtonAvailable) View.VISIBLE",
    ),
    (
        "sembunyikan tombol putar bersama kontrol",
        "        binding.topControls.visibility = View.GONE\n",
        "        binding.topControls.visibility = View.GONE\n"
        "        binding.topRotateBtn.visibility = View.GONE\n",
        "        binding.topRotateBtn.visibility = View.GONE\n        binding.statsTextView",
    ),
]


def main() -> None:
    for p in (ACTIVITY, LAYOUT):
        if not p.is_file():
            sys.exit(f"Tidak ditemukan: {p} (jalankan dari akar repo)")

    activity = ACTIVITY.read_text(encoding="utf-8")
    layout = LAYOUT.read_text(encoding="utf-8")

    if "liveSwipeOpensPlaylist" not in activity:
        sys.exit("Jalankan tools/patch_mpv_live_ui.py lebih dulu")

    for name, anchor, new, marker in EDITS:
        if marker in activity:
            print(f"Sudah ada: {name}")
            continue
        if activity.count(anchor) != 1:
            sys.exit(f"Jangkar '{name}' tidak ditemukan tepat satu kali di {ACTIVITY.name}")
        activity = activity.replace(anchor, new)
        print(f"Diterapkan: {name}")

    if "molina_float_button_bg" not in layout:
        if layout.count(LAYOUT_ANCHOR) != 1:
            sys.exit("Jangkar unlockBtn tidak ditemukan tepat satu kali di player.xml")
        if OLD_ROTATE_IN_TOPBAR in layout:
            layout = layout.replace(OLD_ROTATE_IN_TOPBAR, "")
        elif "@+id/topRotateBtn" in layout:
            sys.exit("Tombol putar di player.xml bukan bentuk yang dikenal; hentikan agar tidak ganda")
        layout = layout.replace(LAYOUT_ANCHOR, FLOAT_ROTATE + LAYOUT_ANCHOR)
        print("Diterapkan: tombol putar mengambang di player.xml")

    for path, text in NEW_FILES.items():
        if not path.is_file() or path.read_text(encoding="utf-8") != text:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
            print(f"Ditulis: {path}")

    for path, text in ((ACTIVITY, activity), (LAYOUT, layout)):
        if path.read_text(encoding="utf-8") != text:
            path.write_text(text, encoding="utf-8")
            print(f"Ditulis: {path}")


main()
