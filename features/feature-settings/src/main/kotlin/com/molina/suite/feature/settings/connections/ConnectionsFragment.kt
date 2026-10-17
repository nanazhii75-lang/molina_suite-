package com.molina.suite.feature.settings.connections

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.molina.suite.core.common.ConnectionLedger
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable

/**
 * Catatan Koneksi: host yang dihubungi aplikasi sesi ini. Hanya membaca [ConnectionLedger]
 * (di memori); tidak ada yang ditulis ke disk dan tidak ada yang dikirim ke mana pun.
 */
class ConnectionsFragment : Fragment(R.layout.fragment_connections), Refreshable {

    private val listAdapter = ConnectionsAdapter()
    private var status: TextView? = null
    private var empty: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        status = view.findViewById(R.id.connections_status)
        empty = view.findViewById(R.id.connections_empty)
        val list = view.findViewById<RecyclerView>(R.id.connections_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = listAdapter
        view.findViewById<MaterialButton>(R.id.connections_refresh).setOnClickListener { refresh() }
        view.findViewById<MaterialButton>(R.id.connections_clear).setOnClickListener {
            ConnectionLedger.shared.clear()
            refresh()
        }
        refresh()
    }

    override fun refresh() {
        if (view == null) return
        val rows = ConnectionRows.of(ConnectionLedger.shared.snapshot())
        listAdapter.submit(rows)
        if (rows.isEmpty()) {
            status?.setText(R.string.connections_summary_none)
            empty?.visibility = View.VISIBLE
        } else {
            val summary = ConnectionRows.summarize(rows)
            status?.text = getString(R.string.connections_summary, summary.hosts, summary.insecure)
            empty?.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        status = null
        empty = null
        super.onDestroyView()
    }
}
