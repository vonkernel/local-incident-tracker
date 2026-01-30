package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.model.KeywordAnalysisResult
import com.vonkernel.lit.core.entity.Article

interface KeywordAnalyzer {
    suspend fun analyze(article: Article): KeywordAnalysisResult
}
