package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.core.entity.Urgency
import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.mapper.UrgencyMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UrgencyMapper 테스트")
class UrgencyMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 낮은 긴급도 변환")
    fun toDomainModel_lowUrgency() {
        val entity = TestFixtures.createUrgencyEntity(name = "LOW", level = 1)
        val domain = UrgencyMapper.toDomainModel(entity)

        assertThat(domain.name).isEqualTo("LOW")
        assertThat(domain.level).isEqualTo(1)
    }

    @Test
    @DisplayName("toDomainModel: 중간 긴급도 변환")
    fun toDomainModel_mediumUrgency() {
        val entity = TestFixtures.createUrgencyEntity(name = "MEDIUM", level = 2)
        val domain = UrgencyMapper.toDomainModel(entity)

        assertThat(domain.name).isEqualTo("MEDIUM")
        assertThat(domain.level).isEqualTo(2)
    }

    @Test
    @DisplayName("toDomainModel: 높은 긴급도 변환")
    fun toDomainModel_highUrgency() {
        val entity = TestFixtures.createUrgencyEntity(name = "HIGH", level = 3)
        val domain = UrgencyMapper.toDomainModel(entity)

        assertThat(domain.name).isEqualTo("HIGH")
        assertThat(domain.level).isEqualTo(3)
    }

    @Test
    @DisplayName("toDomainModel: 특수 긴급도명 변환")
    fun toDomainModel_customUrgencyName() {
        val entity = TestFixtures.createUrgencyEntity(name = "CRITICAL_ALERT", level = 99)
        val domain = UrgencyMapper.toDomainModel(entity)

        assertThat(domain.name).isEqualTo("CRITICAL_ALERT")
        assertThat(domain.level).isEqualTo(99)
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 낮은 긴급도 변환")
    fun toPersistenceModel_lowUrgency() {
        val domain = TestFixtures.createUrgency(name = "LOW", level = 1)
        val entity = UrgencyMapper.toPersistenceModel(domain)

        assertThat(entity.name).isEqualTo("LOW")
        assertThat(entity.level).isEqualTo(1)
    }

    @Test
    @DisplayName("toPersistenceModel: 중간 긴급도 변환")
    fun toPersistenceModel_mediumUrgency() {
        val domain = TestFixtures.createUrgency(name = "MEDIUM", level = 2)
        val entity = UrgencyMapper.toPersistenceModel(domain)

        assertThat(entity.name).isEqualTo("MEDIUM")
        assertThat(entity.level).isEqualTo(2)
    }

    @Test
    @DisplayName("toPersistenceModel: 높은 긴급도 변환")
    fun toPersistenceModel_highUrgency() {
        val domain = TestFixtures.createUrgency(name = "HIGH", level = 3)
        val entity = UrgencyMapper.toPersistenceModel(domain)

        assertThat(entity.name).isEqualTo("HIGH")
        assertThat(entity.level).isEqualTo(3)
    }

    @Test
    @DisplayName("toPersistenceModel: 음수 레벨 변환")
    fun toPersistenceModel_negativeLevel() {
        val domain = TestFixtures.createUrgency(name = "NEGATIVE", level = -5)
        val entity = UrgencyMapper.toPersistenceModel(domain)

        assertThat(entity.name).isEqualTo("NEGATIVE")
        assertThat(entity.level).isEqualTo(-5)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity 불변성")
    fun roundTrip_entityToDomainToEntity() {
        val originalEntity = TestFixtures.createUrgencyEntity(name = "HIGH", level = 3)

        val domain = UrgencyMapper.toDomainModel(originalEntity)
        val reconvertedEntity = UrgencyMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.name).isEqualTo(originalEntity.name)
        assertThat(reconvertedEntity.level).isEqualTo(originalEntity.level)
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createUrgency(name = "MEDIUM", level = 2)

        val entity = UrgencyMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = UrgencyMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.name).isEqualTo(originalDomain.name)
        assertThat(reconvertedDomain.level).isEqualTo(originalDomain.level)
    }

    @Test
    @DisplayName("Round-trip: 모든 표준 긴급도")
    fun roundTrip_allStandardUrgencies() {
        val standardUrgencies = listOf(
            Urgency("LOW", 1),
            Urgency("MEDIUM", 2),
            Urgency("HIGH", 3)
        )

        standardUrgencies.forEach { originalDomain ->
            val entity = UrgencyMapper.toPersistenceModel(originalDomain)
            val reconvertedDomain = UrgencyMapper.toDomainModel(entity)

            assertThat(reconvertedDomain).isEqualTo(originalDomain)
        }
    }
}