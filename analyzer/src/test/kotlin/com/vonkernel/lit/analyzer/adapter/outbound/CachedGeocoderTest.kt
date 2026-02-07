package com.vonkernel.lit.analyzer.adapter.outbound

import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.CachedGeocoder
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.GeocodingClient
import com.vonkernel.lit.core.entity.Address
import com.vonkernel.lit.core.entity.Coordinate
import com.vonkernel.lit.core.entity.Location
import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.core.port.repository.AddressCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CachedGeocoder 테스트")
class CachedGeocoderTest {

    private val geocodingClient: GeocodingClient = mockk()
    private val addressCache: AddressCacheRepository = mockk()
    private lateinit var cachedGeocoder: CachedGeocoder

    private val cachedLocation = Location(
        coordinate = Coordinate(lat = 37.4979, lon = 126.9270),
        address = Address(
            regionType = RegionType.BJDONG,
            code = "1168010600",
            addressName = "서울 강남구",
            depth1Name = "서울특별시",
            depth2Name = "강남구",
            depth3Name = "역삼동"
        )
    )

    private val apiLocation = Location(
        coordinate = Coordinate(lat = 37.4994, lon = 127.0365),
        address = Address(
            regionType = RegionType.HADONG,
            code = "1168010100",
            addressName = "서울 강남구",
            depth1Name = "서울특별시",
            depth2Name = "강남구",
            depth3Name = "역삼1동"
        )
    )

    @BeforeEach
    fun setUp() {
        cachedGeocoder = CachedGeocoder(geocodingClient, addressCache)
    }

    @Nested
    @DisplayName("geocodeByAddress")
    inner class GeocodeByAddress {

        @Test
        @DisplayName("캐시 hit 시 GeocodingClient 호출 없이 캐시된 Location을 반환한다")
        fun cacheHit_returnsCachedWithoutClient() = runTest {
            // Given
            every { addressCache.findByAddressName("서울 강남구") } returns cachedLocation

            // When
            val result = cachedGeocoder.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo(cachedLocation)
            coVerify(exactly = 0) { geocodingClient.geocodeByAddress(any()) }
        }

        @Test
        @DisplayName("캐시 miss 시 GeocodingClient에 위임하여 결과를 반환한다")
        fun cacheMiss_delegatesToClient() = runTest {
            // Given
            every { addressCache.findByAddressName("서울 강남구") } returns null
            coEvery { geocodingClient.geocodeByAddress("서울 강남구") } returns listOf(apiLocation)

            // When
            val result = cachedGeocoder.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo(apiLocation)
            coVerify(exactly = 1) { geocodingClient.geocodeByAddress("서울 강남구") }
        }

        @Test
        @DisplayName("캐시 miss + GeocodingClient 결과 없음 시 빈 리스트를 반환한다")
        fun cacheMiss_clientReturnsEmpty() = runTest {
            // Given
            every { addressCache.findByAddressName("없는 주소") } returns null
            coEvery { geocodingClient.geocodeByAddress("없는 주소") } returns emptyList()

            // When
            val result = cachedGeocoder.geocodeByAddress("없는 주소")

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("geocodeByKeyword")
    inner class GeocodeByKeyword {

        @Test
        @DisplayName("캐시 hit 시 GeocodingClient 호출 없이 캐시된 Location을 반환한다")
        fun cacheHit_returnsCachedWithoutClient() = runTest {
            // Given
            every { addressCache.findByAddressName("강남역") } returns cachedLocation

            // When
            val result = cachedGeocoder.geocodeByKeyword("강남역")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo(cachedLocation)
            coVerify(exactly = 0) { geocodingClient.geocodeByKeyword(any()) }
        }

        @Test
        @DisplayName("캐시 miss 시 GeocodingClient에 위임하여 결과를 반환한다")
        fun cacheMiss_delegatesToClient() = runTest {
            // Given
            every { addressCache.findByAddressName("강남역") } returns null
            coEvery { geocodingClient.geocodeByKeyword("강남역") } returns listOf(apiLocation)

            // When
            val result = cachedGeocoder.geocodeByKeyword("강남역")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0]).isEqualTo(apiLocation)
            coVerify(exactly = 1) { geocodingClient.geocodeByKeyword("강남역") }
        }

        @Test
        @DisplayName("캐시 miss + GeocodingClient 결과 없음 시 빈 리스트를 반환한다")
        fun cacheMiss_clientReturnsEmpty() = runTest {
            // Given
            every { addressCache.findByAddressName("없는 장소") } returns null
            coEvery { geocodingClient.geocodeByKeyword("없는 장소") } returns emptyList()

            // When
            val result = cachedGeocoder.geocodeByKeyword("없는 장소")

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Test
    @DisplayName("addressCache.findByAddressName은 정확한 query로 호출된다")
    fun cacheCalledWithExactQuery() = runTest {
        // Given
        every { addressCache.findByAddressName("서울특별시 강남구 역삼동") } returns cachedLocation

        // When
        cachedGeocoder.geocodeByAddress("서울특별시 강남구 역삼동")

        // Then
        verify(exactly = 1) { addressCache.findByAddressName("서울특별시 강남구 역삼동") }
    }
}
