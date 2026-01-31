package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.mapper.CoordinateMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("CoordinateMapper 테스트")
class CoordinateMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 표준 좌표 변환")
    fun toDomainModel_standardCoordinate() {
        val entity = TestFixtures.createCoordinateEntity(latitude = 37.4979, longitude = 126.9270)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isEqualTo(37.4979)
        assertThat(domain.lon).isEqualTo(126.9270)
    }

    @Test
    @DisplayName("toDomainModel: 북극 좌표")
    fun toDomainModel_northPole() {
        val entity = TestFixtures.createCoordinateEntity(latitude = 90.0, longitude = 0.0)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isEqualTo(90.0)
        assertThat(domain.lon).isEqualTo(0.0)
    }

    @Test
    @DisplayName("toDomainModel: 남극 좌표")
    fun toDomainModel_southPole() {
        val entity = TestFixtures.createCoordinateEntity(latitude = -90.0, longitude = 0.0)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isEqualTo(-90.0)
        assertThat(domain.lon).isEqualTo(0.0)
    }

    @Test
    @DisplayName("toDomainModel: 국제변경선 동쪽")
    fun toDomainModel_dateLineEast() {
        val entity = TestFixtures.createCoordinateEntity(latitude = 0.0, longitude = 180.0)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isEqualTo(0.0)
        assertThat(domain.lon).isEqualTo(180.0)
    }

    @Test
    @DisplayName("toDomainModel: 국제변경선 서쪽")
    fun toDomainModel_dateLineWest() {
        val entity = TestFixtures.createCoordinateEntity(latitude = 0.0, longitude = -180.0)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isEqualTo(0.0)
        assertThat(domain.lon).isEqualTo(-180.0)
    }

    @Test
    @DisplayName("toDomainModel: 고정밀 좌표")
    fun toDomainModel_highPrecision() {
        val entity = TestFixtures.createCoordinateEntity(latitude = 37.49791234567890, longitude = 126.92701234567890)
        val domain = CoordinateMapper.toDomainModel(entity)

        assertThat(domain.lat).isCloseTo(37.49791234567890, offset(1e-10))
        assertThat(domain.lon).isCloseTo(126.92701234567890, offset(1e-10))
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 표준 좌표 변환")
    fun toPersistenceModel_standardCoordinate() {
        val domain = TestFixtures.createCoordinate(lat = 37.4979, lon = 126.9270)
        val entity = CoordinateMapper.toPersistenceModel(domain)

        assertThat(entity.latitude).isEqualTo(37.4979)
        assertThat(entity.longitude).isEqualTo(126.9270)
    }

    @Test
    @DisplayName("toPersistenceModel: 극단값 좌표")
    fun toPersistenceModel_extremeCoordinates() {
        val domain = TestFixtures.createCoordinate(lat = 90.0, lon = 180.0)
        val entity = CoordinateMapper.toPersistenceModel(domain)

        assertThat(entity.latitude).isEqualTo(90.0)
        assertThat(entity.longitude).isEqualTo(180.0)
    }

    @Test
    @DisplayName("toPersistenceModel: 음수 좌표")
    fun toPersistenceModel_negativeCoordinates() {
        val domain = TestFixtures.createCoordinate(lat = -45.5, lon = -90.5)
        val entity = CoordinateMapper.toPersistenceModel(domain)

        assertThat(entity.latitude).isEqualTo(-45.5)
        assertThat(entity.longitude).isEqualTo(-90.5)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity 정확도 유지")
    fun roundTrip_entityToDomainToEntity() {
        val originalEntity = TestFixtures.createCoordinateEntity(latitude = 37.4979, longitude = 126.9270)

        val domain = CoordinateMapper.toDomainModel(originalEntity)
        val reconvertedEntity = CoordinateMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.latitude).isCloseTo(originalEntity.latitude, offset(1e-15))
        assertThat(reconvertedEntity.longitude).isCloseTo(originalEntity.longitude, offset(1e-15))
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 정확도 유지")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createCoordinate(lat = 37.49791234567890, lon = 126.92701234567890)

        val entity = CoordinateMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = CoordinateMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.lat).isCloseTo(originalDomain.lat, offset(1e-15))
        assertThat(reconvertedDomain.lon).isCloseTo(originalDomain.lon, offset(1e-15))
    }

    @Test
    @DisplayName("Round-trip: 극단값 정확도 유지")
    fun roundTrip_extremeValuesAccuracy() {
        val originalDomain = TestFixtures.createCoordinate(lat = 90.0, lon = 180.0)

        val entity = CoordinateMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = CoordinateMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.lat).isEqualTo(90.0)
        assertThat(reconvertedDomain.lon).isEqualTo(180.0)
    }

    @Test
    @DisplayName("Round-trip: 0.0 값 정확도 유지")
    fun roundTrip_zeroValuesAccuracy() {
        val originalDomain = TestFixtures.createCoordinate(lat = 0.0, lon = 0.0)

        val entity = CoordinateMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = CoordinateMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.lat).isEqualTo(0.0)
        assertThat(reconvertedDomain.lon).isEqualTo(0.0)
    }
}