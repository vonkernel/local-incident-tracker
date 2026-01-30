package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.collector.domain.exception.CollectionException
import com.vonkernel.lit.collector.domain.model.ArticlePage
import com.vonkernel.lit.collector.domain.port.NewsApiPort
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.exception.MaxRetriesExceededException
import com.vonkernel.lit.core.repository.ArticleRepository
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class ArticleCollectionServiceImpl(
    private val newsApiPort: NewsApiPort,
    private val articleRepository: ArticleRepository
) : ArticleCollectionService {

    private val logger = LoggerFactory.getLogger(ArticleCollectionServiceImpl::class.java)

    override suspend fun collectArticlesForDate(date: LocalDate, pageSize: Int) {
        val apiDate = date.toApiDateFormat()

        // 1. 첫 페이지 조회 (메타데이터 확보용 - 실패 시 즉시 중단)
        val firstPage = runCatching { fetchPageWithRetry(apiDate, 1, pageSize) }
            .getOrElse { throw mapToCollectionException(it) }

        saveArticles(firstPage.articles)

        // 2. 전체 페이지 계산
        val totalPages = calculateTotalPages(firstPage.totalCount, pageSize)
        if (totalPages <= 1) return

        // 3. Phase 1: 나머지 페이지 1차 수집 (실패한 페이지 번호 수집)
        val failedPages = processPages(apiDate, 2..totalPages, pageSize)

        // 4. Phase 2: 실패한 페이지만 골라 2차 재시도 (Batch Retry)
        if (failedPages.isNotEmpty()) {
            retryBatchFailures(apiDate, failedPages, pageSize)
        }
    }

    // ========== Core Logic (Common Pipeline) ==========

    /**
     * 주어진 페이지 번호들을 처리하고, 실패한 페이지 번호 리스트를 반환함
     */
    private suspend fun processPages(date: String, pages: Iterable<Int>, pageSize: Int): List<Int> {
        return pages.map { pageNo ->
            pageNo to runCatching {
                val page = fetchPageWithRetry(date, pageNo, pageSize)
                saveArticles(page.articles)
            }
        }.filter { (pageNo, result) ->
            // 실패 시 로깅만 하고 결과(실패 여부)를 남김
            result.onFailure { e ->
                logger.warn("Failed to process page $pageNo for $date. It will be collected for retry.", e)
            }
            result.isFailure
        }.map { (pageNo, _) ->
            pageNo
        }
    }

    private suspend fun retryBatchFailures(date: String, failedPages: List<Int>, pageSize: Int) {
        logger.warn("Starting batch retry for ${failedPages.size} pages...")

        // 재시도 수행 후에도 여전히 실패한 페이지가 있는지 확인
        val stillFailed = processPages(date, failedPages, pageSize)

        if (stillFailed.isNotEmpty()) {
            throw CollectionException("Failed to collect pages $stillFailed for $date after final retry")
        }
    }

    private suspend fun saveArticles(rawArticles: List<Article>) = withContext(Dispatchers.IO) {
        rawArticles
            .mapNotNull { it.validate().getOrNull() }
            .takeIf { it.isNotEmpty() }
            ?.let { validArticles ->
                // DB에 없는 ID만 필터링
                val existingIds = articleRepository.filterNonExisting(validArticles.map { it.articleId }).toSet()
                validArticles.filter { it.articleId in existingIds }
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                articleRepository.saveAll(it)
            }
    }

    // ========== Utility & Side Effects (Retry Logic) ==========

    private suspend fun fetchPageWithRetry(date: String, pageNo: Int, pageSize: Int): ArticlePage {
        // executeWithRetry 모듈 사용 (Micro-Retry)
        return executeWithRetry(
            maxRetries = 3,
            onRetry = { attempt, nextDelay, e ->
                logger.warn("Fetch failed for $date p$pageNo (Attempt $attempt). Retrying in ${nextDelay}ms.", e)
            }
        ) {
            newsApiPort.fetchArticles(date, pageNo, pageSize)
        }
    }

    private fun mapToCollectionException(e: Throwable): RuntimeException {
        return when (e) {
            is MaxRetriesExceededException -> CollectionException("Max retries exceeded during initialization", e)
            else -> e as? RuntimeException ?: RuntimeException(e)
        }
    }

    // ========== Pure Functions (Calculations) ==========

    private fun LocalDate.toApiDateFormat(): String =
        format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun calculateTotalPages(totalCount: Int, pageSize: Int): Int =
        (totalCount + pageSize - 1) / pageSize
}