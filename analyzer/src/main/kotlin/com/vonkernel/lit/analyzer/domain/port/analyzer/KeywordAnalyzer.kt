package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.core.entity.Keyword

interface KeywordAnalyzer {
    suspend fun analyze(summary: String): List<Keyword>
}
