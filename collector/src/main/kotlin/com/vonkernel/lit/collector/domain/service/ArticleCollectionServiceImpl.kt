package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.CollectionException
import com.vonkernel.lit.collector.domain.model.ArticlePage
import com.vonkernel.lit.collector.domain.port.NewsFetcher
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.exception.MaxRetriesExceededException
import com.vonkernel.lit.core.port.repository.ArticleRepository
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ArticleCollectionServiceImpl(
    private val newsFetcher: NewsFetcher,
    private val articleRepository: ArticleRepository
) : ArticleCollectionService {

    private val logger = LoggerFactory.getLogger(ArticleCollectionServiceImpl::class.java)

    override suspend fun collectArticlesForDate(date: LocalDate, pageSize: Int) {
        date.toApiDateFormat().let { apiDate ->
            fetchFirstPageOrThrow(apiDate, pageSize)
                .also { saveArticles(it.articles) }
                .let { calculateTotalPages(it.totalCount, pageSize) }
                .takeIf { it > 1 }
                ?.let { totalPages -> collectRemainingPages(apiDate, 2..totalPages, pageSize) }
        }
    }

    // ========== Page Collection Pipeline ==========

    private suspend fun collectRemainingPages(date: String, pages: IntRange, pageSize: Int) {
        processPages(date, pages, pageSize)
            .takeIf { it.isNotEmpty() }
            ?.let { retryFailedPagesOrThrow(date, it, pageSize) }
    }

    private suspend fun processPages(date: String, pages: Iterable<Int>, pageSize: Int): List<Int> =
        pages.mapNotNull { fetchAndSaveOrFailedPage(date, it, pageSize) }

    private suspend fun fetchAndSaveOrFailedPage(date: String, pageNo: Int, pageSize: Int): Int? =
        runCatching { fetchPageWithRetry(date, pageNo, pageSize).also { saveArticles(it.articles) } }
            .fold(
                onSuccess = { null },
                onFailure = { e ->
                    logger.warn("Failed to process page $pageNo for $date. It will be collected for retry.", e)
                    pageNo
                }
            )

    private suspend fun retryFailedPagesOrThrow(date: String, failedPages: List<Int>, pageSize: Int) {
        logger.warn("Starting batch retry for ${failedPages.size} pages...")
        processPages(date, failedPages, pageSize)
            .takeIf { it.isNotEmpty() }
            ?.let { throw CollectionException("Failed to collect pages $it for $date after final retry") }
    }

    // ========== Article Persistence ==========

    private suspend fun saveArticles(rawArticles: List<Article>) = withContext(Dispatchers.IO) {
        rawArticles.mapNotNull { it.validate().getOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.let { filterNewArticles(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { articleRepository.saveAll(it) }
    }

    private fun filterNewArticles(articles: List<Article>): List<Article> =
        articleRepository.filterNonExisting(articles.map { it.articleId })
            .toSet()
            .let { newIds -> articles.filter { it.articleId in newIds } }

    // ========== Side Effects ==========

    private suspend fun fetchFirstPageOrThrow(date: String, pageSize: Int): ArticlePage =
        runCatching { fetchPageWithRetry(date, 1, pageSize) }
            .getOrElse { throw mapToCollectionException(it) }

    private suspend fun fetchPageWithRetry(date: String, pageNo: Int, pageSize: Int): ArticlePage =
        executeWithRetry(
            maxRetries = 3,
            onRetry = { attempt, nextDelay, e ->
                logger.warn("Fetch failed for $date p$pageNo (Attempt $attempt). Retrying in ${nextDelay}ms.", e)
            }
        ) {
            newsFetcher.fetchArticles(date, pageNo, pageSize)
        }

    // ========== Pure Functions ==========

    private fun mapToCollectionException(e: Throwable): RuntimeException =
        when (e) {
            is MaxRetriesExceededException -> CollectionException("Max retries exceeded during initialization", e)
            else -> e as? RuntimeException ?: RuntimeException(e)
        }

    private fun LocalDate.toApiDateFormat(): String =
        format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun calculateTotalPages(totalCount: Int, pageSize: Int): Int =
        (totalCount + pageSize - 1) / pageSize
}
