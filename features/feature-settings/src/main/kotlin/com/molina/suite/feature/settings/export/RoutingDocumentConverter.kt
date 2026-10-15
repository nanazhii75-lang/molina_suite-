package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.ConversionResult
import com.molina.suite.core.common.DocumentConverter
import com.molina.suite.core.common.DocumentFormat

/**
 * Meneruskan konversi ke pelaksana yang tepat: sumber PDF ke [pdf], sumber lain ke [general].
 * Pemakai tetap hanya mengenal satu [DocumentConverter].
 */
internal class RoutingDocumentConverter(
    private val pdf: DocumentConverter,
    private val general: DocumentConverter
) : DocumentConverter {

    override fun targetsFor(extension: String): List<DocumentFormat> =
        pdf.targetsFor(extension).ifEmpty { general.targetsFor(extension) }

    override suspend fun convert(request: ConversionRequest): ConversionResult =
        if (request.source == DocumentFormat.PDF) pdf.convert(request) else general.convert(request)
}
