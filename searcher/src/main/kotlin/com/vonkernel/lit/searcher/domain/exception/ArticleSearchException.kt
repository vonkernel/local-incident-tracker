package com.vonkernel.lit.searcher.domain.exception

class ArticleSearchException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
