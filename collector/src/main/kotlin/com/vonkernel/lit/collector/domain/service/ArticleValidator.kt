package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.core.entity.Article


fun Article.validate(): Result<Article> {
    return when {
        this.title.isBlank() -> Result.failure(
            IllegalArgumentException("Article title cannot be blank")
        )
        this.content.isBlank() -> Result.failure(
            IllegalArgumentException("Article content cannot be blank")
        )
        this.originId.isBlank() -> Result.failure(
            IllegalArgumentException("Article originId cannot be blank")
        )
        this.sourceId.isBlank() -> Result.failure(
            IllegalArgumentException("Article sourceId cannot be blank")
        )
        else -> Result.success(this)
    }
}