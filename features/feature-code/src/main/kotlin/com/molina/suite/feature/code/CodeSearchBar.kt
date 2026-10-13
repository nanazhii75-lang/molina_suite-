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
import android.widget.HorizontalScrollView
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
        minHeight = 0
        minimumHeight = 0
        setPadding(paddingLeft, CodeUi.dp(context, 6), paddingRight, CodeUi.dp(context, 6))
    }

    private fun check(label: Int, checked: Boolean): CheckBox = CheckBox(context).apply {
        setText(label)
        isChecked = checked
        textSize = 12f
        setTextColor(CodePalette.TEXT)
        buttonTintList = ColorStateList.valueOf(CodePalette.ACCENT)
        isFocusableInTouchMode = false
        minHeight = 0
        minimumHeight = 0
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

    private fun compactAction(label: Int, onClick: () -> Unit): Button = action(label, onClick).apply {
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        textSize = 13f
        val pad = CodeUi.dp(context, 10)
        setPadding(pad, 0, pad, 0)
    }

    private val findInput: EditText = field(R.string.code_search_find_hint).apply {
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

    private val replaceInput: EditText = field(R.string.code_search_replace_hint).apply {
        imeOptions = EditorInfo.IME_ACTION_DONE
    }

    private val blockBox: CheckBox = check(R.string.code_search_block, false)
    private val caseBox: CheckBox = check(R.string.code_search_case, true)
    private val regexBox: CheckBox = check(R.string.code_search_regex, false)
    private val wrapBox: CheckBox = check(R.string.code_search_wrap, true)

    private val counter: TextView = TextView(context).apply {
        setTextColor(CodePalette.ACCENT)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    /** Baris Ganti; disembunyikan bawaan supaya panel Cari tidak menutupi ruang kerja. */
    private val replaceRow: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(replaceInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(compactAction(R.string.code_search_replace) {
            callbacks.onReplace(replaceInput.text.toString())
        })
        addView(compactAction(R.string.code_search_replace_all) {
            callbacks.onReplaceAll(replaceInput.text.toString())
        })
    }

    private val replaceToggle: CheckBox = check(R.string.code_search_replace_toggle, false).apply {
        setOnCheckedChangeListener { _, on ->
            replaceRow.visibility = if (on) View.VISIBLE else View.GONE
        }
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(CodePalette.PANEL)
        setPadding(CodeUi.dp(context, 12), CodeUi.dp(context, 4), CodeUi.dp(context, 4), CodeUi.dp(context, 2))
        visibility = View.GONE

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(findInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(
                    counter,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = CodeUi.dp(context, 8) }
                )
                addView(compactAction(R.string.code_search_find) { callbacks.onFind() })
                addView(
                    CodeUi.iconButton(context, R.drawable.ic_code_close, R.string.code_search_close) { hide() },
                    LinearLayout.LayoutParams(CodeUi.dp(context, 40), CodeUi.dp(context, 40))
                )
            },
            matchWidth()
        )
        addView(replaceRow, matchWidth())
        addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        for (box in listOf(caseBox, blockBox, regexBox, wrapBox, replaceToggle)) {
                            addView(
                                box,
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply { marginEnd = CodeUi.dp(context, 12) }
                            )
                        }
                    }
                )
            },
            matchWidth()
        )
    }

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun request(): CodeSearchRequest = CodeSearchRequest(
        query = findInput.text.toString(),
        block = blockBox.isChecked,
        regex = regexBox.isChecked,
        wrapAround = wrapBox.isChecked,
        caseSensitive = caseBox.isChecked
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
            state.total == 0 -> context.getString(R.string.code_search_count_none)
            state.current == 0 -> context.getString(R.string.code_search_count_total, state.total)
            else -> context.getString(R.string.code_search_count_position, state.current, state.total)
        }
    }
}
