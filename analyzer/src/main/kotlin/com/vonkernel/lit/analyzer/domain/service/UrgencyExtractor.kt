package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.core.port.repository.UrgencyRepository
import org.springframework.stereotype.Service

@Service
class UrgencyExtractor(
    private val urgencyRepository: UrgencyRepository,
    private val urgencyAnalyzer: UrgencyAnalyzer
) : RetryableAnalysisService() {

    suspend fun process(articleId: String, title: String, content: String): Urgency =
        withRetry("urgencyAnalyzer", articleId) {
            urgencyRepository.findAll().let { urgencies ->
                urgencyAnalyzer.analyze(urgencies, title, content)
            }
        }
}