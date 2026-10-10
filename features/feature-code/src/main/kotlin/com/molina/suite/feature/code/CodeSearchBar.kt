package com.molina.suite.feature.code

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Bar pencarian di atas toolbar. Tidak mengenal editor; semua aksi lewat [Callbacks]. */
internal class CodeSearchBar(context: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onQueryChanged(query: String)
        fun onNext()
        fun onPrevious()
        fun onClosed()
    }

    private val input = EditText(context).apply {
        setHint(R.string.code_search_hint)
        setHintTextColor(CodePalette.TEXT_MUTED)
        setTextColor(CodePalette.TEXT)
        backgroundTintList = ColorStateList.valueOf(CodePalette.ACCENT)
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        maxLines = 1
        setSingleLine(true)
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                callbacks.onNext()
                true
            } else {
                false
            }
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                callbacks.onQueryChanged(s?.toString().orEmpty())
            }
        })
    }

    private val counter = TextView(context).apply {
        setTextColor(CodePalette.TEXT_MUTED)
        textSize = 12f
        gravity = Gravity.CENTER
        minWidth = CodeUi.dp(context, 44)
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(CodePalette.PANEL)
        setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 4), 0)
        visibility = View.GONE
        addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(counter)
        addView(CodeUi.iconButton(context, R.drawable.ic_code_arrow_up, R.string.code_search_previous) {
            callbacks.onPrevious()
        }, LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 44)))
        addView(CodeUi.iconButton(context, R.drawable.ic_code_arrow_down, R.string.code_search_next) {
            callbacks.onNext()
        }, LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 44)))
        addView(CodeUi.iconButton(context, R.drawable.ic_code_close, R.string.code_search_close) {
            hide()
        }, LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 44)))
    }

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    fun show() {
        view.visibility = View.VISIBLE
        input.requestFocus()
        input.selectAll()
        val imm = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        if (input.text.isNotEmpty()) callbacks.onQueryChanged(input.text.toString())
    }

    /** Sembunyikan bar tanpa memanggil [Callbacks.onClosed]; dipakai saat dokumen berganti. */
    fun dismissSilently() {
        view.visibility = View.GONE
    }

    fun hide() {
        if (!isShowing) return
        view.visibility = View.GONE
        val imm = input.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(input.windowToken, 0)
        callbacks.onClosed()
    }

    fun render(state: CodeSearchState) {
        counter.text = when {
            state.query.isEmpty() -> ""
            state.total == 0 -> "0"
            else -> "${state.current}/${state.total}"
        }
    }
}
