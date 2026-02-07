package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.ArticleValidationException
import com.vonkernel.lit.core.entity.Article

fun Article.validate(): Result<Article> =
    collectErrors()
        .takeIf { it.isNotEmpty() }
        ?.let { Result.failure(ArticleValidationException(articleId, it)) }
        ?: Result.success(this)

private fun Article.collectErrors(): List<String> = listOfNotNull(
    "title cannot be blank".takeIf { title.isBlank() },
    "content cannot be blank".takeIf { content.isBlank() },
    "originId cannot be blank".takeIf { originId.isBlank() },
    "sourceId cannot be blank".takeIf { sourceId.isBlank() },
)
