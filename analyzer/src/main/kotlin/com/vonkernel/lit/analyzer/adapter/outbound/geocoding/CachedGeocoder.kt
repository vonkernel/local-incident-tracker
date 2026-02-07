package com.vonkernel.lit.analyzer.adapter.outbound.geocoding

import com.vonkernel.lit.analyzer.domain.port.geocoding.Geocoder
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.port.repository.AddressCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CachedGeocoder(
    private val geocodingClient: GeocodingClient,
    private val addressCache: AddressCacheRepository
) : Geocoder {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun geocodeByAddress(query: String): List<Location> =
        findCached(query)?.let(::listOf)
            ?: geocodingClient.geocodeByAddress(query)

    override suspend fun geocodeByKeyword(query: String): List<Location> =
        findCached(query)?.let(::listOf)
            ?: geocodingClient.geocodeByKeyword(query)

    private suspend fun findCached(query: String): Location? =
        withContext(Dispatchers.IO) { addressCache.findByAddressName(query) }
            ?.also { log.debug("Address cache hit for: {}", query) }
}
