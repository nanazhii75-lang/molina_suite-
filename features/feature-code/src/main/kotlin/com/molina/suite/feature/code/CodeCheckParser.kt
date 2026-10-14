package com.molina.suite.feature.code

internal enum class CodeIssueSeverity { ERROR, WARNING }

/** Satu temuan pemeriksa; [line] dan [column] berbasis 1, null bila alatnya tidak menyebutkan. */
internal data class CodeCheckIssue(
    val line: Int?,
    val column: Int?,
    val severity: CodeIssueSeverity,
    val message: String
)

/** Hasil akhir pemeriksaan sebuah berkas. */
internal sealed interface CodeCheckOutcome {
    /** Lolos: tidak ada error; [warnings] boleh kosong. */
    data class Passed(val warnings: List<CodeCheckIssue>) : CodeCheckOutcome

    /** Gagal: [issues] selalu berisi minimal satu error; [rawOutput] adalah keluaran alat yang sudah disaring. */
    data class Failed(val issues: List<CodeCheckIssue>, val rawOutput: String) : CodeCheckOutcome

    /** Pemeriksaan dihentikan karena melewati batas waktu. */
    object TimedOut : CodeCheckOutcome
}

/**
 * Membaca keluaran pemeriksa (kompilator atau interpreter) menjadi daftar temuan berikut nomor
 * barisnya. Semua alat ditangani di satu tempat supaya dialog hasil tidak mengenal format
 * keluaran tiap bahasa. Keluaran yang tidak dikenali tidak dibuang: jadi satu temuan tanpa baris.
 */
internal object CodeCheckParser {

    const val TIMEOUT_EXIT_CODE = 124
    private const val MAX_FALLBACK_LINES = 20

    // Format gcc, javac, kotlinc, go, ruby, luac: "berkas:baris[:kolom]: [error|warning|note:] pesan".
    private val COMPILER_LINE =
        Regex("""^(.+?):(\d+):(?:(\d+):)?\s*(?:(fatal error|error|warning|note)\s*:\s*)?(.+)$""")
    private val BASH_LINE = Regex("""^(.+?): line (\d+): (.*)$""")
    private val TRACE_FILE = Regex("""^\s*File "(.+)", line (\d+)""")
    private val NODE_LOCATION = Regex("""^(.+?):(\d+)$""")
    private val TYPED_ERROR = Regex("""^[A-Za-z]+(?:Error|Exception): ?.*$""")
    private val RUST_HEAD = Regex("""^(error|warning)(?:\[\w+])?: (.*)$""")
    private val RUST_ARROW = Regex("""^\s*--> (.+?):(\d+):(\d+)""")
    private val PHP_LINE = Regex("""^(?:PHP )?(Parse error|Fatal error|Warning):\s+(.*) in (.+) on line (\d+)""")
    private val JQ_LINE = Regex("""^(?:jq: )?(?:error|parse error)\b.* at line (\d+)(?:, column (\d+))?""")

    private val NOISE_PREFIXES = listOf(
        "OpenJDK 64-Bit Server VM warning",
        "Picked up ",
        "NOTE: Picked up",
        "Node.js v",
        "# command-line-arguments"
    )

    /** Memutuskan hasil dari [exitCode] dan keluaran alat; panggil di thread mana pun. */
    fun interpret(exitCode: Int, stdout: String, stderr: String): CodeCheckOutcome {
        if (exitCode == TIMEOUT_EXIT_CODE) return CodeCheckOutcome.TimedOut
        val lines = cleanLines(listOf(stderr, stdout).filter { it.isNotBlank() }.joinToString("\n"))
        val issues = parse(lines)
        if (exitCode == 0) {
            return CodeCheckOutcome.Passed(issues.filter { it.severity == CodeIssueSeverity.WARNING })
        }
        val withError = if (issues.any { it.severity == CodeIssueSeverity.ERROR }) {
            issues
        } else {
            issues + CodeCheckIssue(
                null,
                null,
                CodeIssueSeverity.ERROR,
                fallbackMessage(lines, exitCode)
            )
        }
        return CodeCheckOutcome.Failed(withError, lines.joinToString("\n"))
    }

