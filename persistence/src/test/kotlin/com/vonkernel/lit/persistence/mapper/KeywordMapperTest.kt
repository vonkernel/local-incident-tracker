package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.Keyword
import com.vonkernel.lit.persistence.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("KeywordMapper 테스트")
class KeywordMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 높은 우선순위 키워드 변환")
    fun toDomainModel_highPriorityKeyword() {
        val entity = TestFixtures.createKeywordEntity(keyword = "화재", priority = 10)
        val domain = KeywordMapper.toDomainModel(entity)

        assertThat(domain.keyword).isEqualTo("화재")
        assertThat(domain.priority).isEqualTo(10)
    }

    @Test
    @DisplayName("toDomainModel: 중간 우선순위 키워드 변환")
    fun toDomainModel_mediumPriorityKeyword() {
        val entity = TestFixtures.createKeywordEntity(keyword = "대피", priority = 5)
        val domain = KeywordMapper.toDomainModel(entity)

        assertThat(domain.keyword).isEqualTo("대피")
        assertThat(domain.priority).isEqualTo(5)
    }

    @Test
    @DisplayName("toDomainModel: 낮은 우선순위 키워드 변환")
    fun toDomainModel_lowPriorityKeyword() {
        val entity = TestFixtures.createKeywordEntity(keyword = "예보", priority = 1)
        val domain = KeywordMapper.toDomainModel(entity)

        assertThat(domain.keyword).isEqualTo("예보")
        assertThat(domain.priority).isEqualTo(1)
    }

    @Test
    @DisplayName("toDomainModel: 특수문자가 포함된 키워드")
    fun toDomainModel_specialCharactersKeyword() {
        val entity = TestFixtures.createKeywordEntity(keyword = "@#$%&", priority = 3)
        val domain = KeywordMapper.toDomainModel(entity)

        assertThat(domain.keyword).isEqualTo("@#$%&")
        assertThat(domain.priority).isEqualTo(3)
    }

    @Test
    @DisplayName("toDomainModel: 긴 문자열 키워드")
    fun toDomainModel_longStringKeyword() {
        val longKeyword = "x".repeat(500)
        val entity = TestFixtures.createKeywordEntity(keyword = longKeyword, priority = 7)
        val domain = KeywordMapper.toDomainModel(entity)

        assertThat(domain.keyword).isEqualTo(longKeyword)
        assertThat(domain.priority).isEqualTo(7)
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 높은 우선순위 키워드 변환")
    fun toPersistenceModel_highPriorityKeyword() {
        val domain = TestFixtures.createKeyword(keyword = "화재", priority = 10)
        val entity = KeywordMapper.toPersistenceModel(domain)

        assertThat(entity.keyword).isEqualTo("화재")
        assertThat(entity.priority).isEqualTo(10)
    }

    @Test
    @DisplayName("toPersistenceModel: 중간 우선순위 키워드 변환")
    fun toPersistenceModel_mediumPriorityKeyword() {
        val domain = TestFixtures.createKeyword(keyword = "대피", priority = 5)
        val entity = KeywordMapper.toPersistenceModel(domain)

        assertThat(entity.keyword).isEqualTo("대피")
        assertThat(entity.priority).isEqualTo(5)
    }

    @Test
    @DisplayName("toPersistenceModel: 특수문자 키워드")
    fun toPersistenceModel_specialCharactersKeyword() {
        val domain = TestFixtures.createKeyword(keyword = "@#$%&", priority = 3)
        val entity = KeywordMapper.toPersistenceModel(domain)

        assertThat(entity.keyword).isEqualTo("@#$%&")
        assertThat(entity.priority).isEqualTo(3)
    }

    @Test
    @DisplayName("toPersistenceModel: 음수 우선순위")
    fun toPersistenceModel_negativePriority() {
        val domain = TestFixtures.createKeyword(keyword = "test", priority = -5)
        val entity = KeywordMapper.toPersistenceModel(domain)

        assertThat(entity.keyword).isEqualTo("test")
        assertThat(entity.priority).isEqualTo(-5)
    }

    @Test
    @DisplayName("toPersistenceModel: 0 우선순위")
    fun toPersistenceModel_zeroPriority() {
        val domain = TestFixtures.createKeyword(keyword = "keyword", priority = 0)
        val entity = KeywordMapper.toPersistenceModel(domain)

        assertThat(entity.keyword).isEqualTo("keyword")
        assertThat(entity.priority).isEqualTo(0)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity 불변성")
    fun roundTrip_entityToDomainToEntity() {
        val originalEntity = TestFixtures.createKeywordEntity(keyword = "화재", priority = 10)

        val domain = KeywordMapper.toDomainModel(originalEntity)
        val reconvertedEntity = KeywordMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.keyword).isEqualTo(originalEntity.keyword)
        assertThat(reconvertedEntity.priority).isEqualTo(originalEntity.priority)
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createKeyword(keyword = "대피", priority = 5)

        val entity = KeywordMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = KeywordMapper.toDomainModel(entity)

        assertThat(reconvertedDomain).isEqualTo(originalDomain)
    }

    @Test
    @DisplayName("Round-trip: 여러 키워드")
    fun roundTrip_multipleKeywords() {
        val keywords = listOf(
            Keyword("화재", 10),
            Keyword("대피", 8),
            Keyword("경고", 5),
            Keyword("예보", 1),
            Keyword("태풍", 9)
        )

        keywords.forEach { originalDomain ->
            val entity = KeywordMapper.toPersistenceModel(originalDomain)
            val reconvertedDomain = KeywordMapper.toDomainModel(entity)

            assertThat(reconvertedDomain).isEqualTo(originalDomain)
        }
    }

    @Test
    @DisplayName("Round-trip: 특수문자 포함 키워드")
    fun roundTrip_specialCharacterKeywords() {
        val specialKeywords = listOf(
            Keyword("@#$%&", 5),
            Keyword("한글테스트", 8),
            Keyword("mix-한글_special", 3)
        )

        specialKeywords.forEach { originalDomain ->
            val entity = KeywordMapper.toPersistenceModel(originalDomain)
            val reconvertedDomain = KeywordMapper.toDomainModel(entity)

            assertThat(reconvertedDomain).isEqualTo(originalDomain)
        }
    }
}