package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.mapper.RefinedArticleMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@DisplayName("RefinedArticleMapper 테스트")
class RefinedArticleMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: 기본 필드 매핑")
    fun `toDomainModel - 기본 필드 매핑`() {
        val entity = TestFixtures.createRefinedArticleEntity(
            title = "정제된 기사 제목",
            content = "정제된 기사 본문",
            summary = "기사 요약문입니다."
        )
        val domain = RefinedArticleMapper.toDomainModel(entity)

        assertThat(domain.title).isEqualTo("정제된 기사 제목")
        assertThat(domain.content).isEqualTo("정제된 기사 본문")
        assertThat(domain.summary).isEqualTo("기사 요약문입니다.")
    }

    @Test
    @DisplayName("toDomainModel: ZonedDateTime → Instant 변환")
    fun `toDomainModel - ZonedDateTime을 Instant로 변환`() {
        val writtenAt = ZonedDateTime.of(2023, 7, 15, 10, 30, 0, 0, ZoneId.of("Asia/Seoul"))
        val entity = TestFixtures.createRefinedArticleEntity(writtenAt = writtenAt)

        val domain = RefinedArticleMapper.toDomainModel(entity)

        assertThat(domain.writtenAt).isEqualTo(writtenAt.toInstant())
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: 기본 필드 매핑")
    fun `toPersistenceModel - 기본 필드 매핑`() {
        val domain = TestFixtures.createRefinedArticle(
            title = "정제된 기사 제목",
            content = "정제된 기사 본문",
            summary = "기사 요약문입니다."
        )
        val entity = RefinedArticleMapper.toPersistenceModel(domain)

        assertThat(entity.title).isEqualTo("정제된 기사 제목")
        assertThat(entity.content).isEqualTo("정제된 기사 본문")
        assertThat(entity.summary).isEqualTo("기사 요약문입니다.")
    }

    @Test
    @DisplayName("toPersistenceModel: Instant → ZonedDateTime(UTC) 변환")
    fun `toPersistenceModel - Instant를 UTC ZonedDateTime으로 변환`() {
        val writtenAt = Instant.parse("2023-07-15T01:30:00Z")
        val domain = TestFixtures.createRefinedArticle(writtenAt = writtenAt)

        val entity = RefinedArticleMapper.toPersistenceModel(domain)

        val expectedWrittenAt = ZonedDateTime.ofInstant(writtenAt, ZoneOffset.UTC)
        assertThat(entity.writtenAt).isEqualTo(expectedWrittenAt)
        assertThat(entity.writtenAt.zone).isEqualTo(ZoneOffset.UTC)
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun `roundTrip - Domain에서 Entity 변환 후 다시 Domain으로 변환하면 원본과 동일`() {
        val originalDomain = TestFixtures.createRefinedArticle(
            title = "정제된 테스트 기사 제목",
            content = "정제된 테스트 기사 본문",
            summary = "테스트 기사 요약입니다.",
            writtenAt = Instant.parse("2023-07-15T10:30:00Z")
        )

        val entity = RefinedArticleMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = RefinedArticleMapper.toDomainModel(entity)

        assertThat(reconvertedDomain).isEqualTo(originalDomain)
    }
}
