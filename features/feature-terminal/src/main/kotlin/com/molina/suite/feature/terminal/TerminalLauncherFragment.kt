package com.molina.suite.feature.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

/**
 * Isi tab Terminal. UI terminal penuh (TermuxActivity) berjalan sebagai Activity
 * sendiri di proses yang sama; fragment ini hanya pintu masuknya. Activity dipanggil
 * lewat nama kelas (bukan referensi langsung) agar feature tidak terikat ke tipe internal engine.
 */
class TerminalLauncherFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val gap = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(context).apply {
            text = getString(R.string.terminal_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val description = TextView(context).apply {
            text = getString(R.string.terminal_description)
            textSize = 14f
            gravity = Gravity.CENTER
        }
        val open = MaterialButton(context).apply {
            text = getString(R.string.terminal_open)
            setOnClickListener { openTerminal() }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(gap * 2, gap * 2, gap * 2, gap * 2)
            addView(title)
            addView(description, spaced(gap))
            addView(open, spaced(gap))
        }
    }

    private fun spaced(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = top }

    private fun openTerminal() {
        try {
            startActivity(Intent().setClassName(requireContext(), TERMINAL_ACTIVITY_CLASS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.terminal_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val TERMINAL_ACTIVITY_CLASS = "com.molina.suite.terminal.app.TermuxActivity"
    }
}
