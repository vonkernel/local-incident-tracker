package com.vonkernel.lit.analyzer.adapter.outbound

import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.KakaoGeocodingClient
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoAddress
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoAddressDocument
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoAddressResponse
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoKeywordDocument
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoKeywordResponse
import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.model.KakaoMeta
import com.vonkernel.lit.core.entity.RegionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@DisplayName("KakaoGeocodingClient 테스트")
class KakaoGeocodingClientTest {

    private val webClient: WebClient = mockk()
    private lateinit var client: KakaoGeocodingClient

    private val defaultMeta = KakaoMeta(totalCount = 1, pageableCount = 1, isEnd = true)

    private val defaultKakaoAddress = KakaoAddress(
        addressName = "서울 강남구 역삼동",
        region1DepthName = "서울특별시",
        region2DepthName = "강남구",
        region3DepthName = "역삼동",
        region3DepthHName = "역삼1동",
        hCode = "1168010100",
        bCode = "1168010600",
        x = "127.0365",
        y = "37.4994"
    )

    private val defaultAddressDocument = KakaoAddressDocument(
        addressName = "서울 강남구 역삼동",
        addressType = "REGION_ADDR",
        x = "127.0365",
        y = "37.4994",
        address = defaultKakaoAddress,
        roadAddress = null
    )

    @BeforeEach
    fun setUp() {
        client = KakaoGeocodingClient(webClient)
    }

