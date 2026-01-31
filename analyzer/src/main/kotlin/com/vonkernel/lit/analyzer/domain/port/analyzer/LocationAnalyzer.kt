package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation

interface LocationAnalyzer {
    suspend fun analyze(title: String, content: String): List<ExtractedLocation>
}
