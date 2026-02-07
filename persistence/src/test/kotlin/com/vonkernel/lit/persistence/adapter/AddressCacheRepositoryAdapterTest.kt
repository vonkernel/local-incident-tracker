package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.JpaAddressRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AddressCacheRepositoryAdapter::class)
@DisplayName("AddressCacheRepositoryAdapter 테스트")
class AddressCacheRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: AddressCacheRepositoryAdapter

    @Autowired
    private lateinit var jpaRepository: JpaAddressRepository

    @Test
    @DisplayName("findByAddressName: 저장된 주소명으로 조회 시 도메인 Location을 반환한다")
    fun findByAddressName_existingAddress_returnsLocation() {
        // Given
        val entity = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "1168010600",
            addressName = "서울특별시 강남구",
            depth1Name = "서울특별시",
            depth2Name = "강남구",
            depth3Name = "역삼동"
        )
        jpaRepository.save(entity)

        // When
        val result = adapter.findByAddressName("서울특별시 강남구")

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.address.addressName).isEqualTo("서울특별시 강남구")
        assertThat(result.address.regionType).isEqualTo(RegionType.BJDONG)
        assertThat(result.address.code).isEqualTo("1168010600")
        assertThat(result.address.depth1Name).isEqualTo("서울특별시")
        assertThat(result.address.depth2Name).isEqualTo("강남구")
        assertThat(result.address.depth3Name).isEqualTo("역삼동")
    }

    @Test
    @DisplayName("findByAddressName: 좌표가 있는 주소 조회 시 좌표 포함 Location을 반환한다")
    fun findByAddressName_withCoordinate_returnsLocationWithCoordinate() {
        // Given
        val entity = TestFixtures.createAddressEntity(
            regionType = "H",
            code = "1168010100",
            addressName = "서울특별시 강남구 역삼1동"
        ).apply {
            coordinate = TestFixtures.createCoordinateEntity(
                latitude = 37.4994,
                longitude = 127.0365
            ).also { it.address = this }
        }
        jpaRepository.save(entity)

        // When
        val result = adapter.findByAddressName("서울특별시 강남구 역삼1동")

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.coordinate).isNotNull
        assertThat(result.coordinate!!.lat).isEqualTo(37.4994)
        assertThat(result.coordinate!!.lon).isEqualTo(127.0365)
        assertThat(result.address.regionType).isEqualTo(RegionType.HADONG)
    }

    @Test
    @DisplayName("findByAddressName: 존재하지 않는 주소명 조회 시 null을 반환한다")
    fun findByAddressName_nonExistingAddress_returnsNull() {
        // When
        val result = adapter.findByAddressName("존재하지않는주소")

        // Then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("findByAddressName: 여러 주소 중 정확한 주소명으로 조회한다")
    fun findByAddressName_multipleAddresses_returnsCorrectOne() {
        // Given
        jpaRepository.saveAll(listOf(
            TestFixtures.createAddressEntity(
                regionType = "B", code = "1168010600",
                addressName = "서울 강남구", depth1Name = "서울특별시", depth2Name = "강남구"
            ),
            TestFixtures.createAddressEntity(
                regionType = "B", code = "1165010100",
                addressName = "서울 서초구", depth1Name = "서울특별시", depth2Name = "서초구"
            )
        ))

        // When
        val result = adapter.findByAddressName("서울 서초구")

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.address.addressName).isEqualTo("서울 서초구")
        assertThat(result.address.code).isEqualTo("1165010100")
    }
}
