package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.core.util.executeWithRetry
import org.slf4j.LoggerFactory

abstract class RetryableAnalysisService {

    private val log = LoggerFactory.getLogger(javaClass)

    protected suspend fun <T> withRetry(operationName: String, articleId: String, block: suspend () -> T): T =
        executeWithRetry(maxRetries = 2, onRetry = { attempt, delay, e ->
            log.warn("Retrying {} for article {} (attempt {}, delay {}ms): {}",
                operationName, articleId, attempt, delay, e.message)
        }, block = block)
}