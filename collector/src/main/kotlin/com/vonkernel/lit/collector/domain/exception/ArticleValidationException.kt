package com.vonkernel.lit.collector.domain.exception

class ArticleValidationException(
    val articleId: String,
    val errors: List<String>
) : RuntimeException("Article validation failed for $articleId: ${errors.joinToString("; ")}")
