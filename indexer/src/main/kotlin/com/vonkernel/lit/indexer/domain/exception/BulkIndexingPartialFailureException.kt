package com.vonkernel.lit.indexer.domain.exception

class BulkIndexingPartialFailureException(
    val failedArticleIds: List<String>,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
