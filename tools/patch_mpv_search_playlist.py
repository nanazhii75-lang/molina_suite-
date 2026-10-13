#!/usr/bin/env python3
"""Hasil pencarian YouTube jadi playlist lengkap di pemutar mpv.

- Playlist memuat SEMUA hasil yang sudah diambil (halaman 1..n, sampai 30 item), bukan hanya satu halaman.
- Judul hasil pencarian dikirim ke pemutar dan dipakai di panel playlist (bukan "watch?v=...").
- Tombol daftar bulat semi-transparan di bawah tombol putar layar membuka panel playlist untuk
  playlist apa pun yang berisi lebih dari satu item (YouTube maupun IPTV).

Prasyarat: patch_mpv_live_ui.py dan patch_mpv_playlist_panel.py sudah dijalankan.
Idempotent; jangkar yang hilang menghentikan skrip tanpa menulis apa pun. Jalankan dari akar repo.
"""
import sys
from pathlib import Path

MAIN = Path("engines/mpv-android/app/src/main")
PKG = MAIN / "java/com/molina/suite/mpv"
FILES = {
    "activity": PKG / "MPVActivity.kt",
    "dialog": PKG / "PlaylistDialog.kt",
    "fragment": PKG / "MainScreenFragment.kt",
    "playlist": PKG / "search/MolinaPlaylist.kt",
    "controller": PKG / "search/YoutubeSearchController.kt",
    "layout": MAIN / "res/layout/player.xml",
}
ICON = MAIN / "res/drawable/ic_playlist_24dp.xml"
ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M3,13h2v-2L3,11v2zM3,17h2v-2L3,15v2zM3,9h2L5,7L3,7v2zM7,13h14v-2L7,11v2zM7,17h14v-2L7,15v2zM7,7v2h14L21,7L7,7z" />
</vector>
"""

LIST_BUTTON = """        <!-- tombol daftar playlist: bulat, semi-transparan, di bawah tombol putar layar -->
        <ImageButton
            android:id="@+id/openPlaylistBtn"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:layout_alignParentEnd="true"
            android:layout_below="@id/topRotateBtn"
            android:layout_marginTop="10dp"
            android:layout_marginEnd="8dp"
            android:alpha="0.65"
            android:background="@drawable/molina_float_button_bg"
            android:contentDescription="@string/ui_open_playlist"
            android:padding="10dp"
            android:scaleType="fitCenter"
            android:visibility="gone"
            app:srcCompat="@drawable/ic_playlist_24dp"
            app:tint="@color/tint_normal" />

"""
LAYOUT_ANCHOR = "        <!-- mismatching width/height so that the button appears exactly square?! -->\n"
STRINGS = MAIN / "res/values/strings.xml"
STRING_ANCHOR = '    <string name="ui_rotate_screen" translatable="false">Putar layar</string>\n'
STRING_NEW = '    <string name="ui_open_playlist" translatable="false">Daftar playlist</string>\n'

CONTROLLER_FUN = """    /** Semua hasil yang sudah diambil, urut halaman 1..n tanpa celah; kosong bila belum ada. */
    fun fetchedItems(): List<YoutubeResult> {
        val all = ArrayList<YoutubeResult>()
        var page = 1
        while (true) {
            val data = pages[page] ?: break
            all.addAll(data.items)
            page += 1
        }
        return all
    }

"""

PLAYLIST_PUT_OLD = """    fun put(intent: Intent, urls: List<String>, index: Int) {
        intent.putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
        intent.putExtra(EXTRA_INDEX, index)
    }
"""
PLAYLIST_PUT_NEW = """    fun put(intent: Intent, urls: List<String>, titles: List<String>, index: Int) {
        intent.putStringArrayListExtra(EXTRA_URLS, ArrayList(urls))
        intent.putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
        intent.putExtra(EXTRA_INDEX, index)
    }

    /**
     * Judul per URL untuk panel playlist; kosong bila ekstra tidak ada atau jumlahnya tidak sama
     * dengan jumlah URL. Judul dipotong agar intent dari aplikasi lain tidak memuat teks raksasa.
     */
    fun titles(intent: Intent?): Map<String, String> {
        if (intent == null) return emptyMap()
        val urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: return emptyMap()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: return emptyMap()
        if (titles.size != urls.size) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (i in urls.indices) map[urls[i]] = titles[i].take(MAX_TITLE_LENGTH)
        return map
    }
