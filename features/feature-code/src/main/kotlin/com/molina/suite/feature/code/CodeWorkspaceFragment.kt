package com.molina.suite.feature.code

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.molina.suite.core.common.HostChromeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URLEncoder

/**
 * Ruang kerja code-server: WebView ke server lokal dengan toolbar tombol di
 * bawah. Aktif hanya saat tab Code dan ruang kerja sama-sama tampil; saat aktif
 * ia mengatur mode keyboard jendela dan meminta shell masuk mode ringkas ketika
 * keyboard layar terbuka.
 */
class CodeWorkspaceFragment : Fragment() {

    private var webView: WebView? = null
    private var errorText: TextView? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var active = false
    private var keyboardVisible = false
    private var savedSoftInputMode: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = inflater.context
        val web = CodeWebViewFactory.create(context) { reason -> showError(reason) }
        webView = web

        val error = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(dp(24), dp(24), dp(24), dp(24))
            visibility = View.GONE
        }
        errorText = error

        val content = FrameLayout(context).apply {
            addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(error, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        val toolbar = CodeKeyToolbar(CodeKeySender(web)) { toggleKeyboard() }.build(context)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(MATCH, MATCH)
            addView(buildStrip(context), LinearLayout.LayoutParams(MATCH, dp(STRIP_HEIGHT_DP)))
            addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(toolbar, LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listener = ViewTreeObserver.OnGlobalLayoutListener { onLayoutChanged() }
        layoutListener = listener
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        loadEditor()
    }

    override fun onResume() {
        super.onResume()
        applyActive()
    }

    override fun onDestroyView() {
        val observer = view?.viewTreeObserver
        val listener = layoutListener
        if (observer != null && listener != null && observer.isAlive) {
            observer.removeOnGlobalLayoutListener(listener)
        }
        layoutListener = null
        leaveActive()

        val web = webView
        webView = null
        errorText = null
        if (web != null) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        super.onDestroyView()
    }

    /** Dipanggil host: true bila tab Code dan ruang kerja sama-sama sedang tampil. */
    internal fun setActive(value: Boolean) {
        active = value
        applyActive()
    }

    private fun applyActive() {
        if (active && webView != null) enterActive() else leaveActive()
    }

    private fun enterActive() {
        val window = activity?.window ?: return
        if (savedSoftInputMode == null) savedSoftInputMode = window.attributes.softInputMode
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onLayoutChanged()
    }

    private fun leaveActive() {
        val host = activity
        savedSoftInputMode?.let { host?.window?.setSoftInputMode(it) }
        savedSoftInputMode = null
        if (keyboardVisible) hideKeyboard()
        keyboardVisible = false
        (host as? HostChromeController)?.setChromeCompact(false)
    }

    private fun onLayoutChanged() {
        if (!active) return
        val root = view ?: return
        val frame = Rect()
        root.getWindowVisibleDisplayFrame(frame)
        val screenHeight = root.rootView.height
        val visible = screenHeight - frame.bottom > screenHeight * KEYBOARD_THRESHOLD
        if (visible != keyboardVisible) {
            keyboardVisible = visible
            (activity as? HostChromeController)?.setChromeCompact(visible)
        }
    }

    private fun toggleKeyboard() {
        val web = webView ?: return
        val imm = web.context.getSystemService(InputMethodManager::class.java) ?: return
        if (keyboardVisible) {
            imm.hideSoftInputFromWindow(web.windowToken, 0)
        } else {
            web.requestFocus()
            imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val web = webView ?: return
        web.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(web.windowToken, 0)
    }

    private fun loadEditor() {
        errorText?.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val password = withContext(Dispatchers.IO) { readPassword() }
            val web = webView ?: return@launch
            if (password == null) {
                showError(getString(R.string.code_workspace_no_password), raw = true)
                return@launch
            }
            val body = "password=" + URLEncoder.encode(password, "UTF-8")
            web.postUrl(CodeServerEndpoint.BASE_URL + "login", body.toByteArray(Charsets.UTF_8))
        }
    }

    private fun readPassword(): String? =
        try {
            CodeEngineModule.requireEngine().layout.passwordFile.readText().trim().ifEmpty { null }
        } catch (e: IOException) {
            null
        }

    private fun showError(reason: String, raw: Boolean = false) {
        val view = errorText ?: return
        view.text = if (raw) reason else getString(R.string.code_workspace_error, reason)
        view.visibility = View.VISIBLE
    }

    private fun requestControl() {
        (parentFragment as? CodeTabFragment)?.showControl()
    }

    private fun buildStrip(context: Context): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            stripButton(context, R.string.code_workspace_control) { requestControl() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MATCH)
        )
        addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        addView(
            stripButton(context, R.string.code_workspace_reload) { loadEditor() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MATCH)
        )
    }

    private fun stripButton(context: Context, labelRes: Int, action: () -> Unit): Button =
        Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            setText(labelRes)
            setAllCaps(false)
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val STRIP_HEIGHT_DP = 44
        const val KEYBOARD_THRESHOLD = 0.15f
    }
}
