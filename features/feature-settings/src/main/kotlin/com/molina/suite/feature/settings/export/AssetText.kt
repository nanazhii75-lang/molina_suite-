package com.molina.suite.feature.settings.export

import android.content.Context
import java.io.IOException

/** Teks sebuah aset aplikasi, dibaca sekali saat pertama dipakai; null bila aset tidak terbaca. */
internal class AssetText(context: Context, private val path: String) {

    private val appContext = context.applicationContext

    val text: String? by lazy {
        try {
            appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }
}
