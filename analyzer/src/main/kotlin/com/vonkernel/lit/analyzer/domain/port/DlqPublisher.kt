package com.vonkernel.lit.analyzer.domain.port

interface DlqPublisher {
    suspend fun publish(originalMessage: String, retryCount: Int, articleId: String?)
}
