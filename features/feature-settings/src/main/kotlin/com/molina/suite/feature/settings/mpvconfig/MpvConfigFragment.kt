package com.molina.suite.feature.settings.mpvconfig

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.feature.settings.R
import com.molina.suite.feature.settings.Refreshable
import com.molina.suite.feature.settings.SettingsDependencies
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Konfigurasi mpv: membuka mpv.conf, input.conf, dan skrip Lua di tab Code lewat
 * [com.molina.suite.core.common.EditorOpenBoard]. Layar ini tidak mengenal kelas editor.
 */
class MpvConfigFragment : Fragment(R.layout.fragment_mpv_config), Refreshable {

    private var scriptList: LinearLayout? = null
    private var scriptsEmpty: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        scriptList = view.findViewById(R.id.mpvconfig_scripts)
        scriptsEmpty = view.findViewById(R.id.mpvconfig_scripts_empty)
        view.findViewById<View>(R.id.mpvconfig_row_conf).setOnClickListener {
            openConfig(MpvConfigFiles.MPV_CONF)
        }
        view.findViewById<View>(R.id.mpvconfig_row_input).setOnClickListener {
            openConfig(MpvConfigFiles.INPUT_CONF)
        }
        view.findViewById<MaterialButton>(R.id.mpvconfig_new_script).setOnClickListener { askNewScript() }
        refresh()
    }

    override fun refresh() {
        if (view == null) return
        val dir = context?.filesDir ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val scripts = withContext(Dispatchers.IO) { MpvConfigFiles.listScripts(dir) }
            showScripts(scripts)
        }
    }

    private fun showScripts(scripts: List<File>) {
        val list = scriptList ?: return
        list.removeAllViews()
        scriptsEmpty?.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(list.context)
        for (file in scripts) {
            val row = inflater.inflate(R.layout.item_mpv_script, list, false)
            row.findViewById<TextView>(R.id.mpv_script_name).text = file.name
            row.setOnClickListener { openInCode(file, null) }
            list.addView(row)
        }
    }

    private fun openConfig(name: String) {
        val dir = context?.filesDir ?: return
        openInCode(File(dir, name), null)
    }

    /** Memastikan berkas ada, meminta tab Code membukanya, lalu pindah ke tab Code. */
    private fun openInCode(file: File, initialText: String?) {
        val host = activity ?: return
        val board = SettingsDependencies.editorOpen()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val ready = withContext(Dispatchers.IO) { MpvConfigFiles.ensureFile(file, initialText) }) {
                is MpvConfigFiles.Ensure.Ready -> {
                    board.request(file.path)
                    (host as? HostTabSwitcher)?.showEngine(EngineId.CODE)
                    if (ready.created) refresh()
                }
                is MpvConfigFiles.Ensure.Failed ->
                    toast(getString(R.string.mpvconfig_open_failed, file.name, ready.reason))
            }
        }
    }

    private fun askNewScript() {
        val context = context ?: return
        val pad = resources.getDimensionPixelSize(R.dimen.ms_space_md)
        val input = EditText(context).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHint(R.string.mpvconfig_new_hint)
        }
        val holder = FrameLayout(context).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(
                input,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mpvconfig_new_title)
            .setView(holder)
            .setNegativeButton(R.string.mpvconfig_cancel, null)
            .setPositiveButton(R.string.mpvconfig_create, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                when (val check = MpvConfigFiles.checkScriptName(input.text.toString())) {
                    is MpvConfigFiles.NameCheck.Valid -> {
                        dialog.dismiss()
                        createScript(check.fileName)
                    }
                    MpvConfigFiles.NameCheck.Empty -> input.error = getString(R.string.mpvconfig_name_empty)
                    MpvConfigFiles.NameCheck.TooLong -> input.error = getString(R.string.mpvconfig_name_too_long)
                    MpvConfigFiles.NameCheck.Illegal -> input.error = getString(R.string.mpvconfig_name_illegal)
                }
            }
        }
        dialog.show()
    }

    private fun createScript(fileName: String) {
        val dir = context?.filesDir ?: return
        val file = File(MpvConfigFiles.scriptsDir(dir), fileName)
        openInCode(file, MpvConfigFiles.starterText(fileName))
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
    }

    override fun onDestroyView() {
        scriptList = null
        scriptsEmpty = null
        super.onDestroyView()
    }
}
