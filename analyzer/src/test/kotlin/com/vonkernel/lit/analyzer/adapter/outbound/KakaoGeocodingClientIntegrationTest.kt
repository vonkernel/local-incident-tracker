package com.vonkernel.lit.analyzer.adapter.outbound

import com.vonkernel.lit.analyzer.adapter.outbound.geocoding.KakaoGeocodingClient
import com.vonkernel.lit.core.entity.RegionType
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

@Tag("integration")
@DisplayName("KakaoGeocodingClient 통합 테스트")
class KakaoGeocodingClientIntegrationTest {

    private lateinit var client: KakaoGeocodingClient

    @BeforeEach
    fun setUp() {
        val apiKey = System.getenv("KAKAO_REST_API_KEY")
            ?: throw IllegalStateException("KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다")

        val webClient = WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .defaultHeader("Authorization", "KakaoAK $apiKey")
            .build()

        client = KakaoGeocodingClient(webClient)
    }

    @Test
    @DisplayName("geocodeByAddress - 실제 주소 검색이 동작한다")
    fun geocodeByAddress_realApiCall() = runTest {
        // When
        val result = client.geocodeByAddress("서울특별시 강남구 역삼동")

        // Then
        assertThat(result).isNotEmpty
        result.forEach { location ->
            assertThat(location.coordinate).isNotNull
            assertThat(location.address.addressName).isEqualTo("서울특별시 강남구 역삼동")
            // 아래 내용은 주소 체계 개편에 따라 실패할 수 있음.
            assertThat(location.address.depth1Name).isEqualTo("서울")
            assertThat(location.address.depth2Name).isEqualTo("강남구")
            assertThat(location.address.depth3Name).isEqualTo("역삼동")
            assertThat(location.address.regionType).isEqualTo(RegionType.BJDONG)
            assertThat(location.address.code).isEqualTo("1168010100")
            assertThat(location.coordinate?.lat).isCloseTo(37.4953666908087, Offset.offset(0.0001))
            assertThat(location.coordinate?.lon).isCloseTo(127.03306536185, Offset.offset(0.0001))
        }
    }

    @Test
    @DisplayName("geocodeByKeyword - 실제 키워드 검색이 동작한다")
    fun geocodeByKeyword_realApiCall() = runTest {
        // When
        val result = client.geocodeByKeyword("강남역")

        // Then
        assertThat(result).isNotEmpty
        result.forEach { location ->
            assertThat(location.coordinate).isNotNull
            assertThat(location.address.addressName).isEqualTo("강남역")
            // 아래 내용은 주소 체계 개편에 따라 실패할 수 있음.
            assertThat(location.address.depth1Name).isEqualTo("서울")
            assertThat(location.address.depth2Name).isEqualTo("강남구")
            assertThat(location.address.depth3Name).isEqualTo("역삼1동")
            assertThat(location.address.regionType).isEqualTo(RegionType.HADONG)
            assertThat(location.address.code).isEqualTo("1168064000")
            assertThat(location.coordinate?.lat).isCloseTo(37.4970572543978, Offset.offset(0.0001))
            assertThat(location.coordinate?.lon).isCloseTo(127.028180714381, Offset.offset(0.0001))
        }
    }

    @Test
    @DisplayName("geocodeByAddress - 존재하지 않는 주소는 빈 리스트를 반환한다")
    fun geocodeByAddress_nonExistent_returnsEmpty() = runTest {
        // When
        val result = client.geocodeByAddress("존재하지않는주소12345xyz")

        // Then
        assertThat(result).isEmpty()
    }
}
