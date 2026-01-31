package com.vonkernel.lit.analyzer.domain.service

import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationAnalyzer
import com.vonkernel.lit.analyzer.domain.port.analyzer.LocationValidator
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation
import com.vonkernel.lit.analyzer.domain.port.analyzer.model.LocationType
import com.vonkernel.lit.analyzer.domain.port.geocoding.Geocoder
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LocationsExtractor(
    private val locationAnalyzer: LocationAnalyzer,
    private val locationValidator: LocationValidator,
    private val geocoder: Geocoder
) : RetryableAnalysisService() {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun process(articleId: String, title: String, content: String): List<Location> {
        val extracted = withRetry("extract-location", articleId) {
            locationAnalyzer.analyze(title, content)
        }
        val validated = withRetry("validate-location", articleId) {
            locationValidator.validate(title, content, extracted)
        }
        return resolveLocations(articleId, validated)
    }

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
        withRetry("geocode-by-address", articleId) {
            geocoder.geocodeByAddress(name)
                .ifEmpty { listOf(unresolvedLocation(name)) }
        }

    private suspend fun resolveLandmark(articleId: String, name: String): List<Location> =
        withRetry("geocode-by-keyword", articleId) {
            geocoder.geocodeByKeyword(name)
                .ifEmpty { geocoder.geocodeByAddress(name) }
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
