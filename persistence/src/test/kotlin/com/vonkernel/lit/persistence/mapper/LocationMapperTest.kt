package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.RegionType
import com.vonkernel.lit.persistence.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LocationMapper 테스트")
class LocationMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: coordinate 매핑 - CoordinateMapper 호출 확인")
    fun toDomainModel_coordinateMapping() {
        val coordinateEntity = TestFixtures.createCoordinateEntity(latitude = 37.4979, longitude = 126.9270)
        val entity = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "11110"
        ).apply {
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(entity)

        assertThat(domain.coordinate.lat).isEqualTo(37.4979)
        assertThat(domain.coordinate.lon).isEqualTo(126.9270)
    }

    @Test
    @DisplayName("toDomainModel: BJDONG regionType 매핑")
    fun toDomainModel_bjdongRegionType() {
        val entity = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "11110",
            addressName = "서울특별시 강남구",
            depth1Name = "서울특별시",
            depth2Name = "강남구",
            depth3Name = "강남동"
        ).apply {
            val coordinateEntity = TestFixtures.createCoordinateEntity()
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(entity)

        assertThat(domain.address.regionType).isEqualTo(RegionType.BJDONG)
        assertThat(domain.address.code).isEqualTo("11110")
        assertThat(domain.address.addressName).isEqualTo("서울특별시 강남구")
        assertThat(domain.address.depth1Name).isEqualTo("서울특별시")
        assertThat(domain.address.depth2Name).isEqualTo("강남구")
        assertThat(domain.address.depth3Name).isEqualTo("강남동")
    }

    @Test
    @DisplayName("toDomainModel: HADONG regionType 매핑 - depth 일부 null")
    fun toDomainModel_hadongRegionType() {
        val entity = TestFixtures.createAddressEntity(
            regionType = "H",
            code = "11120",
            addressName = "서울특별시 마포구",
            depth1Name = "서울특별시",
            depth2Name = "마포구",
            depth3Name = null
        ).apply {
            val coordinateEntity = TestFixtures.createCoordinateEntity()
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(entity)

        assertThat(domain.address.regionType).isEqualTo(RegionType.HADONG)
        assertThat(domain.address.code).isEqualTo("11120")
        assertThat(domain.address.depth1Name).isEqualTo("서울특별시")
        assertThat(domain.address.depth2Name).isEqualTo("마포구")
        assertThat(domain.address.depth3Name).isNull()
    }

    @Test
    @DisplayName("toDomainModel: UNKNOWN regionType 매핑 - depth 모두 null")
    fun toDomainModel_unknownRegionType() {
        val entity = TestFixtures.createAddressEntity(
            regionType = "U",
            code = "00000",
            addressName = "알 수 없는 주소",
            depth1Name = null,
            depth2Name = null,
            depth3Name = null
        ).apply {
            val coordinateEntity = TestFixtures.createCoordinateEntity()
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(entity)

        assertThat(domain.address.regionType).isEqualTo(RegionType.UNKNOWN)
        assertThat(domain.address.depth1Name).isNull()
        assertThat(domain.address.depth2Name).isNull()
        assertThat(domain.address.depth3Name).isNull()
    }

    @Test
    @DisplayName("toDomainModel: 미지의 regionType 코드 - UNKNOWN으로 폴백")
    fun toDomainModel_unknownCodeFallback() {
        val entity = TestFixtures.createAddressEntity(
            regionType = "X",
            code = "99999"
        ).apply {
            val coordinateEntity = TestFixtures.createCoordinateEntity()
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(entity)

        assertThat(domain.address.regionType).isEqualTo(RegionType.UNKNOWN)
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: CoordinateEntity 생성")
    fun toPersistenceModel_coordinateEntityCreation() {
        val domain = TestFixtures.createLocation(
            coordinate = TestFixtures.createCoordinate(lat = 37.4979, lon = 126.9270)
        )

        val entity = LocationMapper.toPersistenceModel(domain)

        assertThat(entity.coordinate).isNotNull()
        assertThat(entity.coordinate!!.latitude).isEqualTo(37.4979)
        assertThat(entity.coordinate!!.longitude).isEqualTo(126.9270)
    }

    @Test
    @DisplayName("toPersistenceModel: AddressEntity 생성 - RegionType.code 변환")
    fun toPersistenceModel_addressEntityCreation() {
        val domain = TestFixtures.createLocation(
            address = TestFixtures.createAddress(
                regionType = RegionType.BJDONG,
                code = "11110",
                addressName = "서울특별시 강남구",
                depth1Name = "서울특별시",
                depth2Name = "강남구",
                depth3Name = "강남동"
            )
        )

        val entity = LocationMapper.toPersistenceModel(domain)

        assertThat(entity.regionType).isEqualTo("B")
        assertThat(entity.code).isEqualTo("11110")
        assertThat(entity.addressName).isEqualTo("서울특별시 강남구")
        assertThat(entity.depth1Name).isEqualTo("서울특별시")
        assertThat(entity.depth2Name).isEqualTo("강남구")
        assertThat(entity.depth3Name).isEqualTo("강남동")
    }

    @Test
    @DisplayName("toPersistenceModel: 양방향 관계 설정 - @MapsId 검증")
    fun toPersistenceModel_bidirectionalRelationship() {
        val domain = TestFixtures.createLocation()

        val entity = LocationMapper.toPersistenceModel(domain)

        assertThat(entity.coordinate).isNotNull()
        assertThat(entity.coordinate!!.address).isEqualTo(entity)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity - RegionType 일치")
    fun roundTrip_entityToDomainToEntity_regionType() {
        val coordinateEntity = TestFixtures.createCoordinateEntity(latitude = 37.4979, longitude = 126.9270)
        val originalEntity = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "11110",
            addressName = "서울특별시 강남구",
            depth1Name = "서울특별시",
            depth2Name = "강남구",
            depth3Name = "강남동"
        ).apply {
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(originalEntity)
        val reconvertedEntity = LocationMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.regionType).isEqualTo(originalEntity.regionType)
        assertThat(reconvertedEntity.code).isEqualTo(originalEntity.code)
        assertThat(reconvertedEntity.addressName).isEqualTo(originalEntity.addressName)
        assertThat(reconvertedEntity.depth1Name).isEqualTo(originalEntity.depth1Name)
        assertThat(reconvertedEntity.depth2Name).isEqualTo(originalEntity.depth2Name)
        assertThat(reconvertedEntity.depth3Name).isEqualTo(originalEntity.depth3Name)
    }

    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity - coordinate 정확도 유지")
    fun roundTrip_entityToDomainToEntity_coordinateAccuracy() {
        val coordinateEntity = TestFixtures.createCoordinateEntity(latitude = 37.49791234567890, longitude = 126.92701234567890)
        val originalEntity = TestFixtures.createAddressEntity(regionType = "B").apply {
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val domain = LocationMapper.toDomainModel(originalEntity)
        val reconvertedEntity = LocationMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.coordinate!!.latitude).isCloseTo(originalEntity.coordinate!!.latitude, offset(1e-15))
        assertThat(reconvertedEntity.coordinate!!.longitude).isCloseTo(originalEntity.coordinate!!.longitude, offset(1e-15))
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain - 모든 필드 일치")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createLocation(
            coordinate = TestFixtures.createCoordinate(lat = 37.4979, lon = 126.9270),
            address = TestFixtures.createAddress(
                regionType = RegionType.BJDONG,
                code = "11110",
                addressName = "서울특별시 강남구",
                depth1Name = "서울특별시",
                depth2Name = "강남구",
                depth3Name = "강남동"
            )
        )

        val entity = LocationMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = LocationMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.coordinate.lat).isEqualTo(originalDomain.coordinate.lat)
        assertThat(reconvertedDomain.coordinate.lon).isEqualTo(originalDomain.coordinate.lon)
        assertThat(reconvertedDomain.address.regionType).isEqualTo(originalDomain.address.regionType)
        assertThat(reconvertedDomain.address.code).isEqualTo(originalDomain.address.code)
        assertThat(reconvertedDomain.address.addressName).isEqualTo(originalDomain.address.addressName)
        assertThat(reconvertedDomain.address.depth1Name).isEqualTo(originalDomain.address.depth1Name)
        assertThat(reconvertedDomain.address.depth2Name).isEqualTo(originalDomain.address.depth2Name)
        assertThat(reconvertedDomain.address.depth3Name).isEqualTo(originalDomain.address.depth3Name)
    }

    @Test
    @DisplayName("Round-trip: depth 필드 null 유지")
    fun roundTrip_nullDepthFieldsPreserved() {
        val originalDomain = TestFixtures.createLocation(
            address = TestFixtures.createAddress(
                regionType = RegionType.UNKNOWN,
                depth1Name = null,
                depth2Name = null,
                depth3Name = null
            )
        )

        val entity = LocationMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = LocationMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.address.depth1Name).isNull()
        assertThat(reconvertedDomain.address.depth2Name).isNull()
        assertThat(reconvertedDomain.address.depth3Name).isNull()
    }

    @Test
    @DisplayName("Round-trip: HADONG 타입 - depth 일부 null 유지")
    fun roundTrip_hadongPartialNullDepths() {
        val originalDomain = TestFixtures.createLocation(
            address = TestFixtures.createAddress(
                regionType = RegionType.HADONG,
                depth1Name = "서울특별시",
                depth2Name = "마포구",
                depth3Name = null
            )
        )

        val entity = LocationMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = LocationMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.address.regionType).isEqualTo(RegionType.HADONG)
        assertThat(reconvertedDomain.address.depth1Name).isEqualTo("서울특별시")
        assertThat(reconvertedDomain.address.depth2Name).isEqualTo("마포구")
        assertThat(reconvertedDomain.address.depth3Name).isNull()
    }
}