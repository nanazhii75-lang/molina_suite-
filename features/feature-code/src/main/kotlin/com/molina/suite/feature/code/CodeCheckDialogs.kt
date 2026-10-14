package com.molina.suite.feature.code

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.molina.suite.core.common.ToolSetupTarget

/** Isi dialog Lakukan: yang ditampilkan di dropdown Kompilator dan apakah LAKUKAN boleh ditekan. */
internal data class CodeCheckSetup(
    val compilerLabel: String,
    val note: String?,
    val canRun: Boolean,
    val setupTarget: ToolSetupTarget,
    val setupToolIds: List<String>
)

/** Dialog fitur Lakukan: pengaturan, proses, dan hasil. Hanya tampilan; alurnya ada di [CodeCheckController]. */
internal object CodeCheckDialogs {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val errorColor = 0xFFE57373.toInt()
    private val warningColor = 0xFFFFB74D.toInt()

    fun showSetup(context: Context, setup: CodeCheckSetup, onRun: () -> Unit, onOpenSetup: () -> Unit) {
        val pad = CodeUi.dp(context, 20)
        val small = CodeUi.dp(context, 8)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, small, pad, 0)
        }
        root.addView(
            TextView(context).apply { setText(R.string.code_check_compiler) },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        val compilerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf(setup.compilerLabel))
        compilerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val spinner = Spinner(context)
        spinner.adapter = compilerAdapter
        val book = TextView(context).apply {
            setText(R.string.code_check_open_packages)
            textSize = BOOK_TEXT_SP
            gravity = Gravity.CENTER
            setPadding(CodeUi.dp(context, 12), 0, CodeUi.dp(context, 4), 0)
            contentDescription = context.getString(R.string.code_check_open_packages_description)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(spinner, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(book, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        root.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        setup.note?.let { noteText ->
            val noteView = TextView(context)
            noteView.text = noteText
            noteView.setPadding(0, small, 0, 0)
            root.addView(noteView, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.code_check_title)
            .setView(root)
            .setPositiveButton(R.string.code_check_run) { _, _ -> onRun() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        book.setOnClickListener {
            dialog.dismiss()
            onOpenSetup()
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = setup.canRun
    }

    /** Dialog proses yang tidak bisa ditutup selain lewat Batal; pemanggil menutupnya sendiri saat selesai. */
    fun showRunning(context: Context, message: String, onCancel: () -> Unit): AlertDialog {
        val pad = CodeUi.dp(context, 20)
        val size = CodeUi.dp(context, 40)
        val messageView = TextView(context)
        messageView.text = message
        messageView.setPadding(pad / 2, 0, 0, 0)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad / 2)
            addView(ProgressBar(context), LinearLayout.LayoutParams(size, size))
            addView(messageView, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.code_check_title)
            .setView(row)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .create()
        dialog.show()
        return dialog
    }

    fun showMessage(context: Context, @StringRes title: Int, message: String) {
        val body = TextView(context)
        body.text = message
        body.setTextIsSelectable(true)
        val pad = CodeUi.dp(context, 20)
        body.setPadding(pad, CodeUi.dp(context, 8), pad, 0)
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.code_check_ok, null)
            .show()
    }

    /** Hasil pemeriksaan; mengetuk temuan yang punya nomor baris memanggil [onGoToLine] lalu menutup dialog. */
    fun showResult(
        context: Context,
        fileName: String,
        language: CodeCheckLanguage,
        outcome: CodeCheckOutcome,
        onGoToLine: (Int) -> Unit
    ) {
        when (outcome) {
            is CodeCheckOutcome.TimedOut -> showMessage(
                context,
                R.string.code_check_timeout_title,
                context.getString(R.string.code_check_timeout_message, fileName, language.timeoutSeconds)
            )
            is CodeCheckOutcome.Passed -> showIssues(
                context,
                R.string.code_check_passed_title,
                context.getString(R.string.code_check_passed_message, fileName),
                outcome.warnings,
                null,
                onGoToLine
            )
            is CodeCheckOutcome.Failed -> showIssues(
                context,
                R.string.code_check_failed_title,
                null,
                outcome.issues,
                outcome.rawOutput,
                onGoToLine
            )
        }
    }

    private fun showIssues(
        context: Context,
        @StringRes title: Int,
        intro: String?,
        issues: List<CodeCheckIssue>,
        rawOutput: String?,
        onGoToLine: (Int) -> Unit
    ) {
        var dialogRef: AlertDialog? = null
        val onIssue: (CodeCheckIssue) -> Unit = { issue ->
            val line = issue.line
            if (line != null) {
                dialogRef?.dismiss()
                onGoToLine(line)
            }
        }
        val pad = CodeUi.dp(context, 20)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, CodeUi.dp(context, 8), pad, 0)
        }
        if (intro != null) content.addView(plainText(context, intro, false), LinearLayout.LayoutParams(MATCH, WRAP))
        val errors = issues.filter { it.severity == CodeIssueSeverity.ERROR }
        val warnings = issues.filter { it.severity == CodeIssueSeverity.WARNING }
        addSection(context, content, R.string.code_check_errors, errors, errorColor, onIssue)
        addSection(context, content, R.string.code_check_warnings, warnings, warningColor, onIssue)
        if (!rawOutput.isNullOrBlank()) {
            content.addView(sectionHeader(context, context.getString(R.string.code_check_raw)), LinearLayout.LayoutParams(MATCH, WRAP))
            content.addView(plainText(context, rawOutput, true), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.code_check_ok, null)
        if (!rawOutput.isNullOrBlank()) builder.setNeutralButton(R.string.code_check_copy, null)
        val dialog = builder.create()
        dialogRef = dialog
        dialog.show()
        if (!rawOutput.isNullOrBlank()) {
            // Dipasang setelah show() supaya Salin tidak menutup dialog.
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { copyToClipboard(context, rawOutput) }
        }
    }

    private fun addSection(
        context: Context,
        container: LinearLayout,
        @StringRes header: Int,
        issues: List<CodeCheckIssue>,
        color: Int,
        onIssue: (CodeCheckIssue) -> Unit
    ) {
        if (issues.isEmpty()) return
        container.addView(
            sectionHeader(context, context.getString(header, issues.size)),
            LinearLayout.LayoutParams(MATCH, WRAP)
        )
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        for (issue in issues) {
            val row = TextView(context)
            row.text = issueText(context, issue)
            row.setTextColor(color)
            row.setPadding(0, CodeUi.dp(context, 6), 0, CodeUi.dp(context, 6))
            if (issue.line != null) {
                row.setBackgroundResource(ripple.resourceId)
                row.setOnClickListener { onIssue(issue) }
            }
            container.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
    }

    private fun issueText(context: Context, issue: CodeCheckIssue): String {
        val line = issue.line
        val column = issue.column
        val where = when {
            line == null -> ""
            column == null -> context.getString(R.string.code_check_at_line, line)
            else -> context.getString(R.string.code_check_at_line_column, line, column)
        }
        return if (where.isEmpty()) issue.message else "$where  ${issue.message}"
    }

    private fun sectionHeader(context: Context, text: String): TextView {
        val view = TextView(context)
        view.text = text
        view.setTypeface(view.typeface, Typeface.BOLD)
        view.setPadding(0, CodeUi.dp(context, 12), 0, CodeUi.dp(context, 4))
        return view
    }

    private fun plainText(context: Context, text: String, monospace: Boolean): TextView {
        val view = TextView(context)
        view.text = text
        view.setTextIsSelectable(true)
        if (monospace) {
            view.typeface = Typeface.MONOSPACE
            view.textSize = RAW_TEXT_SP
        }
        return view
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.code_check_title), text))
        Toast.makeText(context, R.string.code_check_copied, Toast.LENGTH_SHORT).show()
    }

    private const val BOOK_TEXT_SP = 22f
    private const val RAW_TEXT_SP = 12f
}
