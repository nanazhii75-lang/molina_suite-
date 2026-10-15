package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat

/**
 * Meneruskan konversi ke pelaksana yang tepat: sumber yang punya pelaksana khusus di
 * [specialized] (mis. PDF, XLSX) ke pelaksananya, sumber lain ke [general]. Pemakai tetap hanya
 * mengenal satu [DocumentConverter].
 */
internal class RoutingDocumentConverter(
    private val specialized: Map<DocumentFormat, DocumentConverter>,
    private val general: DocumentConverter
) : DocumentConverter {

    override fun targetsFor(extension: String): List<DocumentFormat> =
        converterFor(DocumentFormat.fromExtension(extension)).targetsFor(extension)

    override suspend fun convert(request: ConversionRequest): ConversionResult =
        converterFor(request.source).convert(request)

    private fun converterFor(source: DocumentFormat?): DocumentConverter =
        if (source == null) general else specialized[source] ?: general
}
