package com.vonkernel.lit.indexer.domain.port

import com.vonkernel.lit.core.entity.ArticleIndexDocument
import java.time.Instant

interface ArticleIndexer {
    suspend fun index(document: ArticleIndexDocument)
    suspend fun delete(articleId: String)
    suspend fun indexAll(documents: List<ArticleIndexDocument>)
    suspend fun findModifiedAtByArticleId(articleId: String): Instant?
}
