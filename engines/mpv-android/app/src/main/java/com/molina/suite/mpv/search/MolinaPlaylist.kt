package com.molina.suite.mpv.search

import android.content.Intent

/**
 * Serah-terima playlist hasil pencarian dari home mpv ke pemutar lewat ekstra Intent, dan
 * pembuatan perintah mpv yang melengkapinya. Pemutar sudah memuat item terpilih lebih dulu;
 * perintah di sini menambah sisanya sehingga urutan playlist sama dengan urutan daftar hasil
 * dan Next/Previous mengikutinya.
 */
object MolinaPlaylist {

    private const val EXTRA_URLS = "molina_playlist_urls"
    private const val EXTRA_INDEX = "molina_playlist_index"
    private const val EXTRA_TITLES = "molina_playlist_titles"
    private const val MAX_TITLE_LENGTH = 200
    private const val WATCH_PREFIX = "https://www.youtube.com/watch?v="
    // Sama dengan jumlah hasil maksimum pencarian: seluruh halaman yang dibuka masuk playlist.
    const val MAX_ITEMS = YoutubeSearchController.PAGE_SIZE * YoutubeSearchController.MAX_PAGES

    fun put(intent: Intent, urls: List<String>, titles: List<String>, index: Int) {
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

    /**
     * Mengembalikan perintah pelengkap playlist, atau null bila [intent] tidak membawa playlist
     * yang valid. [currentPath] adalah file yang sudah dimuat pemutar; harus sama dengan item
     * pada indeks terpilih. Hanya URL tonton YouTube https yang diterima karena pemutar juga
     * dapat dipanggil dari aplikasi lain.
     */
    fun read(intent: Intent?, currentPath: String): List<Array<String>>? {
        if (intent == null) return null
        val urls = intent.getStringArrayListExtra(EXTRA_URLS) ?: return null
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        if (urls.size < 2 || urls.size > MAX_ITEMS) return null
        if (index < 0 || index >= urls.size) return null
        if (urls[index] != currentPath) return null
        if (urls.any { !it.startsWith(WATCH_PREFIX) }) return null
        return completionCommands(urls, index)
    }

    /**
     * Playlist awal hanya berisi item [index] (posisi 0). Item sesudahnya ditambahkan berurutan;
     * item sebelumnya ditambahkan di akhir lalu dipindah ke posisinya masing-masing.
     */
    private fun completionCommands(urls: List<String>, index: Int): List<Array<String>> {
        val commands = ArrayList<Array<String>>()
        for (k in index + 1 until urls.size) {
            commands.add(arrayOf("loadfile", urls[k], "append"))
        }
        var count = urls.size - index // item terpilih + yang sudah ditambahkan
        for (j in 0 until index) {
            commands.add(arrayOf("loadfile", urls[j], "append"))
            commands.add(arrayOf("playlist-move", count.toString(), j.toString()))
            count += 1
        }
        return commands
    }
}
