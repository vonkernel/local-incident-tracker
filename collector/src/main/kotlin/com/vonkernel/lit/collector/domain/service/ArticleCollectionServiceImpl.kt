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

    override suspend fun collectArticlesForDate(date: LocalDate) {
        val inqDt = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

        val firstPage = fetchPageWithRetry(inqDt, pageNo = 1, numOfRows = 1000)
        val totalPages = calculateTotalPages(firstPage.totalCount, pageSize = 1000)

        val firstPageResult = runCatching {
            processAndSave(firstPage.articles)
        }

        val remainingResults = (2..totalPages)
            .map { pageNo -> fetchAndProcess(inqDt, pageNo) }
            .partition { it.isSuccess }

        val allResults = listOf(firstPageResult) + remainingResults.first + remainingResults.second
        val failedPages = allResults
            .withIndex()
            .filter { it.value.isFailure }
            .map { it.index + 1 }

        if (failedPages.isNotEmpty()) {
            logger.warn("Retrying ${failedPages.size} failed pages for $inqDt")
            val retryResults = retryPages(inqDt, failedPages)

            val stillFailed = retryResults
                .withIndex()
                .filter { it.value.isFailure }
                .map { failedPages[it.index] }

            if (stillFailed.isNotEmpty()) {
                throw CollectionException(
                    "Failed to collect pages $stillFailed for $inqDt after retry"
                )
            }
        }
    }

    private fun calculateTotalPages(totalCount: Int, pageSize: Int): Int =
        (totalCount + pageSize - 1) / pageSize

    private suspend fun fetchAndProcess(inqDt: String, pageNo: Int): Result<Unit> = runCatching {
        val page = fetchPageWithRetry(inqDt, pageNo, numOfRows = 1000)
        processAndSave(page.articles)
    }.onFailure { e ->
        logger.error("Failed to fetch page $pageNo for $inqDt", e)
    }

    private suspend fun retryPages(inqDt: String, pageNumbers: List<Int>): List<Result<Unit>> =
        pageNumbers.map { pageNo ->
            runCatching {
                val page = fetchPageWithRetry(inqDt, pageNo, numOfRows = 1000)
                processAndSave(page.articles)
            }.onSuccess {
                logger.info("Successfully retried page $pageNo")
            }.onFailure { e ->
                logger.error("Retry failed for page $pageNo", e)
            }
        }

    private suspend fun fetchPageWithRetry(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int,
        maxRetries: Int = 3
    ): ArticlePage {
        var lastError: Throwable? = null

        repeat(maxRetries) { attempt ->
            runCatching {
                newsApiPort.fetchArticles(inqDt, pageNo, numOfRows)
            }.onSuccess { response ->
                return response
            }.onFailure { error ->
                lastError = error
                if (attempt < maxRetries - 1) {
                    val backoffDelay = calculateBackoffDelay(attempt)
                    logger.warn("Retry attempt ${attempt + 1} after ${backoffDelay}ms")
                    delay(backoffDelay)
                }
            }
        }

        throw CollectionException("Failed after $maxRetries retries", lastError)
    }

    private fun calculateBackoffDelay(attempt: Int): Long =
        2.0.pow(attempt).toLong() * 1000

    private suspend fun processAndSave(items: List<Article>) = withContext(Dispatchers.IO) {
        val newArticles = items
            .asSequence()
            .mapNotNull { it.validate().getOrNull() }
            .toList()
            .let { articles ->
                val articleIds = articles.map { it.articleId }
                val nonExistingIds = articleRepository.filterNonExisting(articleIds).toSet()
                articles.filter { it.articleId in nonExistingIds }
            }

        if (newArticles.isNotEmpty()) {
            articleRepository.saveAll(newArticles)
        }
    }
}

