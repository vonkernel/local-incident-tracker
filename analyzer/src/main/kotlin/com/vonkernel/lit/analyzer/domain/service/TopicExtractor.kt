package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.TopicAnalyzer
import com.vonkernel.lit.core.entity.Topic
import org.springframework.stereotype.Service

@Service
class TopicExtractor(
    private val topicAnalyzer: TopicAnalyzer,
) : RetryableAnalysisService() {

    suspend fun process(articleId: String, summary: String): Topic =
        withRetry("extract-topic", articleId) {
            topicAnalyzer.analyze(summary)
        }
}