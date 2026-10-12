package com.molina.suite.ui

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.molina.suite.R
import com.molina.suite.core.common.EngineId

/** Ditampilkan saat engine untuk sebuah tab belum didaftarkan pada build ini. */
class EngineUnavailableFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val engineId = EngineId.valueOf(requireArguments().getString(ARG_ENGINE_ID).orEmpty())
        val padding = (24 * resources.displayMetrics.density).toInt()
        return TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setTextColor(ContextCompat.getColor(context, R.color.molina_text_secondary))
            textSize = 14f
            text = getString(R.string.engine_unavailable, getString(engineId.labelRes()))
        }
    }

    companion object {
        private const val ARG_ENGINE_ID = "engine_id"

        fun newInstance(id: EngineId) = EngineUnavailableFragment().apply {
            arguments = bundleOf(ARG_ENGINE_ID to id.name)
        }
    }
}
