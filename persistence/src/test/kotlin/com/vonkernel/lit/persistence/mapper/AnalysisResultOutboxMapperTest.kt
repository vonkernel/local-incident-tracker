package com.vonkernel.lit.persistence.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.mapper.AnalysisResultOutboxMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AnalysisResultOutboxMapper 테스트")
class AnalysisResultOutboxMapperTest {

    private lateinit var mapper: AnalysisResultOutboxMapper
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        mapper = AnalysisResultOutboxMapper(objectMapper)
    }

    // ===== toPersistenceModel() 기본 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: articleId 저장")
    fun toPersistenceModel_articleIdStored() {
        val analysisResult = TestFixtures.createAnalysisResult(articleId = "article-123")

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.articleId).isEqualTo("article-123")
    }

    @Test
    @DisplayName("toPersistenceModel: payload JSON 저장")
    fun toPersistenceModel_payloadStored() {
        val analysisResult = TestFixtures.createAnalysisResult(articleId = "article-123")

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).isNotBlank()
        assertThat(entity.payload).contains("article-123")
    }

    // ===== JSON 직렬화 정확성 테스트 =====
    @Test
    @DisplayName("JSON 직렬화: articleId 포함")
    fun jsonSerialization_articleIdIncluded() {
        val analysisResult = TestFixtures.createAnalysisResult(articleId = "article-123")

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"articleId\"")
        assertThat(entity.payload).contains("article-123")
    }

    @Test
    @DisplayName("JSON 직렬화: Set<IncidentType> 배열로 직렬화")
    fun jsonSerialization_incidentTypesAsArray() {
        val analysisResult = TestFixtures.createAnalysisResult(
            incidentTypes = TestFixtures.createIncidentTypes(3)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"incidentTypes\"")
        assertThat(entity.payload).contains("\"code\"")
        assertThat(entity.payload).contains("\"name\"")
    }

    @Test
    @DisplayName("JSON 직렬화: Urgency 객체로 직렬화")
    fun jsonSerialization_urgencyAsObject() {
        val analysisResult = TestFixtures.createAnalysisResult(
            urgency = TestFixtures.createUrgency(name = "HIGH", level = 3)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"urgency\"")
        assertThat(entity.payload).contains("\"name\"")
        assertThat(entity.payload).contains("HIGH")
        assertThat(entity.payload).contains("\"level\"")
        assertThat(entity.payload).contains("3")
    }

    @Test
    @DisplayName("JSON 직렬화: List<Keyword> 배열로 직렬화")
    fun jsonSerialization_keywordsAsArray() {
        val analysisResult = TestFixtures.createAnalysisResult(
            keywords = TestFixtures.createKeywords(5)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"keywords\"")
        assertThat(entity.payload).contains("\"keyword\"")
        assertThat(entity.payload).contains("\"priority\"")
    }

    @Test
    @DisplayName("JSON 직렬화: List<Location> 중첩 직렬화")
    fun jsonSerialization_locationsNested() {
        val analysisResult = TestFixtures.createAnalysisResult(
            locations = TestFixtures.createLocations(3)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"locations\"")
        assertThat(entity.payload).contains("\"coordinate\"")
        assertThat(entity.payload).contains("\"address\"")
        assertThat(entity.payload).contains("\"lat\"")
        assertThat(entity.payload).contains("\"lon\"")
    }

    @Test
    @DisplayName("JSON 직렬화: RegionType enum 문자열로 직렬화")
    fun jsonSerialization_regionTypeAsString() {
        val analysisResult = TestFixtures.createAnalysisResult(
            locations = listOf(
                TestFixtures.createLocation(
                    address = TestFixtures.createAddress(
                        regionType = com.vonkernel.lit.core.entity.RegionType.BJDONG
                    )
                )
            )
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\"regionType\"")
        assertThat(entity.payload).contains("BJDONG")
    }

    // ===== 역직렬화 가능성 테스트 =====
    @Test
    @DisplayName("역직렬화: payload가 유효한 JSON")
    fun deserialization_validJson() {
        val analysisResult = TestFixtures.createAnalysisResult()

        val entity = mapper.toPersistenceModel(analysisResult)

        // JSON 파싱이 에러 없이 성공해야 함
        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized).isNotNull
    }

    @Test
    @DisplayName("역직렬화: 역직렬화된 데이터 == 원본 데이터")
    fun deserialization_dataEquality() {
        val originalAnalysisResult = TestFixtures.createAnalysisResult(
            articleId = "article-123",
            incidentTypes = TestFixtures.createIncidentTypes(3),
            urgency = TestFixtures.createUrgency(name = "HIGH", level = 3),
            keywords = TestFixtures.createKeywords(5),
            locations = TestFixtures.createLocations(3)
        )

        val entity = mapper.toPersistenceModel(originalAnalysisResult)
        val deserializedAnalysisResult = objectMapper.readValue<AnalysisResult>(entity.payload)

        assertThat(deserializedAnalysisResult.articleId).isEqualTo(originalAnalysisResult.articleId)
        assertThat(deserializedAnalysisResult.urgency.name).isEqualTo(originalAnalysisResult.urgency.name)
        assertThat(deserializedAnalysisResult.urgency.level).isEqualTo(originalAnalysisResult.urgency.level)
        assertThat(deserializedAnalysisResult.incidentTypes.size).isEqualTo(originalAnalysisResult.incidentTypes.size)
        assertThat(deserializedAnalysisResult.keywords.size).isEqualTo(originalAnalysisResult.keywords.size)
        assertThat(deserializedAnalysisResult.locations.size).isEqualTo(originalAnalysisResult.locations.size)
    }

    // ===== 특수 문자 처리 테스트 =====
    @Test
    @DisplayName("특수 문자: 한글 키워드 처리")
    fun specialCharacters_koreanKeywords() {
        val analysisResult = TestFixtures.createAnalysisResult(
            keywords = listOf(
                TestFixtures.createKeyword(keyword = "화재", priority = 10),
                TestFixtures.createKeyword(keyword = "태풍", priority = 9),
                TestFixtures.createKeyword(keyword = "홍수", priority = 8)
            )
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("화재")
        assertThat(entity.payload).contains("태풍")
        assertThat(entity.payload).contains("홍수")

        // 역직렬화 검증
        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized.keywords.map { it.keyword }).containsExactlyInAnyOrder("화재", "태풍", "홍수")
    }

    @Test
    @DisplayName("특수 문자: 특수문자 이스케이핑")
    fun specialCharacters_escaping() {
        val analysisResult = TestFixtures.createAnalysisResult(
            keywords = listOf(
                TestFixtures.createKeyword(keyword = "@#\$%&*()", priority = 5)
            )
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        // JSON 파싱이 성공해야 함
        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized.keywords.first().keyword).isEqualTo("@#\$%&*()")
    }

    @Test
    @DisplayName("특수 문자: 줄바꿈 이스케이핑")
    fun specialCharacters_newlineEscaping() {
        val analysisResult = TestFixtures.createAnalysisResult(
            keywords = listOf(
                TestFixtures.createKeyword(keyword = "line1\nline2", priority = 5)
            )
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\\n")

        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized.keywords.first().keyword).isEqualTo("line1\nline2")
    }

    @Test
    @DisplayName("특수 문자: 따옴표 이스케이핑")
    fun specialCharacters_quoteEscaping() {
        val analysisResult = TestFixtures.createAnalysisResult(
            keywords = listOf(
                TestFixtures.createKeyword(keyword = "quote\"test", priority = 5)
            )
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload).contains("\\\"")

        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized.keywords.first().keyword).isEqualTo("quote\"test")
    }

    // ===== 크기 검증 테스트 =====
    @Test
    @DisplayName("크기: 최소 크기 - urgency만 있는 경우")
    fun size_minimal() {
        val analysisResult = TestFixtures.createAnalysisResult(
            incidentTypes = emptySet(),
            keywords = emptyList(),
            locations = emptyList()
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload.length).isGreaterThan(50)  // 최소한의 JSON 구조
        assertThat(entity.payload.length).isLessThan(500)    // 과도하게 크지 않음
    }

    @Test
    @DisplayName("크기: 표준 크기 - 모든 필드 채워진 경우")
    fun size_standard() {
        val analysisResult = TestFixtures.createAnalysisResult(
            incidentTypes = TestFixtures.createIncidentTypes(3),
            keywords = TestFixtures.createKeywords(5),
            locations = TestFixtures.createLocations(3)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload.length).isGreaterThan(500)   // 충분한 데이터
        assertThat(entity.payload.length).isLessThan(5000)     // 합리적인 크기
    }

    @Test
    @DisplayName("크기: 최대 크기 - 100개 컬렉션")
    fun size_maximum() {
        val analysisResult = TestFixtures.createAnalysisResult(
            incidentTypes = TestFixtures.createIncidentTypes(100),
            keywords = TestFixtures.createKeywords(100),
            locations = TestFixtures.createLocations(100)
        )

        val entity = mapper.toPersistenceModel(analysisResult)

        assertThat(entity.payload.length).isGreaterThan(10000)  // 대량 데이터

        // 역직렬화 가능성 검증
        val deserialized = objectMapper.readValue<AnalysisResult>(entity.payload)
        assertThat(deserialized.incidentTypes.size).isEqualTo(100)
        assertThat(deserialized.keywords.size).isEqualTo(100)
        assertThat(deserialized.locations.size).isEqualTo(100)
    }
}