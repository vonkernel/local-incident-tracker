package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.CollectionException
import com.vonkernel.lit.collector.domain.model.ArticlePage
import com.vonkernel.lit.collector.domain.port.NewsApiPort
import com.vonkernel.lit.entity.Article
import com.vonkernel.lit.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow

@Service
class ArticleCollectionServiceImpl(
    private val newsApiPort: NewsApiPort,
    private val articleRepository: ArticleRepository
) : ArticleCollectionService {

    private val logger = LoggerFactory.getLogger(ArticleCollectionServiceImpl::class.java)

    override suspend fun collectArticlesForDate(date: LocalDate) =
        collectAllPages(date.toApiDateFormat())

    // ========== Pure Functions ==========

    private fun LocalDate.toApiDateFormat(): String =
        format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun calculateTotalPages(totalCount: Int): Int =
        (totalCount + PAGE_SIZE - 1) / PAGE_SIZE

    private fun calculateBackoffDelay(attempt: Int): Long =
        2.0.pow(attempt).toLong() * 1000

    private fun extractFailedPageNumbers(results: List<IndexedValue<Result<Unit>>>): List<Int> =
        results
            .filter { it.value.isFailure }
            .map { it.index + 1 }

    private fun excludeExistingArticles(articles: List<Article>, existingIds: Set<String>): List<Article> =
        articles.filter { it.articleId !in existingIds }

    private fun validateArticles(items: List<Article>): List<Article> =
        items.mapNotNull { it.validate().getOrNull() }

    // ========== Effect Functions (with side effects) ==========

    private suspend fun collectAllPages(inqDt: String) {
        fetchPageWithRetry(inqDt, pageNo = 1)
            .let { firstPage ->
                Pair(
                    runCatching { saveValidatedArticles(firstPage.articles) },
                    collectRemainingPages(inqDt, 2..calculateTotalPages(firstPage.totalCount))
                )
            }
            .let { (firstPageResult, remainingResults) ->
                retryFailedPagesIfAny(inqDt, firstPageResult, remainingResults)
            }
    }

    private suspend fun collectRemainingPages(inqDt: String, pageRange: IntRange): List<Result<Unit>> =
        pageRange.map { pageNo -> fetchAndSaveArticles(inqDt, pageNo) }

    private suspend fun retryFailedPagesIfAny(
        inqDt: String,
        firstPageResult: Result<Unit>,
        remainingResults: List<Result<Unit>>
    ) {
        (listOf(firstPageResult) + remainingResults)
            .withIndex()
            .toList()
            .let(::extractFailedPageNumbers)
            .takeIf { it.isNotEmpty() }
            ?.let { failedPages -> retryFailedPages(inqDt, failedPages) }
    }

    private suspend fun retryFailedPages(inqDt: String, failedPages: List<Int>) {
        logger.warn("Retrying ${failedPages.size} failed pages for $inqDt")

        failedPages
            .map { pageNo -> fetchAndSaveArticles(inqDt, pageNo, isRetry = true) }
            .withIndex()
            .filter { it.value.isFailure }
            .map { failedPages[it.index] }
            .takeIf { it.isNotEmpty() }
            ?.let { stillFailed ->
                throw CollectionException("Failed to collect pages $stillFailed for $inqDt after retry")
            }
    }

    private suspend fun fetchAndSaveArticles(
        inqDt: String,
        pageNo: Int,
        isRetry: Boolean = false
    ): Result<Unit> =
        runCatching {
            fetchPageWithRetry(inqDt, pageNo)
                .articles
                .also { saveValidatedArticles(it) }
                .let { }
        }.onSuccess {
            if (isRetry) logger.info("Successfully retried page $pageNo")
        }.onFailure { e ->
            logger.error("Failed to ${if (isRetry) "retry" else "fetch"} page $pageNo for $inqDt", e)
        }

    private suspend fun fetchPageWithRetry(
        inqDt: String,
        pageNo: Int,
        maxRetries: Int = 3,
        currentAttempt: Int = 0,
        lastError: Throwable? = null
    ): ArticlePage {
        if (currentAttempt >= maxRetries) {
            throw CollectionException("Failed after $maxRetries retries", lastError)
        }

        return runCatching {
            newsApiPort.fetchArticles(inqDt, pageNo, PAGE_SIZE)
        }.getOrElse { error ->
            val backoffDelay = calculateBackoffDelay(currentAttempt)
            logger.warn("Retry attempt ${currentAttempt + 1} after ${backoffDelay}ms")
            delay(backoffDelay)
            fetchPageWithRetry(inqDt, pageNo, maxRetries, currentAttempt + 1, error)
        }
    }

    private suspend fun saveValidatedArticles(items: List<Article>): Unit = withContext(Dispatchers.IO) {
        items
            .let(::validateArticles)
            .let { validArticles ->
                validArticles
                    .map { it.articleId }
                    .let { articleRepository.filterNonExisting(it).toSet() }
                    .let { nonExistingIds -> excludeExistingArticles(validArticles, nonExistingIds) }
            }
            .takeIf { it.isNotEmpty() }
            ?.let { articleRepository.saveAll(it) }
            ?: Unit
    }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}
