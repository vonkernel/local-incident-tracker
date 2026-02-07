package com.vonkernel.lit.indexer.domain.service

import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.indexer.domain.assembler.IndexDocumentAssembler
import com.vonkernel.lit.indexer.domain.exception.ArticleIndexingException
import com.vonkernel.lit.indexer.domain.port.Embedder
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ArticleIndexingService(
    private val embedder: Embedder,
    private val articleIndexer: ArticleIndexer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun index(analysisResult: AnalysisResult, analyzedAt: Instant? = null) {
        log.info("Starting indexing for article: {}", analysisResult.articleId)

        if (isStale(analysisResult.articleId, analyzedAt)) return

        runCatching { embedder.embed(analysisResult.refinedArticle.content) }
            .onFailure { log.warn("Embedding failed for article {}, proceeding without: {}", analysisResult.articleId, it.message) }
            .getOrNull()
            .let { embedding -> IndexDocumentAssembler.assemble(analysisResult, embedding, analyzedAt) }
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

    suspend fun indexAll(analysisResults: List<AnalysisResult>, analyzedAts: List<Instant?> = emptyList()) {
        if (analysisResults.isEmpty()) return
        log.info("Starting batch indexing for {} articles", analysisResults.size)

        val paddedAnalyzedAts = analyzedAts + List(analysisResults.size - analyzedAts.size) { null }

        val freshPairs = analysisResults.zip(paddedAnalyzedAts)
            .filter { (result, analyzedAt) -> !isStale(result.articleId, analyzedAt) }

        if (freshPairs.isEmpty()) {
            log.info("All {} articles skipped as stale", analysisResults.size)
            return
        }

        val freshResults = freshPairs.map { it.first }
        val freshAnalyzedAts = freshPairs.map { it.second }

        freshResults
            .map { it.refinedArticle.content }
            .let { contents ->
                runCatching { embedder.embedAll(contents) }
                    .onFailure { log.warn("Batch embedding failed, proceeding without embeddings: {}", it.message) }
                    .getOrElse { List(freshResults.size) { null } }
            }
            .zip(freshResults.zip(freshAnalyzedAts)) { embedding, (result, analyzedAt) ->
                IndexDocumentAssembler.assemble(result, embedding, analyzedAt)
            }
            .also { documents ->
                articleIndexer.indexAll(documents)
                log.info("Batch indexing completed for {} articles ({} skipped as stale)",
                    freshResults.size, analysisResults.size - freshResults.size)
            }
    }

    private suspend fun isStale(articleId: String, analyzedAt: Instant?): Boolean {
        if (analyzedAt == null) return false

        val existingModifiedAt = runCatching { articleIndexer.findModifiedAtByArticleId(articleId) }
            .onFailure { log.warn("Failed to check existing document for article {}, proceeding with indexing: {}", articleId, it.message) }
            .getOrNull()
            ?: return false

        return if (existingModifiedAt >= analyzedAt) {
            log.info("Skipping stale event for article {}: existing modifiedAt={} >= event analyzedAt={}",
                articleId, existingModifiedAt, analyzedAt)
            true
        } else {
            false
        }
    }
}
