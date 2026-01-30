package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.exception.ArticleAnalysisException
import com.vonkernel.lit.analyzer.domain.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.KeywordAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.RefineArticleAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.TopicAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.repository.AnalysisResultRepository
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticleAnalysisService(
    private val refineArticleAnalyzer: RefineArticleAnalyzer,
    private val incidentTypeAnalyzer: IncidentTypeAnalyzer,
    private val urgencyAnalyzer: UrgencyAnalyzer,
    private val keywordAnalyzer: KeywordAnalyzer,
    private val topicAnalyzer: TopicAnalyzer,
    private val locationAnalysisService: LocationAnalysisService,
    private val analysisResultRepository: AnalysisResultRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(article: Article) {
        log.info("Starting analysis for article: {}", article.articleId)

        try {
            ensureNoExistingAnalysis(article.articleId)

            analyzeArticle(article)
                .let { analysisResultRepository.save(it) }

            log.info("Analysis completed and saved for article: {}", article.articleId)
        } catch (e: Exception) {
            log.error("Analysis failed for article {}: {}", article.articleId, e.message, e)
            throw ArticleAnalysisException(
                articleId = article.articleId,
                message = "Analysis failed for article ${article.articleId}",
                cause = e
            )
        }
    }

    private fun ensureNoExistingAnalysis(articleId: String) {
        if (analysisResultRepository.existsByArticleId(articleId)) {
            log.info("Existing analysis found for article: {}, deleting before re-analysis", articleId)
            analysisResultRepository.deleteByArticleId(articleId)
        }
    }

    private suspend fun analyzeArticle(article: Article): AnalysisResult = coroutineScope {
        val articleId = article.articleId

        // Phase 1: Refine article (sequential)
        val refinedArticle = withRetry("refineArticleAnalyzer", articleId) {
            refineArticleAnalyzer.analyze(article)
        }

        // Phase 2: 5 parallel analyses based on refined article
        val incidentTypes = async {
            withRetry("incidentTypeAnalyzer", articleId) {
                incidentTypeAnalyzer.analyze(refinedArticle.title, refinedArticle.content)
            }
        }
        val urgency = async {
            withRetry("urgencyAnalyzer", articleId) {
                urgencyAnalyzer.analyze(refinedArticle.title, refinedArticle.content)
            }
        }
        val keywords = async {
            withRetry("keywordAnalyzer", articleId) {
                keywordAnalyzer.analyze(refinedArticle.summary)
            }
        }
        val topic = async {
            withRetry("topicAnalyzer", articleId) {
                topicAnalyzer.analyze(refinedArticle.summary)
            }
        }
        val locations = async {
            locationAnalysisService.analyze(articleId, refinedArticle.title, refinedArticle.content)
        }

        AnalysisResult(
            articleId = articleId,
            refinedArticle = refinedArticle,
            incidentTypes = incidentTypes.await(),
            urgency = urgency.await(),
            keywords = keywords.await(),
            topic = topic.await(),
            locations = locations.await()
        )
    }

    private suspend fun <T> withRetry(operationName: String, articleId: String, block: suspend () -> T): T =
        executeWithRetry(maxRetries = 2, onRetry = { attempt, delay, e ->
            log.warn("Retrying {} for article {} (attempt {}, delay {}ms): {}",
                operationName, articleId, attempt, delay, e.message)
        }, block = block)
}
