package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.analyzer.IncidentTypeAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.KeywordAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.LocationAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.RefineArticleAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.TopicAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.UrgencyAnalyzer
import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.GeocodingPort
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.core.repository.AnalysisResultRepository
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val locationAnalyzer: LocationAnalyzer,
    private val geocodingPort: GeocodingPort,
    private val analysisResultRepository: AnalysisResultRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(article: Article) {
        log.info("Starting analysis for article: {}", article.articleId)

        ensureNoExistingAnalysis(article.articleId)

        analyzeArticle(article)
            .let { analysisResultRepository.save(it) }

        log.info("Analysis completed and saved for article: {}", article.articleId)
    }

    private fun ensureNoExistingAnalysis(articleId: String) {
        if (analysisResultRepository.existsByArticleId(articleId)) {
            log.info("Existing analysis found for article: {}, deleting before re-analysis", articleId)
            analysisResultRepository.deleteByArticleId(articleId)
        }
    }

    private suspend fun analyzeArticle(article: Article): AnalysisResult = coroutineScope {
        // Phase 1: Refine article (sequential)
        val refinedArticle = withRetry("refineArticleAnalyzer") {
            refineArticleAnalyzer.analyze(article)
        }

        // Phase 2: 5 parallel analyses based on refined article
        val incidentTypes = async {
            withRetry("incidentTypeAnalyzer") {
                incidentTypeAnalyzer.analyze(refinedArticle.title, refinedArticle.content)
            }
        }
        val urgency = async {
            withRetry("urgencyAnalyzer") {
                urgencyAnalyzer.analyze(refinedArticle.title, refinedArticle.content)
            }
        }
        val keywords = async {
            withRetry("keywordAnalyzer") {
                keywordAnalyzer.analyze(refinedArticle.summary)
            }
        }
        val topic = async {
            withRetry("topicAnalyzer") {
                topicAnalyzer.analyze(refinedArticle.summary)
            }
        }
        val locations = async {
            withRetry("locationAnalyzer") {
                locationAnalyzer.analyze(refinedArticle.title, refinedArticle.content)
            }
        }.await().let { resolveLocations(it) }

        AnalysisResult(
            articleId = article.articleId,
            refinedArticle = refinedArticle,
            incidentTypes = incidentTypes.await(),
            urgency = urgency.await(),
            keywords = keywords.await(),
            topic = topic.await(),
            locations = locations
        )
    }

    private suspend fun <T> withRetry(operationName: String, block: suspend () -> T): T =
        executeWithRetry(maxRetries = 2, onRetry = { attempt, delay, e ->
            log.warn("Retrying {} (attempt {}, delay {}ms): {}", operationName, attempt, delay, e.message)
        }, block = block)

    private suspend fun resolveLocations(extractedLocations: List<ExtractedLocation>): List<Location> =
        coroutineScope {
            extractedLocations
                .map { async { resolveLocation(it) } }
                .awaitAll()
                .flatten()
        }

    private suspend fun resolveLocation(extracted: ExtractedLocation): List<Location> =
        when (extracted.type) {
            LocationType.ADDRESS -> resolveAddress(extracted.name)
            LocationType.LANDMARK -> resolveLandmark(extracted.name)
            LocationType.UNRESOLVABLE -> listOf(unresolvedLocation(extracted.name))
        }

    private suspend fun resolveAddress(name: String): List<Location> =
        withRetry("geocodeByAddress") {
            geocodingPort.geocodeByAddress(name)
                .ifEmpty { geocodingPort.geocodeByKeyword(name) }
                .ifEmpty { listOf(unresolvedLocation(name)) }
        }

    private suspend fun resolveLandmark(name: String): List<Location> =
        withRetry("geocodeByKeyword") {
            geocodingPort.geocodeByKeyword(name)
                .ifEmpty { geocodingPort.geocodeByAddress(name) }
                .ifEmpty { listOf(unresolvedLocation(name)) }
        }

    private fun unresolvedLocation(name: String): Location =
        Location(
            coordinate = null,
            address = Address(
                regionType = RegionType.UNKNOWN,
                code = "UNKNOWN",
                addressName = name
            )
        )
}
