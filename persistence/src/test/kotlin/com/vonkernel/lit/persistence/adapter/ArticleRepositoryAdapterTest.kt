package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.JpaArticleRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ArticleRepositoryAdapter::class)
@DisplayName("ArticleRepositoryAdapter 테스트")
class ArticleRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: ArticleRepositoryAdapter

    @Autowired
    private lateinit var jpaRepository: JpaArticleRepository

    // ===== save() - 단일 Article 저장 =====
    @Test
    @DisplayName("save: 기본 저장 및 DB 검증")
    fun save_basicSaveAndDbValidation() {
        val article = TestFixtures.createArticle(
            articleId = "article-save-1",
            title = "테스트 기사 제목",
            content = "테스트 기사 본문",
            sourceUrl = "https://example.com/article"
        )

        val saved = adapter.save(article)

        // 반환값 검증
        assertThat(saved.articleId).isEqualTo(article.articleId)
        assertThat(saved.title).isEqualTo(article.title)
        assertThat(saved.content).isEqualTo(article.content)
        assertThat(saved.sourceUrl).isEqualTo(article.sourceUrl)

        // DB 재조회 검증
        val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.articleId).isEqualTo(article.articleId)
        assertThat(fromDb.title).isEqualTo(article.title)
        assertThat(fromDb.content).isEqualTo(article.content)
        assertThat(fromDb.sourceUrl).isEqualTo(article.sourceUrl)
    }

    @Test
    @DisplayName("save: null sourceUrl 처리")
    fun save_nullSourceUrl() {
        val article = TestFixtures.createArticle(
            articleId = "article-save-2",
            sourceUrl = null
        )

        val saved = adapter.save(article)

        assertThat(saved.sourceUrl).isNull()

        val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.sourceUrl).isNull()
    }

    @Test
    @DisplayName("save: 시간대 변환 정확성")
    fun save_timezoneConversionAccuracy() {
        val writtenAt = Instant.parse("2023-01-15T10:30:00Z")
        val modifiedAt = Instant.parse("2023-01-16T14:45:00Z")

        val article = TestFixtures.createArticle(
            articleId = "article-save-3",
            writtenAt = writtenAt,
            modifiedAt = modifiedAt
        )

        val saved = adapter.save(article)

        // Instant 기준으로 milliseconds 단위 검증
        assertThat(saved.writtenAt.toEpochMilli()).isEqualTo(writtenAt.toEpochMilli())
        assertThat(saved.modifiedAt.toEpochMilli()).isEqualTo(modifiedAt.toEpochMilli())

        val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.writtenAt.toInstant().toEpochMilli()).isEqualTo(writtenAt.toEpochMilli())
        assertThat(fromDb.modifiedAt.toInstant().toEpochMilli()).isEqualTo(modifiedAt.toEpochMilli())
    }

    @Test
    @DisplayName("save: 오래된 기사 저장")
    fun save_oldArticle() {
        val oldDate = Instant.parse("2020-01-01T00:00:00Z")
        val article = TestFixtures.createArticle(
            articleId = "article-save-old",
            writtenAt = oldDate,
            modifiedAt = oldDate
        )

        val saved = adapter.save(article)

        assertThat(saved.writtenAt.toEpochMilli()).isEqualTo(oldDate.toEpochMilli())
    }

    @Test
    @DisplayName("save: 긴 제목 저장")
    fun save_longTitle() {
        val longTitle = "테스트문자 ".repeat(100) // 500+ 글자
        val article = TestFixtures.createArticle(
            articleId = "article-save-long-title",
            title = longTitle
        )

        val saved = adapter.save(article)

        assertThat(saved.title).isEqualTo(longTitle)
        assertThat(saved.title.length).isGreaterThan(500)

        val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.title).isEqualTo(longTitle)
    }

    @Test
    @DisplayName("save: 긴 내용 저장")
    fun save_longContent() {
        val longContent = "이 것은 본문 내용입니다. ".repeat(1000) // 10000+ 글자
        val article = TestFixtures.createArticle(
            articleId = "article-save-long-content",
            content = longContent
        )

        val saved = adapter.save(article)

        assertThat(saved.content).isEqualTo(longContent)
        assertThat(saved.content.length).isGreaterThan(10000)

        val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.content).isEqualTo(longContent)
    }

    // ===== saveAll() - 다중 Article 저장 =====
    @Test
    @DisplayName("saveAll: 빈 컬렉션 처리")
    fun saveAll_emptyCollection() {
        val result = adapter.saveAll(emptyList())

        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("saveAll: 단일 항목 리스트")
    fun saveAll_singleItem() {
        val article = TestFixtures.createArticle(articleId = "article-saveall-1")
        val articles = listOf(article)

        val saved = adapter.saveAll(articles)

        assertThat(saved).hasSize(1)
        assertThat(saved.first().articleId).isEqualTo("article-saveall-1")

        val fromDb = jpaRepository.findById("article-saveall-1").orElse(null)
        assertThat(fromDb).isNotNull
    }

    @Test
    @DisplayName("saveAll: 5개 항목 저장")
    fun saveAll_fiveItems() {
        val articles = (1..5).map { i ->
            TestFixtures.createArticle(
                articleId = "article-saveall-5-$i",
                title = "기사 제목 $i"
            )
        }

        val saved = adapter.saveAll(articles)

        assertThat(saved).hasSize(5)
        assertThat(saved.map { it.articleId }).containsExactlyInAnyOrder(
            "article-saveall-5-1",
            "article-saveall-5-2",
            "article-saveall-5-3",
            "article-saveall-5-4",
            "article-saveall-5-5"
        )

        // DB 검증
        val fromDb = jpaRepository.findAllById(saved.map { it.articleId })
        assertThat(fromDb).hasSize(5)
    }

    @Test
    @DisplayName("saveAll: 10개 항목 저장")
    fun saveAll_tenItems() {
        val articles = (1..10).map { i ->
            TestFixtures.createArticle(articleId = "article-saveall-10-$i")
        }

        val saved = adapter.saveAll(articles)

        assertThat(saved).hasSize(10)

        val fromDb = jpaRepository.findAllById(saved.map { it.articleId })
        assertThat(fromDb).hasSize(10)
    }

    @Test
    @DisplayName("saveAll: 100개 항목 저장")
    fun saveAll_hundredItems() {
        val articles = (1..100).map { i ->
            TestFixtures.createArticle(articleId = "article-saveall-100-$i")
        }

        val saved = adapter.saveAll(articles)

        assertThat(saved).hasSize(100)

        val fromDb = jpaRepository.findAllById(saved.map { it.articleId })
        assertThat(fromDb).hasSize(100)
    }

    @Test
    @DisplayName("saveAll: 각 항목의 articleId가 고유함")
    fun saveAll_uniqueArticleIds() {
        val articles = (1..10).map { i ->
            TestFixtures.createArticle(articleId = "article-unique-$i")
        }

        val saved = adapter.saveAll(articles)

        val articleIds = saved.map { it.articleId }
        assertThat(articleIds).hasSize(10)
        assertThat(articleIds.toSet()).hasSize(10) // 중복 없음
    }

    @Test
    @DisplayName("saveAll: 각 항목의 매핑 정확성 검증")
    fun saveAll_mappingAccuracy() {
        val articles = listOf(
            TestFixtures.createArticle(articleId = "article-map-1", title = "제목1", content = "내용1"),
            TestFixtures.createArticle(articleId = "article-map-2", title = "제목2", content = "내용2"),
            TestFixtures.createArticle(articleId = "article-map-3", title = "제목3", content = "내용3")
        )

        val saved = adapter.saveAll(articles)

        assertThat(saved).hasSize(3)

        // 각 항목별 정확성 검증
        val saved1 = saved.find { it.articleId == "article-map-1" }
        assertThat(saved1).isNotNull
        assertThat(saved1!!.title).isEqualTo("제목1")
        assertThat(saved1.content).isEqualTo("내용1")

        val saved2 = saved.find { it.articleId == "article-map-2" }
        assertThat(saved2).isNotNull
        assertThat(saved2!!.title).isEqualTo("제목2")
        assertThat(saved2.content).isEqualTo("내용2")
    }

    // ===== filterNonExisting() - 존재하지 않는 기사 ID 필터링 =====
    @Test
    @DisplayName("filterNonExisting: 모두 존재하지 않음 - 모든 ID 반환")
    fun filterNonExisting_allNonExisting() {
        val articleIds = listOf("non-exist-1", "non-exist-2", "non-exist-3")

        val result = adapter.filterNonExisting(articleIds)

        assertThat(result).hasSize(3)
        assertThat(result).containsExactlyInAnyOrder("non-exist-1", "non-exist-2", "non-exist-3")
    }

    @Test
    @DisplayName("filterNonExisting: 모두 존재함 - 빈 List 반환")
    fun filterNonExisting_allExisting() {
        // 먼저 3개 저장
        val articles = listOf(
            TestFixtures.createArticle(articleId = "exist-1"),
            TestFixtures.createArticle(articleId = "exist-2"),
            TestFixtures.createArticle(articleId = "exist-3")
        )
        adapter.saveAll(articles)

        val articleIds = listOf("exist-1", "exist-2", "exist-3")
        val result = adapter.filterNonExisting(articleIds)

        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("filterNonExisting: 혼합 - 3개 중 1개 존재, 2개 반환")
    fun filterNonExisting_partialExisting_oneOfThree() {
        // 1개만 저장
        adapter.save(TestFixtures.createArticle(articleId = "partial-exist-1"))

        val articleIds = listOf("partial-exist-1", "partial-non-exist-1", "partial-non-exist-2")
        val result = adapter.filterNonExisting(articleIds)

        assertThat(result).hasSize(2)
        assertThat(result).containsExactlyInAnyOrder("partial-non-exist-1", "partial-non-exist-2")
    }

    @Test
    @DisplayName("filterNonExisting: 혼합 - 10개 중 7개 존재, 3개 반환")
    fun filterNonExisting_partialExisting_sevenOfTen() {
        // 7개 저장
        val existingArticles = (1..7).map { i ->
            TestFixtures.createArticle(articleId = "partial-exist-$i")
        }
        adapter.saveAll(existingArticles)

        // 10개 ID (7개 존재 + 3개 미존재)
        val articleIds = (1..7).map { "partial-exist-$it" } + listOf("partial-non-8", "partial-non-9", "partial-non-10")
        val result = adapter.filterNonExisting(articleIds)

        assertThat(result).hasSize(3)
        assertThat(result).containsExactlyInAnyOrder("partial-non-8", "partial-non-9", "partial-non-10")
    }

    @Test
    @DisplayName("filterNonExisting: 빈 컬렉션 - 빈 List 반환")
    fun filterNonExisting_emptyCollection() {
        val result = adapter.filterNonExisting(emptyList())

        assertThat(result).isEmpty()
    }

    @Test
    @DisplayName("filterNonExisting: 대량 ID (1000개) 성능 검증")
    fun filterNonExisting_largeCollection() {
        // 500개 저장
        val existingArticles = (1..500).map { i ->
            TestFixtures.createArticle(articleId = "large-exist-$i")
        }
        adapter.saveAll(existingArticles)

        // 1000개 ID (500개 존재 + 500개 미존재)
        val articleIds = (1..500).map { "large-exist-$it" } + (501..1000).map { "large-non-$it" }

        val result = adapter.filterNonExisting(articleIds)

        assertThat(result).hasSize(500)
        assertThat(result.all { it.startsWith("large-non-") }).isTrue()
    }

    // ===== 트랜잭션 처리 검증 =====
    @Test
    @DisplayName("트랜잭션: 각 save()는 자동 commit")
    fun transaction_autoCommit() {
        val article1 = TestFixtures.createArticle(articleId = "tx-article-1")
        val saved1 = adapter.save(article1)

        // 즉시 재조회 가능 (자동 commit됨)
        val fromDb1 = jpaRepository.findById(saved1.articleId).orElse(null)
        assertThat(fromDb1).isNotNull

        val article2 = TestFixtures.createArticle(articleId = "tx-article-2")
        val saved2 = adapter.save(article2)

        val fromDb2 = jpaRepository.findById(saved2.articleId).orElse(null)
        assertThat(fromDb2).isNotNull
    }

    @Test
    @DisplayName("트랜잭션: 여러 save()는 각각 독립적인 트랜잭션")
    fun transaction_independentTransactions() {
        val article1 = TestFixtures.createArticle(articleId = "independent-1")
        adapter.save(article1)

        val article2 = TestFixtures.createArticle(articleId = "independent-2")
        adapter.save(article2)

        // 두 항목 모두 저장됨
        val fromDb1 = jpaRepository.findById("independent-1").orElse(null)
        val fromDb2 = jpaRepository.findById("independent-2").orElse(null)

        assertThat(fromDb1).isNotNull
        assertThat(fromDb2).isNotNull
    }
}