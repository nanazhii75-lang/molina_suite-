package com.molina.suite.feature.code

import java.io.File
import java.nio.charset.Charset

/** Berkas yang sedang disunting beserta keadaannya. */
internal data class CodeDocument(
    val file: File,
    val charset: Charset,
    val hasBom: Boolean,
    val dirty: Boolean
)
