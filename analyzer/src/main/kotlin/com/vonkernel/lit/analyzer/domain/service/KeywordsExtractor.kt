package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.KeywordAnalyzer
import com.vonkernel.lit.core.entity.Keyword
import org.springframework.stereotype.Service

@Service
class KeywordsExtractor(
    private val keywordAnalyzer:KeywordAnalyzer,
) : RetryableAnalysisService() {

    suspend fun process(articleId: String, summary: String): List<Keyword> =
        withRetry("extract-keywords", articleId) {
            keywordAnalyzer.analyze(summary)
        }
}
