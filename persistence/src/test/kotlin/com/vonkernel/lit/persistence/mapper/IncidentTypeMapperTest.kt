package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.core.entity.IncidentType
import com.vonkernel.lit.persistence.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IncidentTypeMapper 테스트")
class IncidentTypeMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 산불 사건 유형 변환")
    fun toDomainModel_forestFire() {
        val entity = TestFixtures.createIncidentTypeEntity(code = "forest_fire", name = "산불")
        val domain = IncidentTypeMapper.toDomainModel(entity)

        assertThat(domain.code).isEqualTo("forest_fire")
        assertThat(domain.name).isEqualTo("산불")
    }

    @Test
    @DisplayName("toDomainModel: 태풍 사건 유형 변환")
    fun toDomainModel_typhoon() {
        val entity = TestFixtures.createIncidentTypeEntity(code = "typhoon", name = "태풍")
        val domain = IncidentTypeMapper.toDomainModel(entity)

        assertThat(domain.code).isEqualTo("typhoon")
        assertThat(domain.name).isEqualTo("태풍")
    }

    @Test
    @DisplayName("toDomainModel: 홍수 사건 유형 변환")
    fun toDomainModel_flood() {
        val entity = TestFixtures.createIncidentTypeEntity(code = "flood", name = "홍수")
        val domain = IncidentTypeMapper.toDomainModel(entity)

        assertThat(domain.code).isEqualTo("flood")
        assertThat(domain.name).isEqualTo("홍수")
    }

    @Test
    @DisplayName("toDomainModel: 특수문자가 포함된 사건 유형")
    fun toDomainModel_specialCharacters() {
        val entity = TestFixtures.createIncidentTypeEntity(code = "special-case_123", name = "특수케이스")
        val domain = IncidentTypeMapper.toDomainModel(entity)

        assertThat(domain.code).isEqualTo("special-case_123")
        assertThat(domain.name).isEqualTo("특수케이스")
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 산불 사건 유형 변환")
    fun toPersistenceModel_forestFire() {
        val domain = TestFixtures.createIncidentType(code = "forest_fire", name = "산불")
        val entity = IncidentTypeMapper.toPersistenceModel(domain)

        assertThat(entity.code).isEqualTo("forest_fire")
        assertThat(entity.name).isEqualTo("산불")
    }

    @Test
    @DisplayName("toPersistenceModel: 태풍 사건 유형 변환")
    fun toPersistenceModel_typhoon() {
        val domain = TestFixtures.createIncidentType(code = "typhoon", name = "태풍")
        val entity = IncidentTypeMapper.toPersistenceModel(domain)

        assertThat(entity.code).isEqualTo("typhoon")
        assertThat(entity.name).isEqualTo("태풍")
    }

    @Test
    @DisplayName("toPersistenceModel: 홍수 사건 유형 변환")
    fun toPersistenceModel_flood() {
        val domain = TestFixtures.createIncidentType(code = "flood", name = "홍수")
        val entity = IncidentTypeMapper.toPersistenceModel(domain)

        assertThat(entity.code).isEqualTo("flood")
        assertThat(entity.name).isEqualTo("홍수")
    }

    @Test
    @DisplayName("toPersistenceModel: 긴 코드와 이름")
    fun toPersistenceModel_longCodeAndName() {
        val longCode = "very_long_incident_code_" + "x".repeat(20)
        val longName = "매우긴사건유형이름" + "y".repeat(20)
        val domain = TestFixtures.createIncidentType(code = longCode, name = longName)
        val entity = IncidentTypeMapper.toPersistenceModel(domain)

        assertThat(entity.code).isEqualTo(longCode)
        assertThat(entity.name).isEqualTo(longName)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity 불변성")
    fun roundTrip_entityToDomainToEntity() {
        val originalEntity = TestFixtures.createIncidentTypeEntity(code = "forest_fire", name = "산불")

        val domain = IncidentTypeMapper.toDomainModel(originalEntity)
        val reconvertedEntity = IncidentTypeMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.code).isEqualTo(originalEntity.code)
        assertThat(reconvertedEntity.name).isEqualTo(originalEntity.name)
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createIncidentType(code = "typhoon", name = "태풍")

        val entity = IncidentTypeMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = IncidentTypeMapper.toDomainModel(entity)

        assertThat(reconvertedDomain).isEqualTo(originalDomain)
    }

    @Test
    @DisplayName("Round-trip: 여러 사건 유형")
    fun roundTrip_multipleIncidentTypes() {
        val incidentTypes = listOf(
            IncidentType("forest_fire", "산불"),
            IncidentType("typhoon", "태풍"),
            IncidentType("flood", "홍수"),
            IncidentType("earthquake", "지진"),
            IncidentType("landslide", "산사태")
        )

        incidentTypes.forEach { originalDomain ->
            val entity = IncidentTypeMapper.toPersistenceModel(originalDomain)
            val reconvertedDomain = IncidentTypeMapper.toDomainModel(entity)

            assertThat(reconvertedDomain).isEqualTo(originalDomain)
        }
    }
}