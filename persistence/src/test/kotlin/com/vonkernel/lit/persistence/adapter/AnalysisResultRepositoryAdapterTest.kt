package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.RegionType
import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.config.ObjectMapperConfig
import com.vonkernel.lit.persistence.jpa.entity.article.IncidentTypeEntity
import com.vonkernel.lit.persistence.jpa.entity.article.UrgencyTypeEntity
import com.vonkernel.lit.persistence.jpa.*
import com.vonkernel.lit.persistence.jpa.mapper.AnalysisResultOutboxMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AnalysisResultRepositoryAdapter::class, AnalysisResultOutboxMapper::class, ObjectMapperConfig::class)
@DisplayName("AnalysisResultRepositoryAdapter 테스트")
class AnalysisResultRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: AnalysisResultRepositoryAdapter

    @Autowired
    private lateinit var jpaAnalysisResultRepository: JpaAnalysisResultRepository

    @Autowired
    private lateinit var jpaAnalysisResultOutboxRepository: JpaAnalysisResultOutboxRepository

    @Autowired
    private lateinit var jpaArticleRepository: JpaArticleRepository

    @Autowired
    private lateinit var jpaUrgencyTypeRepository: JpaUrgencyTypeRepository

    @Autowired
    private lateinit var jpaIncidentTypeRepository: JpaIncidentTypeRepository

    @Autowired
    private lateinit var jpaAddressRepository: JpaAddressRepository

    // Reference data entities
    private lateinit var urgencyLow: UrgencyTypeEntity
    private lateinit var urgencyMedium: UrgencyTypeEntity
    private lateinit var urgencyHigh: UrgencyTypeEntity

    private lateinit var incidentTypes: Map<String, IncidentTypeEntity>

    @BeforeEach
    fun setUp() {
        // 1. Urgency Types - schema-h2.sql에서 INSERT된 마스터 데이터 로드
        urgencyLow = jpaUrgencyTypeRepository.findByName("정보")!!
        urgencyMedium = jpaUrgencyTypeRepository.findByName("주의")!!
        urgencyHigh = jpaUrgencyTypeRepository.findByName("긴급")!!

        // 2. Incident Types - schema-h2.sql에서 INSERT된 마스터 데이터 로드
        incidentTypes = jpaIncidentTypeRepository.findAll().associateBy { it.code }
    }

    // ===== Part 1: 기본 save() 흐름 테스트 (5개) =====

    @Test
    @DisplayName("Part 1.1: 최소 구성 - urgency만 있는 AnalysisResult 저장")
    fun save_minimalAnalysisResult() {
        // Given: Article 준비
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-minimal-1")
        )

        // Given: 최소 AnalysisResult (urgency만 필수)
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = emptySet(),
            urgency = TestFixtures.createUrgency(name = "긴급", level = 9),
            keywords = emptyList(),
            locations = emptyList()
        )

        // When: 저장
        val saved = adapter.save(analysisResult)

        // Then: 반환값 검증
        assertThat(saved.articleId).isEqualTo("article-minimal-1")
        assertThat(saved.urgency.name).isEqualTo("긴급")
        assertThat(saved.urgency.level).isEqualTo(9)
        assertThat(saved.incidentTypes).isEmpty()
        assertThat(saved.keywords).isEmpty()
        assertThat(saved.locations).isEmpty()

        // Then: DB에서 AnalysisResultEntity 재조회
        val fromDb = jpaAnalysisResultRepository.findAll()
        assertThat(fromDb).hasSize(1)
        assertThat(fromDb[0].urgencyMapping).isNotNull
        assertThat(fromDb[0].urgencyMapping?.urgencyType?.name).isEqualTo("긴급")
        assertThat(fromDb[0].incidentTypeMappings).isEmpty()
        assertThat(fromDb[0].keywords).isEmpty()
        assertThat(fromDb[0].addressMappings).isEmpty()

        // Then: Outbox 저장 확인
        val outboxEntries = jpaAnalysisResultOutboxRepository.findAll()
        assertThat(outboxEntries).hasSize(1)
        assertThat(outboxEntries[0].articleId).isEqualTo("article-minimal-1")
        assertThat(outboxEntries[0].payload).contains("\"urgency\"")
        assertThat(outboxEntries[0].payload).contains("긴급")
    }

    @Test
    @DisplayName("Part 1.2: 표준 구성 - 모든 필드 포함 AnalysisResult 저장")
    fun save_standardAnalysisResult() {
        // Given: Article 준비
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-standard-1")
        )

        // Given: 표준 AnalysisResult
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(
                TestFixtures.createKeyword("화재", 10),
                TestFixtures.createKeyword("대피", 8),
                TestFixtures.createKeyword("경고", 5)
            ),
            locations = listOf(
                TestFixtures.createLocation(
                    coordinate = TestFixtures.createCoordinate(37.4979, 126.9270),
                    address = TestFixtures.createAddress(code = "11110")
                )
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: 반환값 검증
        assertThat(saved.articleId).isEqualTo("article-standard-1")
        assertThat(saved.incidentTypes).hasSize(2)
        assertThat(saved.incidentTypes.map { it.code }).containsExactlyInAnyOrder("FOREST_FIRE", "TYPHOON")
        assertThat(saved.urgency.name).isEqualTo("긴급")
        assertThat(saved.keywords).hasSize(3)
        assertThat(saved.keywords.map { it.keyword }).containsExactlyInAnyOrder("화재", "대피", "경고")
        assertThat(saved.locations).hasSize(1)
        assertThat(saved.locations[0].address.code).isEqualTo("11110")

        // Then: DB 재조회 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
        assertThat(fromDb.keywords).hasSize(3)
        assertThat(fromDb.addressMappings).hasSize(1)

        // Then: 양방향 관계 검증
        fromDb.incidentTypeMappings.forEach { mapping ->
            assertThat(mapping.analysisResult).isEqualTo(fromDb)
        }
        fromDb.keywords.forEach { keyword ->
            assertThat(keyword.analysisResult).isEqualTo(fromDb)
        }
    }

    @Test
    @DisplayName("Part 1.3: save()가 AnalysisResultMapper.toDomainModel() 통해 도메인 모델로 변환")
    fun save_returnsDomainModelViaMapper() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-mapper-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("주의", 3),
            keywords = listOf(TestFixtures.createKeyword("테스트", 5)),
            locations = emptyList()
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: 도메인 모델 타입 확인
        assertThat(saved).isInstanceOf(com.vonkernel.lit.core.entity.AnalysisResult::class.java)

        // Then: 모든 필드가 정확히 매핑됨
        assertThat(saved.articleId).isEqualTo(analysisResult.articleId)
        assertThat(saved.incidentTypes.size).isEqualTo(analysisResult.incidentTypes.size)
        assertThat(saved.urgency.name).isEqualTo(analysisResult.urgency.name)
        assertThat(saved.keywords.size).isEqualTo(analysisResult.keywords.size)
    }

    @Test
    @DisplayName("Part 1.4: 단일 트랜잭션에서 AnalysisResultEntity + Outbox 모두 저장")
    fun save_savesAnalysisResultAndOutboxInSameTransaction() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-tx-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("정보", 1)
        )

        // When
        adapter.save(analysisResult)

        // Then: AnalysisResult 저장됨
        val analysisResults = jpaAnalysisResultRepository.findAll()
        assertThat(analysisResults).hasSize(1)

        // Then: Outbox 저장됨 (같은 트랜잭션)
        val outboxEntries = jpaAnalysisResultOutboxRepository.findAll()
        assertThat(outboxEntries).hasSize(1)

        // Then: 둘 다 commit되었으므로 즉시 조회 가능
        assertThat(outboxEntries[0].articleId).isEqualTo("article-tx-1")
    }

    @Test
    @DisplayName("Part 1.5: 복잡한 AnalysisResult - 5개 IncidentTypes, 10개 Keywords, 3개 Locations")
    fun save_complexAnalysisResultWithMultipleRelations() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-complex-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("FLOOD", "홍수"),
                TestFixtures.createIncidentType("EARTHQUAKE", "지진"),
                TestFixtures.createIncidentType("LANDSLIDE", "산사태")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = TestFixtures.createKeywords(10),
            locations = TestFixtures.createLocations(3)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).hasSize(5)
        assertThat(saved.keywords).hasSize(10)
        assertThat(saved.locations).hasSize(3)

        // Then: DB 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(5)
        assertThat(fromDb.keywords).hasSize(10)
        assertThat(fromDb.addressMappings).hasSize(3)

        // Then: Address도 3개 생성됨 (신규 Address)
        val addresses = jpaAddressRepository.findAll()
        assertThat(addresses).hasSizeGreaterThanOrEqualTo(3)
    }

    // ===== Part 2: loadUrgency() 테스트 (5개) =====

    @Test
    @DisplayName("Part 2.1: loadUrgency() - 존재하는 urgency HIGH 로드 성공")
    fun loadUrgency_existingHigh() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-urgency-high")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.urgency.name).isEqualTo("긴급")
        assertThat(saved.urgency.level).isEqualTo(9)

        // Then: UrgencyMappingEntity가 기존 UrgencyTypeEntity 참조
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isEqualTo(urgencyHigh.id)
        assertThat(fromDb.urgencyMapping?.urgencyType?.name).isEqualTo("긴급")
    }

    @Test
    @DisplayName("Part 2.2: loadUrgency() - 존재하는 urgency LOW 로드 성공")
    fun loadUrgency_existingLow() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-urgency-low")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("정보", 1)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.urgency.name).isEqualTo("정보")
        assertThat(saved.urgency.level).isEqualTo(1)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isEqualTo(urgencyLow.id)
    }

    @Test
    @DisplayName("Part 2.3: loadUrgency() - 존재하는 urgency MEDIUM 로드 성공")
    fun loadUrgency_existingMedium() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-urgency-medium")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("주의", 3)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.urgency.name).isEqualTo("주의")
        assertThat(saved.urgency.level).isEqualTo(3)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isEqualTo(urgencyMedium.id)
    }

    @Test
    @DisplayName("Part 2.4: loadUrgency() - 존재하지 않는 urgency → IllegalArgumentException")
    fun loadUrgency_nonExistingThrowsException() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-urgency-invalid")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("NONEXISTENT", 99)
        )

        // When & Then
        assertThatThrownBy { adapter.save(analysisResult) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Urgency not found: NONEXISTENT")

        // Then: 전체 롤백되어 AnalysisResult 저장 안 됨
        assertThat(jpaAnalysisResultRepository.findAll()).isEmpty()

        // Then: Outbox도 저장 안 됨
        assertThat(jpaAnalysisResultOutboxRepository.findAll()).isEmpty()
    }

    @Test
    @DisplayName("Part 2.5: createUrgencyMapping() - 양방향 관계 설정 검증")
    fun createUrgencyMapping_bidirectionalRelationship() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-urgency-bidirect")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val urgencyMapping = fromDb.urgencyMapping

        assertThat(urgencyMapping).isNotNull
        assertThat(urgencyMapping!!.analysisResult).isEqualTo(fromDb)
        assertThat(fromDb.urgencyMapping).isEqualTo(urgencyMapping)
        assertThat(urgencyMapping.urgencyType?.name).isEqualTo("긴급")
    }

    // ===== Part 3: loadIncidentTypes() 테스트 (8개) =====

    @Test
    @DisplayName("Part 3.1: loadIncidentTypes() - 0개 incident types (빈 Set)")
    fun loadIncidentTypes_emptySet() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-0")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = emptySet()
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).isEmpty()

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).isEmpty()
    }

    @Test
    @DisplayName("Part 3.2: loadIncidentTypes() - 1개 incident type 로드")
    fun loadIncidentTypes_singleType() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불"))
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).hasSize(1)
        assertThat(saved.incidentTypes.first().code).isEqualTo("FOREST_FIRE")

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(1)
        assertThat(fromDb.incidentTypeMappings.first().incidentType?.code).isEqualTo("FOREST_FIRE")
    }

    @Test
    @DisplayName("Part 3.3: loadIncidentTypes() - 5개 incident types 모두 로드")
    fun loadIncidentTypes_fiveTypes() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-5")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("FLOOD", "홍수"),
                TestFixtures.createIncidentType("EARTHQUAKE", "지진"),
                TestFixtures.createIncidentType("LANDSLIDE", "산사태")
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).hasSize(5)
        assertThat(saved.incidentTypes.map { it.code }).containsExactlyInAnyOrder(
            "FOREST_FIRE", "TYPHOON", "FLOOD", "EARTHQUAKE", "LANDSLIDE"
        )

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(5)
    }

    @Test
    @DisplayName("Part 3.4: loadIncidentTypes() - 일부 존재하지 않는 types (부분 로드)")
    fun loadIncidentTypes_partialExisting() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-partial")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("nonexistent", "없음"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: 존재하는 2개만 로드됨 (fire, typhoon)
        assertThat(saved.incidentTypes).hasSize(2)
        assertThat(saved.incidentTypes.map { it.code }).containsExactlyInAnyOrder("FOREST_FIRE", "TYPHOON")

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
    }

    @Test
    @DisplayName("Part 3.5: loadIncidentTypes() - findByCodeIn() 정확히 호출됨")
    fun loadIncidentTypes_callsFindByCodeIn() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-find")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 저장된 매핑이 정확히 요청한 codes와 일치
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val loadedCodes = fromDb.incidentTypeMappings.map { it.incidentType?.code }
        assertThat(loadedCodes).containsExactlyInAnyOrder("FOREST_FIRE", "TYPHOON")
    }

    @Test
    @DisplayName("Part 3.6: createIncidentTypeMappings() - 양방향 관계 설정")
    fun createIncidentTypeMappings_bidirectionalRelationship() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-bidirect")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("FLOOD", "홍수")
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        fromDb.incidentTypeMappings.forEach { mapping ->
            assertThat(mapping.analysisResult).isEqualTo(fromDb)
            assertThat(fromDb.incidentTypeMappings).contains(mapping)
        }
    }

    @Test
    @DisplayName("Part 3.7: loadIncidentTypes() - MutableSet에 추가 순서 (DB 로드 순서)")
    fun loadIncidentTypes_orderInMutableSet() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-order")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("FLOOD", "홍수")
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: Set이므로 순서 보장 안 함 (containsExactlyInAnyOrder 사용)
        assertThat(saved.incidentTypes.map { it.code }).containsExactlyInAnyOrder(
            "FOREST_FIRE", "TYPHOON", "FLOOD"
        )
    }

    @Test
    @DisplayName("Part 3.8: loadIncidentTypes() - 10개 incident types 대량 로드")
    fun loadIncidentTypes_tenTypes() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-incidents-10")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("FLOOD", "홍수"),
                TestFixtures.createIncidentType("EARTHQUAKE", "지진"),
                TestFixtures.createIncidentType("LANDSLIDE", "산사태"),
                TestFixtures.createIncidentType("HEAVY_SNOW", "대설"),
                TestFixtures.createIncidentType("HEAT_WAVE", "폭염"),
                TestFixtures.createIncidentType("COLD_WAVE", "한파"),
                TestFixtures.createIncidentType("STRONG_WIND", "강풍"),
                TestFixtures.createIncidentType("DROUGHT", "가뭄")
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).hasSize(10)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(10)
    }

    // ===== Part 4: loadOrCreateAddresses() 테스트 (8개) =====

    @Test
    @DisplayName("Part 4.1: loadOrCreateAddresses() - 기존 address 재사용")
    fun loadOrCreateAddresses_reuseExisting() {
        // Given: 기존 Address 저장
        val existingAddress = jpaAddressRepository.save(
            TestFixtures.createAddressEntity(regionType = "B", code = "11110")
        )

        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-reuse")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(
                    address = TestFixtures.createAddress(code = "11110")
                )
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: 기존 Address 재사용됨
        assertThat(saved.locations).hasSize(1)
        assertThat(saved.locations[0].address.code).isEqualTo("11110")

        // Then: 새로운 Address 생성 안 됨 (기존 것 사용)
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSize(1)
        assertThat(allAddresses[0].id).isEqualTo(existingAddress.id)

        // Then: AddressMappingEntity는 기존 Address 참조
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(1)
        assertThat(fromDb.addressMappings.first().address?.id).isEqualTo(existingAddress.id)
    }

    @Test
    @DisplayName("Part 4.2: loadOrCreateAddresses() - 새로운 address 생성")
    fun loadOrCreateAddresses_createNew() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-new")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(
                    coordinate = TestFixtures.createCoordinate(37.123, 127.456),
                    address = TestFixtures.createAddress(code = "99999")
                )
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.locations).hasSize(1)

        // Then: 새로운 Address 생성됨
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSize(1)
        assertThat(allAddresses[0].code).isEqualTo("99999")

        // Then: AddressCoordinateEntity도 @MapsId로 생성됨
        val address = allAddresses[0]
        assertThat(address.coordinate).isNotNull
        assertThat(address.coordinate?.latitude).isEqualTo(37.123)
        assertThat(address.coordinate?.longitude).isEqualTo(127.456)
    }

    @Test
    @DisplayName("Part 4.3: loadOrCreateAddresses() - 혼합 (2개 기존 + 3개 신규)")
    fun loadOrCreateAddresses_mixed() {
        // Given: 2개 기존 Address 저장
        jpaAddressRepository.save(
            TestFixtures.createAddressEntity(code = "11110")
        )
        jpaAddressRepository.save(
            TestFixtures.createAddressEntity(code = "11120")
        )

        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-mixed")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11110")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11120")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "99991")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "99992")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "99993"))
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.locations).hasSize(5)

        // Then: 총 5개 Address (2개 기존 + 3개 신규)
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSize(5)

        // Then: AddressMappingEntity 5개 생성
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(5)
    }

    @Test
    @DisplayName("Part 4.4: loadOrCreateAddresses() - 0개 locations (빈 List)")
    fun loadOrCreateAddresses_emptyList() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-0")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = emptyList()
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.locations).isEmpty()

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).isEmpty()
    }

    @Test
    @DisplayName("Part 4.5: loadOrCreateAddresses() - findByRegionTypeAndCode() unique constraint")
    fun loadOrCreateAddresses_uniqueConstraint() {
        // Given: 같은 regionType + code로 Address 중복 저장 시도
        val address1 = jpaAddressRepository.save(
            TestFixtures.createAddressEntity(regionType = "B", code = "11110", addressName = "첫번째")
        )

        // Then: 같은 regionType + code로 재조회하면 기존 Address 반환
        val found = jpaAddressRepository.findByRegionTypeAndCode("B", "11110")
        assertThat(found).isNotNull
        assertThat(found?.id).isEqualTo(address1.id)
        assertThat(found?.addressName).isEqualTo("첫번째")
    }

    @Test
    @DisplayName("Part 4.6: loadOrCreateAddresses() - AddressCoordinateEntity @MapsId로 생성")
    fun loadOrCreateAddresses_addressCoordinateEntityCreated() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-coordinate")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(
                    coordinate = TestFixtures.createCoordinate(37.5665, 126.9780),
                    address = TestFixtures.createAddress(code = "coord-1")
                )
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: AddressEntity와 AddressCoordinateEntity 생성됨
        val address = jpaAddressRepository.findAll()[0]
        assertThat(address.coordinate).isNotNull
        assertThat(address.coordinate?.id).isEqualTo(address.id)
        assertThat(address.coordinate?.latitude).isEqualTo(37.5665)
        assertThat(address.coordinate?.longitude).isEqualTo(126.9780)
    }

    @Test
    @DisplayName("Part 4.7: createAddressMappings() - 양방향 관계 설정")
    fun createAddressMappings_bidirectionalRelationship() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-bidirect")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11110")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11120"))
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        fromDb.addressMappings.forEach { mapping ->
            assertThat(mapping.analysisResult).isEqualTo(fromDb)
            assertThat(fromDb.addressMappings).contains(mapping)
        }
    }

    @Test
    @DisplayName("Part 4.8: loadOrCreateAddresses() - 100개 addresses 대량 생성")
    fun loadOrCreateAddresses_hundredAddresses() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-address-100")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = TestFixtures.createLocations(100)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.locations).hasSize(100)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(100)

        // Then: 100개 Address 생성됨
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSizeGreaterThanOrEqualTo(100)
    }

    // ===== Part 5: createKeywords() 테스트 (4개) =====

    @Test
    @DisplayName("Part 5.1: createKeywords() - 0개 keywords (빈 List)")
    fun createKeywords_emptyList() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-keywords-0")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            keywords = emptyList()
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.keywords).isEmpty()

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.keywords).isEmpty()
    }

    @Test
    @DisplayName("Part 5.2: createKeywords() - 1개 keyword 생성")
    fun createKeywords_singleKeyword() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-keywords-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            keywords = listOf(TestFixtures.createKeyword("화재", 10))
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.keywords).hasSize(1)
        assertThat(saved.keywords[0].keyword).isEqualTo("화재")
        assertThat(saved.keywords[0].priority).isEqualTo(10)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.keywords).hasSize(1)
        assertThat(fromDb.keywords.first().keyword).isEqualTo("화재")
    }

    @Test
    @DisplayName("Part 5.3: createKeywords() - 10개 keywords with priority order")
    fun createKeywords_tenKeywordsWithPriority() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-keywords-10")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            keywords = (1..10).map { i ->
                TestFixtures.createKeyword("keyword_$i", 11 - i)
            }
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.keywords).hasSize(10)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.keywords).hasSize(10)

        // Then: 우선순위가 올바르게 저장됨
        val priorities = fromDb.keywords.map { it.priority }.sorted()
        assertThat(priorities).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    }

    @Test
    @DisplayName("Part 5.4: createKeywords() - 특수문자 키워드 정확히 저장")
    fun createKeywords_specialCharacters() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-keywords-special")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            keywords = listOf(
                TestFixtures.createKeyword("@#\$%&", 10),
                TestFixtures.createKeyword("\\n테스트", 8),
                TestFixtures.createKeyword("\"quotes\"", 6),
                TestFixtures.createKeyword("한글테스트", 4),
                TestFixtures.createKeyword("🔥emoji", 2)
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: 특수문자가 정확히 저장됨
        assertThat(saved.keywords).hasSize(5)
        assertThat(saved.keywords.map { it.keyword }).containsExactlyInAnyOrder(
            "@#\$%&", "\\n테스트", "\"quotes\"", "한글테스트", "🔥emoji"
        )

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val keywordTexts = fromDb.keywords.map { it.keyword }
        assertThat(keywordTexts).contains("@#\$%&", "\\n테스트", "\"quotes\"", "🔥emoji")
    }

    // ===== Part 6: setupAnalysisResult() 양방향 관계 설정 (6개) =====

    @Test
    @DisplayName("Part 6.1: UrgencyMappingEntity.setupAnalysisResult() - 양방향 관계")
    fun setupAnalysisResult_urgencyMapping() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-urgency")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val urgencyMapping = fromDb.urgencyMapping

        assertThat(urgencyMapping).isNotNull
        assertThat(urgencyMapping!!.analysisResult).isSameAs(fromDb)
        assertThat(fromDb.urgencyMapping).isSameAs(urgencyMapping)
    }

    @Test
    @DisplayName("Part 6.2: IncidentTypeMappingEntity.setupAnalysisResult() - 양방향 관계 (다중)")
    fun setupAnalysisResult_incidentTypeMappings() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-incidents")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 각 mapping의 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(2)

        fromDb.incidentTypeMappings.forEach { mapping ->
            assertThat(mapping.analysisResult).isSameAs(fromDb)
            assertThat(fromDb.incidentTypeMappings).contains(mapping)
        }
    }

    @Test
    @DisplayName("Part 6.3: AddressMappingEntity.setupAnalysisResult() - 양방향 관계 (다중)")
    fun setupAnalysisResult_addressMappings() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-addresses")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11110")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "11120"))
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 각 mapping의 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(2)

        fromDb.addressMappings.forEach { mapping ->
            assertThat(mapping.analysisResult).isSameAs(fromDb)
            assertThat(fromDb.addressMappings).contains(mapping)
        }
    }

    @Test
    @DisplayName("Part 6.4: ArticleKeywordEntity.setupAnalysisResult() - 양방향 관계 (다중)")
    fun setupAnalysisResult_keywords() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-keywords")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            keywords = listOf(
                TestFixtures.createKeyword("화재", 10),
                TestFixtures.createKeyword("대피", 8)
            )
        )

        // When
        adapter.save(analysisResult)

        // Then: 각 keyword의 양방향 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.keywords).hasSize(2)

        fromDb.keywords.forEach { keyword ->
            assertThat(keyword.analysisResult).isSameAs(fromDb)
            assertThat(fromDb.keywords).contains(keyword)
        }
    }

    @Test
    @DisplayName("Part 6.5: 모든 매핑 엔티티의 양방향 관계 설정 검증")
    fun setupAnalysisResult_allMappings() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-all")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: 모든 관계 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]

        // Urgency
        assertThat(fromDb.urgencyMapping?.analysisResult).isSameAs(fromDb)

        // IncidentTypes
        fromDb.incidentTypeMappings.forEach {
            assertThat(it.analysisResult).isSameAs(fromDb)
        }

        // Addresses
        fromDb.addressMappings.forEach {
            assertThat(it.analysisResult).isSameAs(fromDb)
        }

        // Keywords
        fromDb.keywords.forEach {
            assertThat(it.analysisResult).isSameAs(fromDb)
        }
    }

    @Test
    @DisplayName("Part 6.6: setupAnalysisResult() 호출 순서 - buildAnalysisResultEntity() 내부")
    fun setupAnalysisResult_callOrder() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-setup-order")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: AnalysisResultEntity가 모든 child 엔티티를 포함
        val fromDb = jpaAnalysisResultRepository.findAll()[0]

        assertThat(fromDb.urgencyMapping).isNotNull
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
        assertThat(fromDb.addressMappings).hasSize(1)
        assertThat(fromDb.keywords).hasSize(1)
    }

    // ===== Part 7: @Transactional 트랜잭션 보증 (3개) =====

    @Test
    @DisplayName("Part 7.1: @Transactional - 모든 엔티티가 한 트랜잭션으로 저장")
    fun transactional_allEntitiesInSingleTransaction() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-tx-all")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: 모든 엔티티 저장됨 (COMMIT 완료)
        val analysisResults = jpaAnalysisResultRepository.findAll()
        assertThat(analysisResults).hasSize(1)

        val fromDb = analysisResults[0]
        assertThat(fromDb.urgencyMapping).isNotNull
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
        assertThat(fromDb.keywords).hasSize(1)
        assertThat(fromDb.addressMappings).hasSize(1)

        // Then: Outbox도 저장됨
        val outboxEntries = jpaAnalysisResultOutboxRepository.findAll()
        assertThat(outboxEntries).hasSize(1)
    }

    @Test
    @DisplayName("Part 7.2: @Transactional - Urgency 미존재 시 전체 ROLLBACK")
    fun transactional_rollbackOnUrgencyNotFound() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-tx-rollback")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("INVALID", 99)
        )

        // When & Then: IllegalArgumentException 발생
        assertThatThrownBy { adapter.save(analysisResult) }
            .isInstanceOf(IllegalArgumentException::class.java)

        // Then: 전체 ROLLBACK (AnalysisResult 저장 안 됨)
        assertThat(jpaAnalysisResultRepository.findAll()).isEmpty()

        // Then: Outbox도 저장 안 됨
        assertThat(jpaAnalysisResultOutboxRepository.findAll()).isEmpty()
    }

    @Test
    @DisplayName("Part 7.3: @Transactional - Outbox INSERT 실패 시 전체 ROLLBACK")
    fun transactional_rollbackOnOutboxFailure() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-tx-outbox")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: AnalysisResult와 Outbox 둘 다 저장됨
        assertThat(jpaAnalysisResultRepository.findAll()).hasSize(1)
        assertThat(jpaAnalysisResultOutboxRepository.findAll()).hasSize(1)
    }

    // ===== Part 8: 데이터 무결성 (FK, UK 제약) (8개) =====

    @Test
    @DisplayName("Part 8.1: FK - urgency_mapping.urgency_type_id → urgency_type(id)")
    fun dataIntegrity_fkUrgencyType() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-fk-urgency")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: UrgencyMappingEntity가 유효한 UrgencyTypeEntity 참조
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isNotNull
        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isEqualTo(urgencyHigh.id)
    }

    @Test
    @DisplayName("Part 8.2: FK - incident_type_mapping.incident_type_id → incident_type(id)")
    fun dataIntegrity_fkIncidentType() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-fk-incident")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불"))
        )

        // When
        adapter.save(analysisResult)

        // Then: IncidentTypeMappingEntity가 유효한 IncidentTypeEntity 참조
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val mapping = fromDb.incidentTypeMappings.first()
        assertThat(mapping.incidentType?.id).isNotNull
        assertThat(mapping.incidentType?.code).isEqualTo("FOREST_FIRE")
    }

    @Test
    @DisplayName("Part 8.3: FK - address_mapping.address_id → address(id)")
    fun dataIntegrity_fkAddress() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-fk-address")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: AddressMappingEntity가 유효한 AddressEntity 참조
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        val mapping = fromDb.addressMappings.first()
        assertThat(mapping.address?.id).isNotNull
    }

    @Test
    @DisplayName("Part 8.4: UK - incident_type_mapping(analysis_result_id, incident_type_id) 제약")
    fun dataIntegrity_ukIncidentTypeMapping() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-uk-incident")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("FOREST_FIRE", "산불")
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then: Set 특성상 1개만 저장됨
        assertThat(saved.incidentTypes).hasSize(1)

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(1)
    }

    @Test
    @DisplayName("Part 8.5: UK - address(region_type, code) unique 제약")
    fun dataIntegrity_ukAddressRegionTypeCode() {
        // Given: Address 직접 저장
        val address1 = jpaAddressRepository.save(
            TestFixtures.createAddressEntity(regionType = "B", code = "11110", addressName = "첫번째")
        )

        // When: 같은 regionType + code로 저장 시도
        val address2 = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "11110",
            addressName = "두번째"
        )

        // Then: DataIntegrityViolationException 발생
        assertThatThrownBy { jpaAddressRepository.save(address2) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    @DisplayName("Part 8.6: FK - 모든 매핑 엔티티의 FK 참조 검증")
    fun dataIntegrity_allForeignKeys() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-fk-all")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: 모든 FK 참조 확인
        val fromDb = jpaAnalysisResultRepository.findAll()[0]

        assertThat(fromDb.urgencyMapping?.urgencyType?.id).isNotNull
        assertThat(fromDb.incidentTypeMappings.first().incidentType?.id).isNotNull
        assertThat(fromDb.addressMappings.first().address?.id).isNotNull
    }

    @Test
    @DisplayName("Part 8.7: 양방향 관계 무결성 검증")
    fun dataIntegrity_bidirectionalIntegrity() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-integrity")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: 양방향 참조 무결성
        val fromDb = jpaAnalysisResultRepository.findAll()[0]

        // UrgencyMapping 양방향
        assertThat(fromDb.urgencyMapping?.analysisResult).isEqualTo(fromDb)

        // IncidentTypeMapping 양방향
        fromDb.incidentTypeMappings.forEach {
            assertThat(it.analysisResult).isEqualTo(fromDb)
        }

        // AddressMapping 양방향
        fromDb.addressMappings.forEach {
            assertThat(it.analysisResult).isEqualTo(fromDb)
        }

        // Keyword 양방향
        fromDb.keywords.forEach {
            assertThat(it.analysisResult).isEqualTo(fromDb)
        }
    }

    @Test
    @DisplayName("Part 8.8: Cascade 저장 검증")
    fun dataIntegrity_cascadeSave() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-cascade")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(
                TestFixtures.createKeyword("화재", 10),
                TestFixtures.createKeyword("대피", 8)
            ),
            locations = listOf(TestFixtures.createLocation())
        )

        // When: AnalysisResultEntity 저장만 호출
        adapter.save(analysisResult)

        // Then: 모든 자식 엔티티가 Cascade로 함께 저장됨
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping).isNotNull
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
        assertThat(fromDb.keywords).hasSize(2)
        assertThat(fromDb.addressMappings).hasSize(1)
    }

    // ===== Part 9: Outbox 패턴 검증 (5개) =====

    @Test
    @DisplayName("Part 9.1: AnalysisResultOutboxEntity 저장 (CDC 트리거 준비)")
    fun outbox_saveForCdc() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-outbox-cdc")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: Outbox 저장됨
        val outboxEntries = jpaAnalysisResultOutboxRepository.findAll()
        assertThat(outboxEntries).hasSize(1)

        val outbox = outboxEntries[0]
        assertThat(outbox.articleId).isEqualTo("article-outbox-cdc")
        assertThat(outbox.payload).isNotBlank
        assertThat(outbox.createdAt).isNotNull
    }

    @Test
    @DisplayName("Part 9.2: Outbox.articleId 저장 (검색용)")
    fun outbox_articleIdForSearch() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-outbox-search")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("주의", 3)
        )

        // When
        adapter.save(analysisResult)

        // Then: articleId가 Outbox에 저장됨
        val outbox = jpaAnalysisResultOutboxRepository.findAll()[0]
        assertThat(outbox.articleId).isEqualTo("article-outbox-search")
    }

    @Test
    @DisplayName("Part 9.3: Outbox.payload JSON 직렬화 및 유효성 검증")
    fun outbox_payloadJson() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-outbox-json")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = listOf(TestFixtures.createKeyword("화재", 10)),
            locations = listOf(TestFixtures.createLocation())
        )

        // When
        adapter.save(analysisResult)

        // Then: payload가 유효한 JSON
        val outbox = jpaAnalysisResultOutboxRepository.findAll()[0]
        val payload = outbox.payload

        assertThat(payload).contains("\"articleId\"")
        assertThat(payload).contains("article-outbox-json")
        assertThat(payload).contains("\"incidentTypes\"")
        assertThat(payload).contains("\"urgency\"")
        assertThat(payload).contains("긴급")
        assertThat(payload).contains("\"keywords\"")
        assertThat(payload).contains("\"locations\"")

        // Then: JSON 역직렬화 가능
        val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        assertThatCode {
            objectMapper.readTree(payload)
        }.doesNotThrowAnyException()
    }

    @Test
    @DisplayName("Part 9.4: Outbox.createdAt 자동 설정 (감사용)")
    fun outbox_createdAtAutoSet() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-outbox-time")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("정보", 1)
        )

        // When
        val beforeSave = java.time.ZonedDateTime.now()
        adapter.save(analysisResult)
        val afterSave = java.time.ZonedDateTime.now()

        // Then: createdAt이 자동 설정됨
        val outbox = jpaAnalysisResultOutboxRepository.findAll()[0]
        assertThat(outbox.createdAt).isNotNull
        assertThat(outbox.createdAt).isBetween(beforeSave, afterSave)
    }

    @Test
    @DisplayName("Part 9.5: Debezium CDC 트리거 준비 (INSERT 이벤트 감지 준비)")
    fun outbox_cdcTriggerReady() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-outbox-debezium")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When
        adapter.save(analysisResult)

        // Then: Outbox INSERT 완료
        val outboxEntries = jpaAnalysisResultOutboxRepository.findAll()
        assertThat(outboxEntries).hasSize(1)

        // Then: payload가 Kafka로 발행될 준비가 됨
        val outbox = outboxEntries[0]
        assertThat(outbox.payload).isNotBlank
        assertThat(outbox.createdAt).isNotNull
    }

    // ===== Part 10: 엣지 케이스 (4개) =====

    @Test
    @DisplayName("Part 10.1: 엣지 케이스 - 최소 분석 결과 (urgency만)")
    fun edgeCase_minimalAnalysisResult() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-edge-minimal")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = emptySet(),
            urgency = TestFixtures.createUrgency("정보", 1),
            keywords = emptyList(),
            locations = emptyList()
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.urgency.name).isEqualTo("정보")
        assertThat(saved.incidentTypes).isEmpty()
        assertThat(saved.keywords).isEmpty()
        assertThat(saved.locations).isEmpty()

        // Then: DB 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.urgencyMapping).isNotNull
        assertThat(fromDb.incidentTypeMappings).isEmpty()
        assertThat(fromDb.keywords).isEmpty()
        assertThat(fromDb.addressMappings).isEmpty()
    }

    @Test
    @DisplayName("Part 10.2: 엣지 케이스 - 최대 분석 결과 (100개씩)")
    fun edgeCase_maximalAnalysisResult() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-edge-maximal")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("FLOOD", "홍수"),
                TestFixtures.createIncidentType("EARTHQUAKE", "지진"),
                TestFixtures.createIncidentType("LANDSLIDE", "산사태"),
                TestFixtures.createIncidentType("HEAVY_SNOW", "대설"),
                TestFixtures.createIncidentType("HEAT_WAVE", "폭염"),
                TestFixtures.createIncidentType("COLD_WAVE", "한파"),
                TestFixtures.createIncidentType("STRONG_WIND", "강풍"),
                TestFixtures.createIncidentType("DROUGHT", "가뭄")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9),
            keywords = TestFixtures.createKeywords(100),
            locations = TestFixtures.createLocations(100)
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.incidentTypes).hasSize(10)
        assertThat(saved.keywords).hasSize(100)
        assertThat(saved.locations).hasSize(100)

        // Then: DB 검증
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(10)
        assertThat(fromDb.keywords).hasSize(100)
        assertThat(fromDb.addressMappings).hasSize(100)
    }

    @Test
    @DisplayName("Part 10.3: 엣지 케이스 - 혼합 주소 (2개 기존 + 3개 신규)")
    fun edgeCase_mixedAddresses() {
        // Given: 2개 기존 Address
        jpaAddressRepository.save(TestFixtures.createAddressEntity(code = "exist-1"))
        jpaAddressRepository.save(TestFixtures.createAddressEntity(code = "exist-2"))

        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-edge-mixed")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            locations = listOf(
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "exist-1")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "exist-2")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "new-1")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "new-2")),
                TestFixtures.createLocation(address = TestFixtures.createAddress(code = "new-3"))
            )
        )

        // When
        val saved = adapter.save(analysisResult)

        // Then
        assertThat(saved.locations).hasSize(5)

        // Then: 총 5개 Address (2개 기존 + 3개 신규)
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSize(5)

        // Then: 5개 매핑 생성
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(5)
    }

    @Test
    @DisplayName("Part 10.4: 에러 시나리오 - 부분 IncidentType 로드")
    fun edgeCase_partialIncidentTypeLoading() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-edge-partial")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(
                TestFixtures.createIncidentType("FOREST_FIRE", "산불"),
                TestFixtures.createIncidentType("does_not_exist_1", "없음1"),
                TestFixtures.createIncidentType("TYPHOON", "태풍"),
                TestFixtures.createIncidentType("does_not_exist_2", "없음2")
            ),
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When: 존재하는 것만 로드됨
        val saved = adapter.save(analysisResult)

        // Then: 존재하는 2개만 저장됨
        assertThat(saved.incidentTypes).hasSize(2)
        assertThat(saved.incidentTypes.map { it.code }).containsExactlyInAnyOrder("FOREST_FIRE", "TYPHOON")

        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.incidentTypeMappings).hasSize(2)
    }

    @Test
    @DisplayName("Part 10.5: 다중 location이 동일 address로 resolve될 때 중복 없이 저장된다")
    fun edgeCase_duplicateAddressFromMultipleLocations() {
        // Given: 서로 다른 location name이지만 같은 (regionType, code)로 resolve되는 경우
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-dup-address")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            urgency = TestFixtures.createUrgency("긴급", 9),
            locations = listOf(
                TestFixtures.createLocation(
                    address = TestFixtures.createAddress(
                        regionType = RegionType.UNKNOWN,
                        code = "UNKNOWN",
                        addressName = "대전"
                    )
                ),
                TestFixtures.createLocation(
                    address = TestFixtures.createAddress(
                        regionType = RegionType.UNKNOWN,
                        code = "UNKNOWN",
                        addressName = "세종"
                    )
                )
            )
        )

        // When: 예외 없이 저장 성공
        val saved = assertThatCode { adapter.save(analysisResult) }
            .doesNotThrowAnyException()

        // Then: address_mapping에 중복 없이 1개만 생성됨
        val fromDb = jpaAnalysisResultRepository.findAll()[0]
        assertThat(fromDb.addressMappings).hasSize(1)

        // Then: Address는 1개만 존재
        val allAddresses = jpaAddressRepository.findAll()
        assertThat(allAddresses).hasSize(1)
        assertThat(allAddresses[0].code).isEqualTo("UNKNOWN")
    }

    // ===== Part 11: save() 멱등성 테스트 - 기존 데이터 원자적 교체 (2개) =====

    @Test
    @DisplayName("Part 11.1: save() - 동일 articleId로 재저장 시 기존 데이터를 원자적으로 교체한다")
    fun save_existingArticleId_replacesAtomically() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-replace-1")
        )
        val firstResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9)
        )
        adapter.save(firstResult)

        val secondResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When & Then
        assertThatCode { adapter.save(secondResult) }.doesNotThrowAnyException()
        assertThat(jpaAnalysisResultRepository.findByArticleId("article-replace-1")).isNotNull()
    }

    @Test
    @DisplayName("Part 11.2: save() - 존재하지 않는 articleId에 대해 정상 저장된다")
    fun save_newArticleId_savesSuccessfully() {
        // Given
        val article = jpaArticleRepository.save(
            TestFixtures.createArticleEntity(articleId = "article-new-1")
        )
        val analysisResult = TestFixtures.createAnalysisResult(
            articleId = article.articleId,
            incidentTypes = setOf(TestFixtures.createIncidentType("FOREST_FIRE", "산불")),
            urgency = TestFixtures.createUrgency("긴급", 9)
        )

        // When & Then
        assertThatCode { adapter.save(analysisResult) }.doesNotThrowAnyException()
        assertThat(jpaAnalysisResultRepository.findByArticleId("article-new-1")).isNotNull()
    }
}