    private fun cleanLines(output: String): List<String> =
        output.lineSequence()
            .map { it.trimEnd() }
            .filterNot { line ->
                val trimmed = line.trimStart()
                NOISE_PREFIXES.any { trimmed.startsWith(it) } || trimmed.startsWith("at ")
            }
            .toList()
            .let { all -> all.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() } }

    private fun fallbackMessage(lines: List<String>, exitCode: Int): String {
        val text = lines.filter { it.isNotBlank() }.take(MAX_FALLBACK_LINES).joinToString("\n")
        return text.ifEmpty { "Pemeriksa berhenti dengan kode keluar $exitCode tanpa pesan." }
    }

    /** Membaca [lines] yang sudah disaring menjadi temuan; urutan mengikuti kemunculan, tanpa duplikat. */
    fun parse(lines: List<String>): List<CodeCheckIssue> {
        val issues = LinkedHashSet<CodeCheckIssue>()
        var pendingLine: Int? = null
        var pendingRust: Pair<CodeIssueSeverity, String>? = null

        fun flushRust() {
            pendingRust?.let { (severity, message) -> issues.add(CodeCheckIssue(null, null, severity, message)) }
            pendingRust = null
        }

        for (line in lines) {
            val trace = TRACE_FILE.find(line)
            if (trace != null) {
                pendingLine = trace.groupValues[2].toInt()
                continue
            }
            if (pendingLine != null && TYPED_ERROR.matches(line)) {
                issues.add(CodeCheckIssue(pendingLine, null, CodeIssueSeverity.ERROR, line.trim()))
                pendingLine = null
                continue
            }
            // Panah rustc harus dicek sebelum pola kompilator umum, yang salah membacanya.
            val arrow = RUST_ARROW.find(line)
            if (arrow != null) {
                pendingRust?.let { (severity, message) ->
                    issues.add(CodeCheckIssue(arrow.groupValues[2].toInt(), arrow.groupValues[3].toInt(), severity, message))
                }
                pendingRust = null
                continue
            }
            val compiler = COMPILER_LINE.find(line)
            if (compiler != null) {
                val kind = compiler.groupValues[4]
                if (kind != "note") {
                    issues.add(
                        CodeCheckIssue(
                            compiler.groupValues[2].toInt(),
                            compiler.groupValues[3].toIntOrNull(),
                            if (kind == "warning") CodeIssueSeverity.WARNING else CodeIssueSeverity.ERROR,
                            compiler.groupValues[5].trim()
                        )
                    )
                }
                continue
            }
            val bash = BASH_LINE.find(line)
            if (bash != null) {
                issues.add(CodeCheckIssue(bash.groupValues[2].toInt(), null, CodeIssueSeverity.ERROR, bash.groupValues[3].trim()))
                continue
            }
            val php = PHP_LINE.find(line)
            if (php != null) {
                val severity = if (php.groupValues[1] == "Warning") CodeIssueSeverity.WARNING else CodeIssueSeverity.ERROR
                issues.add(CodeCheckIssue(php.groupValues[4].toInt(), null, severity, php.groupValues[1] + ": " + php.groupValues[2].trim()))
                continue
            }
            val jq = JQ_LINE.find(line)
            if (jq != null) {
                issues.add(CodeCheckIssue(jq.groupValues[1].toInt(), jq.groupValues[2].toIntOrNull(), CodeIssueSeverity.ERROR, line.trim()))
                continue
            }
            val rust = RUST_HEAD.find(line)
            if (rust != null) {
                flushRust()
                val message = rust.groupValues[2].trim()
                if (!message.startsWith("aborting due to") && !message.startsWith("could not compile")) {
                    pendingRust = (if (rust.groupValues[1] == "warning") CodeIssueSeverity.WARNING else CodeIssueSeverity.ERROR) to message
                }
                continue
            }
            val node = NODE_LOCATION.find(line)
            if (node != null) pendingLine = node.groupValues[2].toInt()
        }
        flushRust()
        return issues.toList()
    }
}
