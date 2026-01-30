package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.analyzer.LocationAnalyzer
import com.vonkernel.lit.analyzer.domain.analyzer.LocationValidator
import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.GeocodingPort
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.core.util.executeWithRetry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LocationAnalysisService(
    private val locationAnalyzer: LocationAnalyzer,
    private val locationValidator: LocationValidator,
    private val geocodingPort: GeocodingPort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun analyze(articleId: String, title: String, content: String): List<Location> {
        val extracted = withRetry("locationAnalyzer", articleId) {
            locationAnalyzer.analyze(title, content)
        }
        val validated = withRetry("locationValidator", articleId) {
            locationValidator.validate(title, content, extracted)
        }
        return resolveLocations(articleId, validated)
    }

    private suspend fun <T> withRetry(operationName: String, articleId: String, block: suspend () -> T): T =
        executeWithRetry(maxRetries = 2, onRetry = { attempt, delay, e ->
            log.warn("Retrying {} for article {} (attempt {}, delay {}ms): {}",
                operationName, articleId, attempt, delay, e.message)
        }, block = block)

    private suspend fun resolveLocations(articleId: String, extractedLocations: List<ExtractedLocation>): List<Location> =
        coroutineScope {
            extractedLocations
                .map { extracted ->
                    async {
                        try {
                            resolveLocation(articleId, extracted)
                        } catch (e: Exception) {
                            log.warn("Failed to geocode location '{}' (type={}) for article {}: {}",
                                extracted.name, extracted.type, articleId, e.message)
                            listOf(unresolvedLocation(extracted.name))
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

    private suspend fun resolveLocation(articleId: String, extracted: ExtractedLocation): List<Location> =
        when (extracted.type) {
            LocationType.ADDRESS -> resolveAddress(articleId, extracted.name)
            LocationType.LANDMARK -> resolveLandmark(articleId, extracted.name)
            LocationType.UNRESOLVABLE -> listOf(unresolvedLocation(extracted.name))
        }

    private suspend fun resolveAddress(articleId: String, name: String): List<Location> =
        withRetry("geocodeByAddress", articleId) {
            geocodingPort.geocodeByAddress(name)
                .ifEmpty { listOf(unresolvedLocation(name)) }
        }

    private suspend fun resolveLandmark(articleId: String, name: String): List<Location> =
        withRetry("geocodeByKeyword", articleId) {
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
