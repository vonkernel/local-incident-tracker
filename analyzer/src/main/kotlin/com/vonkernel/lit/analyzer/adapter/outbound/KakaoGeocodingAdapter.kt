package com.vonkernel.lit.analyzer.adapter.outbound

import com.vonkernel.lit.analyzer.adapter.outbound.model.KakaoAddress
import com.vonkernel.lit.analyzer.adapter.outbound.model.KakaoAddressDocument
import com.vonkernel.lit.analyzer.adapter.outbound.model.KakaoAddressResponse
import com.vonkernel.lit.analyzer.adapter.outbound.model.KakaoKeywordDocument
import com.vonkernel.lit.analyzer.adapter.outbound.model.KakaoKeywordResponse
import com.vonkernel.lit.analyzer.domain.port.GeocodingPort
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.persistence.jpa.JpaAddressRepository
import com.vonkernel.lit.persistence.mapper.LocationMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KakaoGeocodingAdapter(
    @param:Qualifier("kakaoWebClient") private val webClient: WebClient,
    private val jpaAddressRepository: JpaAddressRepository
) : GeocodingPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun geocodeByAddress(query: String): List<Location> =
        findCached(query)?.let { listOf(it) }
            ?: searchAddress(query)?.let { mapToLocations(it, query) }
            ?: searchAddressBroader(query)

    override suspend fun geocodeByKeyword(query: String): List<Location> =
        findCached(query)?.let { listOf(it) }
            ?: (searchKeyword(query)?.let { keywordDoc ->
                keywordDoc.addressName
                    ?.let { searchAddress(it) }
                    ?.let { mapToLocations(it, query) }
                    ?: listOf(locationFromCoordinate(keywordDoc.x, keywordDoc.y, query))
            } ?: emptyList())

    private suspend fun findCached(query: String): Location? =
        withContext(Dispatchers.IO) {
            jpaAddressRepository.findFirstByAddressName(query)
        }?.also {
            log.debug("Address cache hit for: {}", query)
        }?.let(LocationMapper::toDomainModel)

    private suspend fun searchAddressBroader(query: String): List<Location> {
        val parts = query.split(" ")
        if (parts.size < 2) return emptyList()

        val broader = parts.dropLast(1).joinToString(" ")
        log.debug("Address search fallback: '{}' -> '{}'", query, broader)
        return searchAddress(broader)?.let { mapToLocations(it, query) } ?: emptyList()
    }

    private suspend fun searchAddress(query: String): KakaoAddressDocument? =
        webClient.get()
            .uri {
                it.path("/v2/local/search/address.json")
                    .queryParam("query", query)
                    .queryParam("analyze_type", "similar")
                    .build()
            }
            .retrieve()
            .bodyToMono<KakaoAddressResponse>()
            .awaitSingleOrNull()
            ?.documents?.firstOrNull()

    private suspend fun searchKeyword(query: String): KakaoKeywordDocument? =
        webClient.get()
            .uri {
                it.path("/v2/local/search/keyword.json")
                    .queryParam("query", query)
                    .build()
            }
            .retrieve()
            .bodyToMono<KakaoKeywordResponse>()
            .awaitSingleOrNull()
            ?.documents?.firstOrNull()

    private fun mapToLocations(document: KakaoAddressDocument, originalName: String): List<Location> {
        val addr = document.address ?: return listOf(locationFromCoordinate(document.x, document.y, originalName))
        val coordinate = parseCoordinate(document.x, document.y)
            ?: return listOf(locationFromCoordinate(document.x, document.y, originalName))

        return buildList {
            if (addr.hCode.isNotBlank()) {
                add(buildLocation(coordinate, RegionType.HADONG, addr.hCode, originalName, addr, addr.region3DepthHName))
            } else if (addr.bCode.isNotBlank()) {
                add(buildLocation(coordinate, RegionType.BJDONG, addr.bCode, originalName, addr, addr.region3DepthName))
            }
            if (isEmpty()) {
                add(locationFromCoordinate(document.x, document.y, originalName))
            }
        }
    }

    private fun buildLocation(
        coordinate: Coordinate,
        regionType: RegionType,
        code: String,
        originalName: String,
        addr: KakaoAddress,
        depth3Name: String
    ): Location =
        Location(
            coordinate = coordinate,
            address = Address(
                regionType = regionType,
                code = code,
                addressName = originalName,
                depth1Name = addr.region1DepthName.ifBlank { null },
                depth2Name = addr.region2DepthName.ifBlank { null },
                depth3Name = depth3Name.ifBlank { null }
            )
        )

    private fun parseCoordinate(x: String?, y: String?): Coordinate? {
        val lon = x?.toDoubleOrNull() ?: return null
        val lat = y?.toDoubleOrNull() ?: return null
        return Coordinate(lat = lat, lon = lon)
    }

    private fun locationFromCoordinate(x: String?, y: String?, originalName: String): Location =
        Location(
            coordinate = parseCoordinate(x, y),
            address = Address(regionType = RegionType.UNKNOWN, code = "UNKNOWN", addressName = originalName)
        )
}