    private fun mockWebClientGetSingle(responseMono: Mono<out Any>) {
        val responseSpec = mockk<WebClient.ResponseSpec>()

        every { webClient.get() } returns mockk(relaxed = true) {
            every { uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } answers {
                mockk(relaxed = true) {
                    every { retrieve() } returns responseSpec
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        every { responseSpec.bodyToMono(any<Class<*>>()) } returns responseMono as Mono<Any>
        @Suppress("UNCHECKED_CAST")
        every { responseSpec.bodyToMono(any<ParameterizedTypeReference<*>>()) } returns responseMono as Mono<Any>
    }

    private fun mockWebClientGetSequential(firstMono: Mono<out Any>, secondMono: Mono<out Any>) {
        val responseSpec = mockk<WebClient.ResponseSpec>()

        every { webClient.get() } returns mockk(relaxed = true) {
            every { uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } answers {
                mockk(relaxed = true) {
                    every { retrieve() } returns responseSpec
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        every { responseSpec.bodyToMono(any<Class<*>>()) } returnsMany listOf(firstMono as Mono<Any>, secondMono as Mono<Any>)
        @Suppress("UNCHECKED_CAST")
        every { responseSpec.bodyToMono(any<ParameterizedTypeReference<*>>()) } returnsMany listOf(firstMono as Mono<Any>, secondMono as Mono<Any>)
    }

    @Nested
    @DisplayName("geocodeByAddress")
    inner class GeocodeByAddress {

        @Test
        @DisplayName("주소 검색 성공 (b_code + h_code) - HADONG 우선 1개 Location")
        fun bothCodes_returnsHadongOnly() = runTest {
            // Given
            mockWebClientGetSingle(Mono.just(KakaoAddressResponse(defaultMeta, listOf(defaultAddressDocument))))

            // When
            val result = client.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.HADONG)
            assertThat(result[0].address.code).isEqualTo("1168010100")
            assertThat(result[0].address.depth3Name).isEqualTo("역삼1동")
        }

        @Test
        @DisplayName("주소 검색 성공 (b_code만) - BJDONG 1개 Location")
        fun bCodeOnly_returnsOneBjdong() = runTest {
            // Given
            val addr = defaultKakaoAddress.copy(hCode = "")
            val doc = defaultAddressDocument.copy(address = addr)
            mockWebClientGetSingle(Mono.just(KakaoAddressResponse(defaultMeta, listOf(doc))))

            // When
            val result = client.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.BJDONG)
        }

        @Test
        @DisplayName("주소 검색 성공 (h_code만) - HADONG 1개 Location")
        fun hCodeOnly_returnsOneHadong() = runTest {
            // Given
            val addr = defaultKakaoAddress.copy(bCode = "")
            val doc = defaultAddressDocument.copy(address = addr)
            mockWebClientGetSingle(Mono.just(KakaoAddressResponse(defaultMeta, listOf(doc))))

            // When
            val result = client.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.HADONG)
        }

        @Test
        @DisplayName("주소 검색 성공 (address null) - 좌표 기반 UNKNOWN Location")
        fun nullAddress_returnsUnknownLocation() = runTest {
            // Given
            val doc = defaultAddressDocument.copy(address = null)
            mockWebClientGetSingle(Mono.just(KakaoAddressResponse(defaultMeta, listOf(doc))))

            // When
            val result = client.geocodeByAddress("서울 강남구")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.UNKNOWN)
            assertThat(result[0].address.code).isEqualTo("UNKNOWN")
            assertThat(result[0].address.addressName).isEqualTo("서울 강남구")
            assertThat(result[0].coordinate).isNotNull
            assertThat(result[0].coordinate!!.lat).isEqualTo(37.4994)
            assertThat(result[0].coordinate!!.lon).isEqualTo(127.0365)
        }

        @Test
        @DisplayName("주소 검색 결과 없음 (단일 토큰) - broader fallback 없이 빈 리스트")
        fun noResults_singleToken_returnsEmpty() = runTest {
            // Given
            mockWebClientGetSingle(Mono.just(KakaoAddressResponse(defaultMeta.copy(totalCount = 0), emptyList())))

            // When
            val result = client.geocodeByAddress("존재하지않는주소")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("주소 검색 결과 없음 (다중 토큰) - 마지막 토큰 제거 후 broader 재검색 성공")
        fun noResults_multiToken_broaderFallbackSuccess() = runTest {
            // Given
            val emptyResponse = KakaoAddressResponse(defaultMeta.copy(totalCount = 0), emptyList())
            val broaderDoc = defaultAddressDocument.copy(
                addressName = "경북 포항시 남구",
                address = defaultKakaoAddress.copy(
                    addressName = "경북 포항시 남구",
                    region1DepthName = "경상북도",
                    region2DepthName = "포항시 남구",
                    region3DepthName = "",
                    region3DepthHName = "",
                    hCode = "",
                    bCode = "4711000000"
                )
            )
            val broaderResponse = KakaoAddressResponse(defaultMeta, listOf(broaderDoc))
            mockWebClientGetSequential(Mono.just(emptyResponse), Mono.just(broaderResponse))

            // When
            val result = client.geocodeByAddress("경북 포항시 남구 호동")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.addressName).isEqualTo("경북 포항시 남구 호동")
            assertThat(result[0].address.regionType).isEqualTo(RegionType.BJDONG)
        }

        @Test
        @DisplayName("주소 검색 결과 없음 (다중 토큰) - broader 재검색도 실패 시 빈 리스트")
        fun noResults_multiToken_broaderFallbackAlsoFails() = runTest {
            // Given
            val emptyResponse = KakaoAddressResponse(defaultMeta.copy(totalCount = 0), emptyList())
            mockWebClientGetSequential(Mono.just(emptyResponse), Mono.just(emptyResponse))

            // When
            val result = client.geocodeByAddress("경북 없는군 없는면")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("API 예외 발생 시 예외가 그대로 전파된다")
        fun apiException_propagatesException() = runTest {
            // Given
            mockWebClientGetSingle(Mono.error(RuntimeException("API 연결 실패")))

            // When & Then
            assertThatThrownBy { runBlocking { client.geocodeByAddress("에러 테스트") } }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("API 연결 실패")
        }
    }

    @Nested
    @DisplayName("geocodeByKeyword")
    inner class GeocodeByKeyword {

        private val defaultKeywordDocument = KakaoKeywordDocument(
            id = "12345",
            placeName = "강남역",
            addressName = "서울 강남구 역삼동",
            roadAddressName = "서울 강남구 테헤란로 지하 156",
            x = "127.0284",
            y = "37.4979"
        )

        @Test
        @DisplayName("키워드 검색 성공 후 주소 검색으로 HADONG Location을 반환한다")
        fun keywordThenAddress_returnsHadongLocation() = runTest {
            // Given
            val keywordResponse = KakaoKeywordResponse(defaultMeta, listOf(defaultKeywordDocument))
            val addressResponse = KakaoAddressResponse(defaultMeta, listOf(defaultAddressDocument))
            mockWebClientGetSequential(Mono.just(keywordResponse), Mono.just(addressResponse))

            // When
            val result = client.geocodeByKeyword("강남역")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.HADONG)
            assertThat(result[0].address.addressName).isEqualTo("강남역")
        }

        @Test
        @DisplayName("키워드 검색 성공 + 주소 검색 실패 시 좌표 기반 UNKNOWN Location을 반환한다")
        fun keywordSuccess_addressFails_returnsCoordinateLocation() = runTest {
            // Given
            val keywordDoc = defaultKeywordDocument.copy(addressName = "서울 강남구 특수동")
            val keywordResponse = KakaoKeywordResponse(defaultMeta, listOf(keywordDoc))
            val addressResponse = KakaoAddressResponse(defaultMeta.copy(totalCount = 0), emptyList())
            mockWebClientGetSequential(Mono.just(keywordResponse), Mono.just(addressResponse))

            // When
            val result = client.geocodeByKeyword("특수 장소")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].address.regionType).isEqualTo(RegionType.UNKNOWN)
            assertThat(result[0].address.addressName).isEqualTo("특수 장소")
            assertThat(result[0].coordinate).isNotNull
        }

        @Test
        @DisplayName("키워드 검색 결과 없음 시 빈 리스트를 반환한다")
        fun keywordNoResults_returnsEmpty() = runTest {
            // Given
            mockWebClientGetSingle(Mono.just(KakaoKeywordResponse(defaultMeta.copy(totalCount = 0), emptyList())))

            // When
            val result = client.geocodeByKeyword("없는 장소")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("API 예외 발생 시 예외가 그대로 전파된다")
        fun apiException_propagatesException() = runTest {
            // Given
            mockWebClientGetSingle(Mono.error(RuntimeException("API 연결 실패")))

            // When & Then
            assertThatThrownBy { runBlocking { client.geocodeByKeyword("에러 테스트") } }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessage("API 연결 실패")
        }
    }
}
