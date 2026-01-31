package com.vonkernel.lit.indexer.domain.service

import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.domain.assembler.IndexDocumentAssembler
import com.vonkernel.lit.indexer.domain.exception.ArticleIndexingException
import com.vonkernel.lit.indexer.domain.port.Embedder
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticleIndexingService(
    private val embedder: Embedder,
    private val articleIndexer: ArticleIndexer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun index(analysisResult: AnalysisResult) {
        log.info("Starting indexing for article: {}", analysisResult.articleId)

        runCatching { embedder.embed(analysisResult.refinedArticle.content) }
            .onFailure { log.warn("Embedding failed for article {}, proceeding without: {}", analysisResult.articleId, it.message) }
            .getOrNull()
            .let { embedding -> IndexDocumentAssembler.assemble(analysisResult, embedding) }
            .also { document ->
                runCatching { articleIndexer.index(document) }
                    .onSuccess { log.info("Indexing completed for article: {}", analysisResult.articleId) }
                    .onFailure { e ->
                        log.error("Indexing failed for article {}: {}", analysisResult.articleId, e.message, e)
                        throw ArticleIndexingException(
                            articleId = analysisResult.articleId,
                            message = "Indexing failed for article ${analysisResult.articleId}",
                            cause = e
                        )
                    }
            }
    }

    suspend fun indexAll(analysisResults: List<AnalysisResult>) {
        if (analysisResults.isEmpty()) return
        log.info("Starting batch indexing for {} articles", analysisResults.size)

        analysisResults
            .map { it.refinedArticle.content }
            .let { contents ->
                runCatching { embedder.embedAll(contents) }
                    .onFailure { log.warn("Batch embedding failed, proceeding without embeddings: {}", it.message) }
                    .getOrElse { List(analysisResults.size) { null } }
            }
            .zip(analysisResults) { embedding, result -> IndexDocumentAssembler.assemble(result, embedding) }
            .also { documents ->
                articleIndexer.indexAll(documents)
                log.info("Batch indexing completed for {} articles", analysisResults.size)
            }
    }
}
