package com.vonkernel.lit.indexer.domain.port

interface DlqPublisher {
    suspend fun publish(originalMessage: String, retryCount: Int, articleId: String?)
}
