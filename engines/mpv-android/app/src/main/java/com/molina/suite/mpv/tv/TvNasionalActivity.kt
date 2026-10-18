package com.molina.suite.mpv.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class TvNasionalActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TV NASIONAL"
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.setPadding(pad, pad, pad, pad)
        val w = ViewGroup.LayoutParams.MATCH_PARENT
        val h = ViewGroup.LayoutParams.WRAP_CONTENT
        for (group in TvCatalog.groups) {
            val header = TextView(this)
            header.text = group.title
            header.textSize = 18f
            header.setTypeface(header.typeface, Typeface.BOLD)
            header.setPadding(0, pad, 0, pad / 2)
            column.addView(header, LinearLayout.LayoutParams(w, h))
            for (channel in group.channels) {
                val button = Button(this)
                button.text = channel.name
                button.setOnClickListener { open(channel) }
                column.addView(button, LinearLayout.LayoutParams(w, h))
            }
        }
        val scroll = ScrollView(this)
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun open(channel: TvChannel) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channel.url))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val msg = "TIDAK ADA APLIKASI UNTUK MEMBUKA " + channel.name
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
}
