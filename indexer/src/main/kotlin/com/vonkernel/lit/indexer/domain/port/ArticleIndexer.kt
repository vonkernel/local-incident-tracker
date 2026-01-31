package com.vonkernel.lit.indexer.domain.port

import com.vonkernel.lit.core.entity.ArticleIndexDocument

interface ArticleIndexer {
    suspend fun index(document: ArticleIndexDocument)
    suspend fun delete(articleId: String)
    suspend fun indexAll(documents: List<ArticleIndexDocument>)
}
