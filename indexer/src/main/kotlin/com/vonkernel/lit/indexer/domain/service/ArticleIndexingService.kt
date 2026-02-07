package com.vonkernel.lit.indexer.domain.service

import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.ArticleIndexDocument
import com.vonkernel.lit.core.util.executeWithRetry
import com.vonkernel.lit.indexer.domain.assembler.IndexDocumentAssembler
import com.vonkernel.lit.indexer.domain.exception.ArticleIndexingException
import com.vonkernel.lit.indexer.domain.port.Embedder
import com.vonkernel.lit.indexer.domain.port.ArticleIndexer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ArticleIndexingService(
    private val embedder: Embedder,
    private val articleIndexer: ArticleIndexer,
    @param:Value("\${indexing.retry.max-retries:2}") private val maxRetries: Int = 2,
    @param:Value("\${indexing.retry.base-delay-ms:1000}") private val baseDelayMs: Long = 1000L,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun index(analysisResult: AnalysisResult, analyzedAt: Instant? = null) {
        if (isStale(analysisResult.articleId, analyzedAt)) return

        embedOrNull(analysisResult.refinedArticle.content)
            .let { embedding -> IndexDocumentAssembler.assemble(analysisResult, embedding, analyzedAt) }
            .also { document -> indexOrThrow(document) }
    }

    suspend fun indexAll(analysisResults: List<AnalysisResult>, analyzedAts: List<Instant?> = emptyList()) {
        if (analysisResults.isEmpty()) return
        log.info("Starting batch indexing for {} articles", analysisResults.size)

        filterFresh(analysisResults, analyzedAts)
            .takeIf { it.isNotEmpty() }
            ?.unzip()
            ?.let { (freshResults, freshAnalyzedAts) ->
                assembleDocuments(freshResults, freshAnalyzedAts)
                    .also { indexAllAndLog(it, freshResults.size, analysisResults.size) }
            }
            ?: log.info("All {} articles skipped as stale", analysisResults.size)
    }

    // 복구 가능한 부수효과: 안전한 값 반환
    private suspend fun embedOrNull(content: String): ByteArray? =
        runCatching { embedder.embed(content) }
            .onFailure { log.warn("Embedding failed, proceeding without: {}", it.message) }
            .getOrNull()

    // 복구 가능한 부수효과: 안전한 값 반환 (배치)
    private suspend fun embedAllOrNulls(contents: List<String>, size: Int): List<ByteArray?> =
        runCatching { embedder.embedAll(contents) }
            .onFailure { log.warn("Batch embedding failed, proceeding without embeddings: {}", it.message) }
            .getOrElse { List(size) { null } }

    // 복구 불가능한 부수효과: 이름에 throw 의도 표현
    private suspend fun indexOrThrow(document: ArticleIndexDocument) {
        runCatching {
            executeWithRetry(
                maxRetries = maxRetries,
                baseDelayMs = baseDelayMs,
                onRetry = { attempt, delay, e ->
                    log.warn("Retrying index for article {} (attempt={}, delay={}ms): {}", document.articleId, attempt, delay, e.message)
                }
            ) { articleIndexer.index(document) }
        }
            .onSuccess { log.info("Indexed article: {}", document.articleId) }
            .getOrElse { e -> throw ArticleIndexingException(document.articleId, "Failed to index article: ${document.articleId}", e) }
    }

    private suspend fun assembleDocuments(
        results: List<AnalysisResult>, analyzedAts: List<Instant?>
    ): List<ArticleIndexDocument> =
        embedAllOrNulls(results.map { it.refinedArticle.content }, results.size)
            .zip(results.zip(analyzedAts)) { embedding, (result, analyzedAt) ->
                IndexDocumentAssembler.assemble(result, embedding, analyzedAt)
            }

    private suspend fun indexAllAndLog(documents: List<ArticleIndexDocument>, freshCount: Int, totalCount: Int) {
        executeWithRetry(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            onRetry = { attempt, delay, e ->
                log.warn("Retrying batch indexing (attempt={}, delay={}ms): {}", attempt, delay, e.message)
            }
        ) { articleIndexer.indexAll(documents) }
        log.info("Batch indexing completed for {} articles ({} skipped as stale)", freshCount, totalCount - freshCount)
    }

    private suspend fun filterFresh(
        analysisResults: List<AnalysisResult>,
        analyzedAts: List<Instant?>,
    ): List<Pair<AnalysisResult, Instant?>> =
        padToSize(analyzedAts, analysisResults.size)
            .let { analysisResults.zip(it) }
            .filter { (result, analyzedAt) -> !isStale(result.articleId, analyzedAt) }

    private fun padToSize(list: List<Instant?>, targetSize: Int): List<Instant?> =
        list + List(targetSize - list.size) { null }

    private suspend fun isStale(articleId: String, analyzedAt: Instant?): Boolean =
        analyzedAt
            ?.let { findModifiedAtOrNull(articleId) }
            ?.let { existing -> existing >= analyzedAt }
            ?.also { stale ->
                if (stale) log.info("Skipping stale event for article {}: existing modifiedAt >= event analyzedAt={}", articleId, analyzedAt)
            }
            ?: false

    // 복구 가능한 부수효과: 안전한 값 반환
    private suspend fun findModifiedAtOrNull(articleId: String): Instant? =
        runCatching { articleIndexer.findModifiedAtByArticleId(articleId) }
            .onFailure { log.warn("Failed to check existing document for article {}, proceeding with indexing: {}", articleId, it.message) }
            .getOrNull()
}
