package com.vonkernel.lit.analyzer.domain.exception

class ArticleAnalysisException(
    val articleId: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
