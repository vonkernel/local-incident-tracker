package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.core.entity.Article

interface LocationAnalyzer {
    suspend fun analyze(article: Article): List<ExtractedLocation>
}
