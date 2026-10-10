package com.molina.suite.feature.code

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.molina.suite.core.common.HostBackPressHandler
import kotlinx.coroutines.launch

/**
 * Host tab Code: berganti antara layar kontrol ([CodeControlFragment]) dan
 * ruang kerja editor ([CodeWorkspaceFragment]). Ruang kerja dipertahankan
 * (hide/show) agar sesi editor tidak hilang, dan ditutup bila server berhenti.
 */
class CodeTabFragment : Fragment(), HostBackPressHandler {

    private var showingWorkspace = false
    private var lastServerState: CodeServerState? = null

    private val engine: CodeEngine
        get() = CodeEngineModule.requireEngine()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(inflater.context).apply {
        id = R.id.code_container
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val fm = childFragmentManager
        if (fm.findFragmentByTag(TAG_CONTROL) == null) {
            fm.beginTransaction()
                .add(R.id.code_container, CodeControlFragment(), TAG_CONTROL)
                .commitNow()
        }
        showingWorkspace = fm.findFragmentByTag(TAG_WORKSPACE)?.isHidden == false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                engine.controller.state.collect { onServerState(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        syncWorkspaceActive()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Shell memakai add/hide, jadi lifecycle tidak berubah saat tab disembunyikan.
        syncWorkspaceActive()
        if (!hidden) engine.installer.refresh()
    }

    override fun onHostBackPressed(): Boolean {
        if (!showingWorkspace || isHidden) return false
        showControl()
        return true
    }

    internal fun openWorkspace() {
        if (showingWorkspace) return
        val fm = childFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        fm.findFragmentByTag(TAG_CONTROL)?.let { tx.hide(it) }
        val workspace = fm.findFragmentByTag(TAG_WORKSPACE)
        if (workspace == null) {
            tx.add(R.id.code_container, CodeWorkspaceFragment(), TAG_WORKSPACE)
        } else {
            tx.show(workspace)
        }
        tx.commitNow()
        showingWorkspace = true
        syncWorkspaceActive()
    }

    internal fun showControl() {
        if (!showingWorkspace) return
        val fm = childFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        fm.findFragmentByTag(TAG_WORKSPACE)?.let { tx.hide(it) }
        fm.findFragmentByTag(TAG_CONTROL)?.let { tx.show(it) }
        tx.commitNow()
        showingWorkspace = false
        syncWorkspaceActive()
    }

    private fun closeWorkspace() {
        val fm = childFragmentManager
        val workspace = fm.findFragmentByTag(TAG_WORKSPACE)
        if (workspace == null && !showingWorkspace) return
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        workspace?.let { tx.remove(it) }
        fm.findFragmentByTag(TAG_CONTROL)?.let { tx.show(it) }
        tx.commitNow()
        showingWorkspace = false
    }

    private fun onServerState(state: CodeServerState) {
        val previous = lastServerState
        lastServerState = state
        if (state == CodeServerState.Running) {
            if (previous == CodeServerState.Starting && !isHidden) openWorkspace()
        } else {
            closeWorkspace()
        }
    }

    private fun syncWorkspaceActive() {
        val workspace = childFragmentManager.findFragmentByTag(TAG_WORKSPACE) as? CodeWorkspaceFragment
        workspace?.setActive(showingWorkspace && !isHidden)
    }

    private companion object {
        const val TAG_CONTROL = "code_control"
        const val TAG_WORKSPACE = "code_workspace"
    }
}
