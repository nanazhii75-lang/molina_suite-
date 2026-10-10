package com.molina.suite.feature.mpv

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Pengganti tab mpv saat library native belum ter-bundel pada build ini. */
internal class MpvUnavailableFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val padding = (24 * resources.displayMetrics.density).toInt()
        return TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setText(R.string.mpv_native_unavailable)
        }
    }
}
