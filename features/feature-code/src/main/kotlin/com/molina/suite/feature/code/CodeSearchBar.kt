package com.molina.suite.feature.code

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Panel Cari dan Ganti di atas toolbar. Tidak mengenal editor; semua aksi
 * diteruskan lewat [Callbacks] bersama opsi yang sedang dipilih.
 */
internal class CodeSearchBar(private val context: Context, private val callbacks: Callbacks) {

    interface Callbacks {
        fun onRequestChanged(request: CodeSearchRequest)
        fun onFind()
        fun onReplace(replacement: String)
        fun onReplaceAll(replacement: String)
        fun onClosed()
    }

    private fun field(hint: Int): EditText = EditText(context).apply {
        setHint(hint)
        setHintTextColor(CodePalette.TEXT_MUTED)
        setTextColor(CodePalette.TEXT)
        textSize = 15f
        backgroundTintList = ColorStateList.valueOf(CodePalette.ACCENT)
        inputType = InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
    }

    private fun check(label: Int, checked: Boolean): CheckBox = CheckBox(context).apply {
        setText(label)
        isChecked = checked
        textSize = 12f
        setTextColor(CodePalette.TEXT)
        buttonTintList = ColorStateList.valueOf(CodePalette.ACCENT)
        isFocusableInTouchMode = false
        setOnCheckedChangeListener { _, _ -> callbacks.onRequestChanged(request()) }
    }

    private fun action(label: Int, onClick: () -> Unit): Button =
        Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            setText(label)
            setTextColor(CodePalette.ACCENT)
            setTypeface(typeface, Typeface.BOLD)
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { onClick() }
        }

    private val findInput = field(R.string.code_search_find_hint).apply {
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                callbacks.onFind()
                true
            } else {
                false
            }
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                callbacks.onRequestChanged(request())
            }
        })
    }

    private val replaceInput = field(R.string.code_search_replace_hint).apply {
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private val caseBox = check(R.string.code_search_case, false)
    private val regexBox = check(R.string.code_search_regex, false)
    private val wrapBox = check(R.string.code_search_wrap, true)

    private val counter = TextView(context).apply {
        setTextColor(CodePalette.TEXT_MUTED)
        textSize = 12f
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(CodePalette.PANEL)
        setPadding(CodeUi.dp(context, 16), CodeUi.dp(context, 8), CodeUi.dp(context, 8), CodeUi.dp(context, 4))
        visibility = View.GONE

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(context).apply {
                        setText(R.string.code_search_title)
                        setTextColor(CodePalette.ACCENT)
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                addView(counter)
                addView(
                    CodeUi.iconButton(context, R.drawable.ic_code_close, R.string.code_search_close) { hide() },
                    LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 40))
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        addView(findInput, matchWidth())
        addView(replaceInput, matchWidth())
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                for (box in listOf(caseBox, regexBox, wrapBox)) {
                    addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            },
            matchWidth()
        )
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                addView(action(R.string.code_search_replace_all) {
                    callbacks.onReplaceAll(replaceInput.text.toString())
                })
                addView(action(R.string.code_search_replace) {
                    callbacks.onReplace(replaceInput.text.toString())
                })
                addView(action(R.string.code_search_find) { callbacks.onFind() })
            },
            matchWidth()
        )
    }

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    private fun matchWidth() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun request() = CodeSearchRequest(
        query = findInput.text.toString(),
        caseSensitive = caseBox.isChecked,
        regex = regexBox.isChecked,
        wrapAround = wrapBox.isChecked
    )

    fun show() {
        view.visibility = View.VISIBLE
        findInput.requestFocus()
        findInput.selectAll()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
        if (findInput.text.isNotEmpty()) callbacks.onRequestChanged(request())
    }

    /** Sembunyikan panel tanpa memanggil [Callbacks.onClosed]; dipakai saat dokumen berganti. */
    fun dismissSilently() {
        view.visibility = View.GONE
    }

    fun hide() {
        if (!isShowing) return
        view.visibility = View.GONE
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(findInput.windowToken, 0)
        callbacks.onClosed()
    }

    fun render(state: CodeSearchState) {
        counter.text = when {
            state.invalidPattern -> context.getString(R.string.code_search_invalid)
            state.query.isEmpty() -> ""
            state.total == 0 -> "0"
            else -> "${state.current}/${state.total}"
        }
    }
}
