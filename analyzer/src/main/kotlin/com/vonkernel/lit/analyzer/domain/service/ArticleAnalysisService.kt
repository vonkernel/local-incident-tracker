package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.exception.ArticleAnalysisException
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.port.repository.AnalysisResultRepository
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArticleAnalysisService(
    private val articleRefiner: ArticleRefiner,
    private val incidentTypeExtractor: IncidentTypeExtractor,
    private val urgencyExtractor: UrgencyExtractor,
    private val locationsExtractor: LocationsExtractor,
    private val keywordsExtractor: KeywordsExtractor,
    private val topicExtractor: TopicExtractor,
    private val analysisResultRepository: AnalysisResultRepository,

) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(article: Article, articleUpdatedAt: Instant? = null) {
        log.info("Starting analysis for article: {}", article.articleId)

        try {
            ensureNoExistingAnalysis(article.articleId)

            analyzeArticle(article)
                .let { analysisResultRepository.save(it, articleUpdatedAt) }

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
        articleRefiner.process(article).let { refinedArticle ->
            val articleId = article.articleId
            val title = refinedArticle.title
            val content = refinedArticle.content
            val summary = refinedArticle.summary

            val incidentTypes = async { incidentTypeExtractor.process(articleId, title, content) }
            val urgency = async { urgencyExtractor.process(articleId, title, content) }
            val locations = async { locationsExtractor.process(articleId, title, content) }
            val keywords = async { keywordsExtractor.process(articleId, summary) }
            val topic = async { topicExtractor.process(articleId, summary) }

            AnalysisResult(
                articleId = article.articleId,
                refinedArticle = refinedArticle,
                incidentTypes = incidentTypes.await(),
                urgency = urgency.await(),
                keywords = keywords.await(),
                topic = topic.await(),
                locations = locations.await()
            )
        }
    }
}
