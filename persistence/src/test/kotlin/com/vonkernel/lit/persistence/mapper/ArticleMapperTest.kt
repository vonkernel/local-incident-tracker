package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.persistence.TestFixtures
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

@DisplayName("ArticleMapper 테스트")
class ArticleMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 기본 필드 매핑")
    fun toDomainModel_basicFieldMapping() {
        val entity = TestFixtures.createArticleEntity(
            articleId = "article-123",
            originId = "origin-456",
            sourceId = "yonhapnews",
            title = "테스트 기사 제목",
            content = "테스트 기사 본문",
            sourceUrl = "https://example.com/article"
        )
        val domain = ArticleMapper.toDomainModel(entity)

        assertThat(domain.articleId).isEqualTo("article-123")
        assertThat(domain.originId).isEqualTo("origin-456")
        assertThat(domain.sourceId).isEqualTo("yonhapnews")
        assertThat(domain.title).isEqualTo("테스트 기사 제목")
        assertThat(domain.content).isEqualTo("테스트 기사 본문")
        assertThat(domain.sourceUrl).isEqualTo("https://example.com/article")
    }

    @Test
    @DisplayName("toDomainModel: null sourceUrl 처리")
    fun toDomainModel_nullSourceUrl() {
        val entity = TestFixtures.createArticleEntity(
            articleId = "article-123",
            sourceUrl = null
        )
        val domain = ArticleMapper.toDomainModel(entity)

        assertThat(domain.sourceUrl).isNull()
    }

    @Test
    @DisplayName("toDomainModel: ZonedDateTime → Instant 변환")
    fun toDomainModel_timeFieldConversion() {
        val writtenAt = ZonedDateTime.of(2023, 1, 15, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val modifiedAt = ZonedDateTime.of(2023, 1, 16, 14, 45, 0, 0, ZoneId.of("Asia/Seoul"))

        val entity = TestFixtures.createArticleEntity(
            writtenAt = writtenAt,
            modifiedAt = modifiedAt
        )
        val domain = ArticleMapper.toDomainModel(entity)

        assertThat(domain.writtenAt).isEqualTo(writtenAt.toInstant())
        assertThat(domain.modifiedAt).isEqualTo(modifiedAt.toInstant())
    }

    @Test
    @DisplayName("toDomainModel: 오래된 기사 시간 변환")
    fun toDomainModel_oldArticleTimeConversion() {
        val writtenAt = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
        val entity = TestFixtures.createArticleEntity(writtenAt = writtenAt)
        val domain = ArticleMapper.toDomainModel(entity)

        assertThat(domain.writtenAt).isEqualTo(writtenAt.toInstant())
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 기본 필드 매핑")
    fun toPersistenceModel_basicFieldMapping() {
        val domain = TestFixtures.createArticle(
            articleId = "article-123",
            originId = "origin-456",
            sourceId = "yonhapnews",
            title = "테스트 기사 제목",
            content = "테스트 기사 본문",
            sourceUrl = "https://example.com/article"
        )
        val entity = ArticleMapper.toPersistenceModel(domain)

        assertThat(entity.articleId).isEqualTo("article-123")
        assertThat(entity.originId).isEqualTo("origin-456")
        assertThat(entity.sourceId).isEqualTo("yonhapnews")
        assertThat(entity.title).isEqualTo("테스트 기사 제목")
        assertThat(entity.content).isEqualTo("테스트 기사 본문")
        assertThat(entity.sourceUrl).isEqualTo("https://example.com/article")
    }

    @Test
    @DisplayName("toPersistenceModel: Instant → ZonedDateTime 시스템 기본 시간대 변환")
    fun toPersistenceModel_systemDefaultTimeZoneConversion() {
        val writtenAt = Instant.parse("2023-01-15T10:30:00Z")
        val modifiedAt = Instant.parse("2023-01-16T14:45:00Z")

        val domain = TestFixtures.createArticle(
            writtenAt = writtenAt,
            modifiedAt = modifiedAt
        )
        val entity = ArticleMapper.toPersistenceModel(domain)

        val expectedWrittenAt = ZonedDateTime.ofInstant(writtenAt, ZoneId.systemDefault())
        val expectedModifiedAt = ZonedDateTime.ofInstant(modifiedAt, ZoneId.systemDefault())

        assertThat(entity.writtenAt).isEqualTo(expectedWrittenAt)
        assertThat(entity.modifiedAt).isEqualTo(expectedModifiedAt)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Entity → Domain → Entity 불변성")
    fun roundTrip_entityToDomainToEntity() {
        val originalEntity = TestFixtures.createArticleEntity(
            articleId = "article-123",
            originId = "origin-456",
            sourceId = "yonhapnews",
            writtenAt = ZonedDateTime.now(),
            modifiedAt = ZonedDateTime.now(),
            title = "테스트 기사",
            content = "테스트 내용",
            sourceUrl = "https://example.com/article"
        )

        val domain = ArticleMapper.toDomainModel(originalEntity)
        val reconvertedEntity = ArticleMapper.toPersistenceModel(domain)

        assertThat(reconvertedEntity.articleId).isEqualTo(originalEntity.articleId)
        assertThat(reconvertedEntity.originId).isEqualTo(originalEntity.originId)
        assertThat(reconvertedEntity.sourceId).isEqualTo(originalEntity.sourceId)
        assertThat(reconvertedEntity.title).isEqualTo(originalEntity.title)
        assertThat(reconvertedEntity.content).isEqualTo(originalEntity.content)
        assertThat(reconvertedEntity.sourceUrl).isEqualTo(originalEntity.sourceUrl)

        // 시간 필드는 milliseconds 단위로 검증
        assertThat(reconvertedEntity.writtenAt.toInstant().toEpochMilli())
            .isEqualTo(originalEntity.writtenAt.toInstant().toEpochMilli())
        assertThat(reconvertedEntity.modifiedAt.toInstant().toEpochMilli())
            .isEqualTo(originalEntity.modifiedAt.toInstant().toEpochMilli())
    }

    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun roundTrip_domainToEntityToDomain() {
        val originalDomain = TestFixtures.createArticle(
            articleId = "article-123",
            originId = "origin-456",
            sourceId = "yonhapnews",
            writtenAt = Instant.now(),
            modifiedAt = Instant.now(),
            title = "테스트 기사",
            content = "테스트 내용",
            sourceUrl = "https://example.com/article"
        )

        val entity = ArticleMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = ArticleMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.articleId).isEqualTo(originalDomain.articleId)
        assertThat(reconvertedDomain.originId).isEqualTo(originalDomain.originId)
        assertThat(reconvertedDomain.sourceId).isEqualTo(originalDomain.sourceId)
        assertThat(reconvertedDomain.title).isEqualTo(originalDomain.title)
        assertThat(reconvertedDomain.content).isEqualTo(originalDomain.content)
        assertThat(reconvertedDomain.sourceUrl).isEqualTo(originalDomain.sourceUrl)

        // 시간 필드는 milliseconds 단위로 검증
        assertThat(reconvertedDomain.writtenAt.toEpochMilli())
            .isEqualTo(originalDomain.writtenAt.toEpochMilli())
        assertThat(reconvertedDomain.modifiedAt.toEpochMilli())
            .isEqualTo(originalDomain.modifiedAt.toEpochMilli())
    }

    @Test
    @DisplayName("Round-trip: 다양한 시간대에서 정확한 Instant 변환")
    fun roundTrip_accurateInstantConversionAcrossTimeZones() {
        val seoulTime = ZonedDateTime.of(2023, 7, 15, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val entity = TestFixtures.createArticleEntity(writtenAt = seoulTime)

        val domain = ArticleMapper.toDomainModel(entity)
        val reconvertedEntity = ArticleMapper.toPersistenceModel(domain)

        // UTC 기준으로 일관성 검증
        assertThat(reconvertedEntity.writtenAt.toInstant().toEpochMilli())
            .isEqualTo(seoulTime.toInstant().toEpochMilli())
    }

    @Test
    @DisplayName("Round-trip: null sourceUrl 유지")
    fun roundTrip_nullSourceUrlPreserved() {
        val originalDomain = TestFixtures.createArticle(sourceUrl = null)

        val entity = ArticleMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = ArticleMapper.toDomainModel(entity)

        assertThat(reconvertedDomain.sourceUrl).isNull()
    }
}