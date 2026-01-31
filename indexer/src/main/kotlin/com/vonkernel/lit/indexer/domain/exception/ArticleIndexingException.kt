package com.vonkernel.lit.indexer.domain.exception

class ArticleIndexingException(
    val articleId: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
