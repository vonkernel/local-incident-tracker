package com.vonkernel.lit.analyzer.adapter.outbound

import com.vonkernel.lit.persistence.jpa.JpaAddressRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

@Tag("integration")
@DisplayName("KakaoGeocodingAdapter 통합 테스트")
class KakaoGeocodingAdapterIntegrationTest {

    private val jpaAddressRepository: JpaAddressRepository = mockk()

    private lateinit var adapter: KakaoGeocodingAdapter

    @BeforeEach
    fun setUp() {
        val apiKey = System.getenv("KAKAO_REST_API_KEY")
            ?: throw IllegalStateException("KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다")

        val webClient = WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .defaultHeader("Authorization", "KakaoAK $apiKey")
            .build()

        adapter = KakaoGeocodingAdapter(webClient, jpaAddressRepository)

        // 캐시 miss로 항상 API 호출하도록 설정
        every { jpaAddressRepository.findFirstByAddressName(any()) } returns null
    }

    @Test
    @DisplayName("geocodeByAddress - 실제 주소 검색이 동작한다")
    fun geocodeByAddress_realApiCall() = runTest {
        // When
        val result = adapter.geocodeByAddress("서울특별시 강남구 역삼동")

        // Then
        assertThat(result).isNotEmpty
        result.forEach { location ->
            assertThat(location.coordinate).isNotNull
            assertThat(location.address.addressName).isEqualTo("서울특별시 강남구 역삼동")
            println("  ${location.address.addressName}: type=${location.address.regionType}, code=${location.address.code}, " +
                "lat=${location.coordinate?.lat}, lon=${location.coordinate?.lon}, depth1=${location.address.depth1Name}, " +
                "depth2=${location.address.depth2Name}, depth3=${location.address.depth3Name}")
        }
    }

    @Test
    @DisplayName("geocodeByKeyword - 실제 키워드 검색이 동작한다")
    fun geocodeByKeyword_realApiCall() = runTest {
        // When
        val result = adapter.geocodeByKeyword("강남역")

        // Then
        assertThat(result).isNotEmpty
        result.forEach { location ->
            assertThat(location.coordinate).isNotNull
            assertThat(location.address.addressName).isEqualTo("강남역")
            println("  ${location.address.addressName}: type=${location.address.regionType}, code=${location.address.code}, " +
                    "lat=${location.coordinate?.lat}, lon=${location.coordinate?.lon}, depth1=${location.address.depth1Name}, " +
                    "depth2=${location.address.depth2Name}, depth3=${location.address.depth3Name}")
        }
    }

    @Test
    @DisplayName("geocodeByAddress - 존재하지 않는 주소는 빈 리스트를 반환한다")
    fun geocodeByAddress_nonExistent_returnsEmpty() = runTest {
        // When
        val result = adapter.geocodeByAddress("존재하지않는주소12345xyz")

        // Then
        assertThat(result).isEmpty()
    }
}