"""

# (berkas, nama, jangkar, pengganti, penanda bahwa sudah diterapkan)
EDITS = [
    ("controller", "daftar hasil terambil",
     "    /** Mengulang pengambilan halaman yang terakhir diminta (setelah gagal). */\n",
     CONTROLLER_FUN + "    /** Mengulang pengambilan halaman yang terakhir diminta (setelah gagal). */\n",
     "fun fetchedItems()"),
    ("playlist", "ekstra judul",
     '    private const val EXTRA_INDEX = "molina_playlist_index"\n',
     '    private const val EXTRA_INDEX = "molina_playlist_index"\n'
     '    private const val EXTRA_TITLES = "molina_playlist_titles"\n'
     "    private const val MAX_TITLE_LENGTH = 200\n",
     "EXTRA_TITLES = "),
    ("playlist", "batas item 30 (6 halaman x 5)",
     "    private const val MAX_ITEMS = 20\n",
     "    private const val MAX_ITEMS = 30\n",
     "MAX_ITEMS = 30"),
    ("playlist", "put dengan judul", PLAYLIST_PUT_OLD, PLAYLIST_PUT_NEW, "fun titles(intent: Intent?)"),
    ("fragment", "pakai semua hasil terambil",
     "        val items = (YoutubeSearchController.state as? SearchState.Results)?.items\n"
     "        val index = items?.indexOfFirst { it.id == selected.id } ?: -1\n"
     "        if (items == null || index < 0) {\n",
     "        val items = YoutubeSearchController.fetchedItems()\n"
     "        val index = items.indexOfFirst { it.id == selected.id }\n"
     "        if (index < 0) {\n",
     "YoutubeSearchController.fetchedItems()"),
    ("fragment", "kirim judul",
     "        MolinaPlaylist.put(intent, items.map { it.watchUrl }, index)\n",
     "        MolinaPlaylist.put(intent, items.map { it.watchUrl }, items.map { it.title }, index)\n",
     "items.map { it.title }, index)"),
    ("fragment", "buang impor SearchState yang tak terpakai",
     "import com.molina.suite.mpv.search.SearchState\n", "", None),
    ("dialog", "konstruktor dengan judul",
     "internal class PlaylistDialog(private val player: MPVView) {\n",
     "internal class PlaylistDialog(\n"
     "    private val player: MPVView,\n"
     "    /** Judul per URL dari hasil pencarian; menggantikan nama berkas mentah seperti watch?v=... */\n"
     "    private val titles: Map<String, String> = emptyMap()\n"
     ") {\n",
     "private val titles: Map<String, String>"),
    ("dialog", "judul item",
     "                textView.text = item.title ?: Utils.fileBasename(item.filename)\n",
     "                textView.text = parent.titles[item.filename] ?: item.title ?: Utils.fileBasename(item.filename)\n",
     "parent.titles[item.filename]"),
    ("activity", "status judul playlist",
     "    private var pendingPlaylist: List<Array<String>>? = null\n",
     "    private var pendingPlaylist: List<Array<String>>? = null\n"
     "    // judul per URL yang dibawa intent pencarian; dipakai panel playlist\n"
     "    private var playlistTitles: Map<String, String> = emptyMap()\n",
     "private var playlistTitles"),
    ("activity", "baca judul saat mulai",
     "        pendingPlaylist = com.molina.suite.mpv.search.MolinaPlaylist.read(intent, filepath)\n"
     "        player.addObserver(this)\n",
     "        pendingPlaylist = com.molina.suite.mpv.search.MolinaPlaylist.read(intent, filepath)\n"
     "        playlistTitles = if (pendingPlaylist != null)\n"
     "            com.molina.suite.mpv.search.MolinaPlaylist.titles(intent)\n"
     "        else\n"
     "            emptyMap()\n"
     "        player.addObserver(this)\n",
     "playlistTitles = if (pendingPlaylist != null)"),
    ("activity", "baca judul saat intent baru",
     "            pendingPlaylist = com.molina.suite.mpv.search.MolinaPlaylist.read(intent, filepath)\n"
     "            MPVLib.command(arrayOf(\"loadfile\", filepath))\n",
     "            pendingPlaylist = com.molina.suite.mpv.search.MolinaPlaylist.read(intent, filepath)\n"
     "            playlistTitles = if (pendingPlaylist != null)\n"
     "                com.molina.suite.mpv.search.MolinaPlaylist.titles(intent)\n"
     "            else\n"
     "                emptyMap()\n"
     "            MPVLib.command(arrayOf(\"loadfile\", filepath))\n",
     "            playlistTitles = if (pendingPlaylist != null)"),
    ("activity", "dialog playlist memakai judul",
     "        val impl = PlaylistDialog(player)\n",
     "        val impl = PlaylistDialog(player, playlistTitles)\n",
     "PlaylistDialog(player, playlistTitles)"),
    ("activity", "klik tombol daftar",
     "            topRotateBtn.setOnClickListener {\n"
     "                autoRotationMode = \"manual\"\n"
     "                cycleOrientation()\n"
     "            }\n",
     "            topRotateBtn.setOnClickListener {\n"
     "                autoRotationMode = \"manual\"\n"
     "                cycleOrientation()\n"
     "            }\n"
     "            openPlaylistBtn.setOnClickListener { openPlaylistMenu({}) }\n",
     "openPlaylistBtn.setOnClickListener"),
    ("activity", "tombol daftar ikut memudar",
     "            binding.topRotateBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n",
     "            binding.topRotateBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n"
     "            binding.openPlaylistBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)\n",
     "binding.openPlaylistBtn.animate().alpha(0f)"),
    ("activity", "batalkan animasi tombol daftar",
     "        binding.topRotateBtn.animate().cancel()\n",
     "        binding.topRotateBtn.animate().cancel()\n"
     "        binding.openPlaylistBtn.animate().cancel()\n",
     "binding.openPlaylistBtn.animate().cancel()"),
    ("activity", "alpha tombol daftar saat kontrol tampil",
     "        binding.topRotateBtn.alpha = ROTATE_BTN_ALPHA\n",
     "        binding.topRotateBtn.alpha = ROTATE_BTN_ALPHA\n"
     "        binding.openPlaylistBtn.alpha = ROTATE_BTN_ALPHA\n",
     "binding.openPlaylistBtn.alpha = ROTATE_BTN_ALPHA"),
    ("activity", "tampilkan tombol daftar bersama kontrol",
     "            binding.topRotateBtn.visibility = if (rotateButtonAvailable) View.VISIBLE else View.GONE\n",
     "            binding.topRotateBtn.visibility = if (rotateButtonAvailable) View.VISIBLE else View.GONE\n"
     "            binding.openPlaylistBtn.visibility = if (psc.playlistCount > 1) View.VISIBLE else View.GONE\n",
     "if (psc.playlistCount > 1) View.VISIBLE"),
    ("activity", "sembunyikan tombol daftar bersama kontrol",
     "        binding.topRotateBtn.visibility = View.GONE\n        binding.statsTextView.visibility = View.GONE\n",
     "        binding.topRotateBtn.visibility = View.GONE\n"
     "        binding.openPlaylistBtn.visibility = View.GONE\n"
     "        binding.statsTextView.visibility = View.GONE\n",
     "binding.openPlaylistBtn.visibility = View.GONE"),
    ("activity", "sinkron tombol daftar dengan jumlah playlist",
     "    private fun updatePlaylistButtons() {\n        val plCount = psc.playlistCount\n",
     "    private fun updatePlaylistButtons() {\n"
     "        binding.openPlaylistBtn.visibility =\n"
     "            if (binding.controls.visibility == View.VISIBLE && psc.playlistCount > 1) View.VISIBLE else View.GONE\n"
     "        val plCount = psc.playlistCount\n",
     "binding.openPlaylistBtn.visibility =\n"),
]


def main() -> None:
    for p in FILES.values():
        if not p.is_file():
            sys.exit(f"Tidak ditemukan: {p} (jalankan dari akar repo)")
    texts = {k: p.read_text(encoding="utf-8") for k, p in FILES.items()}
    if "topRotateBtn" not in texts["activity"] or "dockPlaylistPanel" not in texts["activity"]:
        sys.exit("Jalankan patch_mpv_live_ui.py dan patch_mpv_playlist_panel.py lebih dulu")

    for key, name, anchor, new, marker in EDITS:
        text = texts[key]
        done = (marker in text) if marker is not None else (anchor not in text)
        if done:
            print(f"Sudah ada: {name}")
            continue
        if text.count(anchor) != 1:
            sys.exit(f"Jangkar '{name}' tidak ditemukan tepat satu kali di {FILES[key].name}")
        texts[key] = text.replace(anchor, new)
        print(f"Diterapkan: {name}")

    if "@+id/openPlaylistBtn" not in texts["layout"]:
        if texts["layout"].count(LAYOUT_ANCHOR) != 1:
            sys.exit("Jangkar unlockBtn tidak ditemukan tepat satu kali di player.xml")
        texts["layout"] = texts["layout"].replace(LAYOUT_ANCHOR, LIST_BUTTON + LAYOUT_ANCHOR)
        print("Diterapkan: tombol daftar di player.xml")

    strings = STRINGS.read_text(encoding="utf-8")
    if 'name="ui_open_playlist"' not in strings:
        if strings.count(STRING_ANCHOR) != 1:
            sys.exit("Jangkar ui_rotate_screen tidak ditemukan tepat satu kali di strings.xml")
        STRINGS.write_text(strings.replace(STRING_ANCHOR, STRING_ANCHOR + STRING_NEW), encoding="utf-8")
        print("Diterapkan: string baru")

    if not ICON.is_file() or ICON.read_text(encoding="utf-8") != ICON_XML:
        ICON.write_text(ICON_XML, encoding="utf-8")
        print(f"Ditulis: {ICON}")
    for key, p in FILES.items():
        if p.read_text(encoding="utf-8") != texts[key]:
            p.write_text(texts[key], encoding="utf-8")
            print(f"Ditulis: {p}")


main()
