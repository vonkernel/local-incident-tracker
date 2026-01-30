package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation

interface LocationAnalyzer {
    suspend fun analyze(title: String, content: String): List<ExtractedLocation>
}
