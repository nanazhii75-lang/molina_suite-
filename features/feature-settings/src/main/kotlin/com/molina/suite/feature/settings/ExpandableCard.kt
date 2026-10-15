package com.molina.suite.feature.settings

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup

/**
 * Perilaku dropdown yang sama dengan kartu Library: mengetuk [trigger] membuka atau menutup
 * [details] dan memutar [chevron]. Hanya mengatur tampilan; isi dan aksi tiap layar tetap milik
 * layar itu. [onChanged] dipakai pemilik untuk mengingat kartu mana yang terbuka.
 */
internal class ExpandableCard(
    private val trigger: View,
    private val details: List<View>,
    private val chevron: View,
    private val transitionRoot: ViewGroup? = null,
    expanded: Boolean = false,
    private val onChanged: (Boolean) -> Unit = {}
) {
    var expanded: Boolean = expanded
        private set

    init {
        render()
        trigger.setOnClickListener { set(!this.expanded) }
    }

    fun set(open: Boolean, animate: Boolean = true) {
        if (open == expanded) return
        expanded = open
        if (animate) {
            val root = transitionRoot ?: (trigger.parent as? ViewGroup)
            if (root != null) TransitionManager.beginDelayedTransition(root, AutoTransition())
        }
        render()
        onChanged(open)
    }

    private fun render() {
        val visibility = if (expanded) View.VISIBLE else View.GONE
        for (view in details) view.visibility = visibility
        chevron.rotation = if (expanded) 180f else 0f
    }
}
