package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.entity.analysis.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AnalysisResultMapper 테스트")
class AnalysisResultMapperTest {

    // ===== toDomainModel() - articleId 추출 테스트 =====
    @Test
    @DisplayName("toDomainModel: articleId 정확히 추출")
    fun toDomainModel_articleIdExtraction() {
        val articleEntity = TestFixtures.createArticleEntity(articleId = "article-123")
        val urgencyEntity = TestFixtures.createUrgencyEntity(name = "HIGH", level = 3)

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.articleId).isEqualTo("article-123")
    }

    // ===== toDomainModel() - incidentTypeMappings 변환 테스트 =====
    @Test
    @DisplayName("toDomainModel: 빈 incidentTypeMappings - 빈 Set 반환")
    fun toDomainModel_emptyIncidentTypeMappings() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.incidentTypes).isEmpty()
    }

    @Test
    @DisplayName("toDomainModel: 1개 incident type 변환")
    fun toDomainModel_singleIncidentType() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()
        val incidentTypeEntity = TestFixtures.createIncidentTypeEntity(code = "fire", name = "산불")

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = incidentTypeEntity
                )
            )
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.incidentTypes).hasSize(1)
        assertThat(domain.incidentTypes.first().code).isEqualTo("fire")
        assertThat(domain.incidentTypes.first().name).isEqualTo("산불")
    }

    @Test
    @DisplayName("toDomainModel: 5개 incident types 변환")
    fun toDomainModel_multipleIncidentTypes() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "fire", name = "산불")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "typhoon", name = "태풍")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "flood", name = "홍수")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "earthquake", name = "지진")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "landslide", name = "산사태")
                )
            )
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.incidentTypes).hasSize(5)
        val codes = domain.incidentTypes.map { it.code }
        assertThat(codes).containsExactlyInAnyOrder("fire", "typhoon", "flood", "earthquake", "landslide")
    }

    @Test
    @DisplayName("toDomainModel: null incidentType 필터링")
    fun toDomainModel_nullIncidentTypeFiltering() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "fire", name = "산불")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = null  // null 포함
                )
            )
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.incidentTypes).hasSize(1)
        assertThat(domain.incidentTypes.first().code).isEqualTo("fire")
    }

    // ===== toDomainModel() - urgencyMapping 변환 테스트 =====
    @Test
    @DisplayName("toDomainModel: urgency 변환 - UrgencyMapper 위임")
    fun toDomainModel_urgencyConversion() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity(name = "HIGH", level = 3)

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.urgency.name).isEqualTo("HIGH")
        assertThat(domain.urgency.level).isEqualTo(3)
    }

    // ===== toDomainModel() - keywords 변환 테스트 =====
    @Test
    @DisplayName("toDomainModel: 빈 keywords - 빈 List 반환")
    fun toDomainModel_emptyKeywords() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.keywords).isEmpty()
    }

    @Test
    @DisplayName("toDomainModel: 1개 keyword 변환")
    fun toDomainModel_singleKeyword() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            val keywordEntity = ArticleKeywordEntity(
                keyword = "화재",
                priority = 10
            )
            keywordEntity.setupAnalysisResult(this)
            keywords.add(keywordEntity)
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.keywords).hasSize(1)
        assertThat(domain.keywords.first().keyword).isEqualTo("화재")
        assertThat(domain.keywords.first().priority).isEqualTo(10)
    }

    @Test
    @DisplayName("toDomainModel: 10개 keywords 변환")
    fun toDomainModel_multipleKeywords() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            for (i in 1..10) {
                val keywordEntity = ArticleKeywordEntity(
                    keyword = "keyword_$i",
                    priority = 11 - i
                )
                keywordEntity.setupAnalysisResult(this)
                keywords.add(keywordEntity)
            }
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.keywords).hasSize(10)
    }

    // ===== toDomainModel() - addressMappings 변환 테스트 =====
    @Test
    @DisplayName("toDomainModel: 빈 addressMappings - 빈 List 반환")
    fun toDomainModel_emptyAddressMappings() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.locations).isEmpty()
    }

    @Test
    @DisplayName("toDomainModel: 1개 location 변환")
    fun toDomainModel_singleLocation() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()
        val coordinateEntity = TestFixtures.createCoordinateEntity(latitude = 37.4979, longitude = 126.9270)
        val addressEntity = TestFixtures.createAddressEntity(
            regionType = "B",
            code = "11110"
        ).apply {
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            addressMappings.add(
                AddressMappingEntity(
                    analysisResult = this,
                    address = addressEntity
                )
            )
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.locations).hasSize(1)
        assertThat(domain.locations.first().coordinate!!.lat).isEqualTo(37.4979)
        assertThat(domain.locations.first().coordinate!!.lon).isEqualTo(126.9270)
    }

    @Test
    @DisplayName("toDomainModel: 5개 locations 변환")
    fun toDomainModel_multipleLocations() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            for (i in 1..5) {
                val coordinateEntity = TestFixtures.createCoordinateEntity(
                    latitude = 37.4979 + i * 0.001,
                    longitude = 126.9270 + i * 0.001
                )
                val addressEntity = TestFixtures.createAddressEntity(
                    regionType = "B",
                    code = "1111$i"
                ).apply {
                    this.coordinate = coordinateEntity
                    coordinateEntity.address = this
                }

                addressMappings.add(
                    AddressMappingEntity(
                        analysisResult = this,
                        address = addressEntity
                    )
                )
            }
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.locations).hasSize(5)
    }

    @Test
    @DisplayName("toDomainModel: null address 필터링")
    fun toDomainModel_nullAddressFiltering() {
        val articleEntity = TestFixtures.createArticleEntity()
        val urgencyEntity = TestFixtures.createUrgencyEntity()
        val coordinateEntity = TestFixtures.createCoordinateEntity()
        val addressEntity = TestFixtures.createAddressEntity().apply {
            this.coordinate = coordinateEntity
            coordinateEntity.address = this
        }

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
            addressMappings.add(
                AddressMappingEntity(
                    analysisResult = this,
                    address = addressEntity
                )
            )
            addressMappings.add(
                AddressMappingEntity(
                    analysisResult = this,
                    address = null  // null 포함
                )
            )
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.locations).hasSize(1)
    }

    // ===== 컬렉션 크기 검증 테스트 =====
    @Test
    @DisplayName("toDomainModel: 최소 분석 결과 - urgency만 있음")
    fun toDomainModel_minimalAnalysisResult() {
        val articleEntity = TestFixtures.createArticleEntity(articleId = "article-min")
        val urgencyEntity = TestFixtures.createUrgencyEntity(name = "LOW", level = 1)

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.articleId).isEqualTo("article-min")
        assertThat(domain.urgency.name).isEqualTo("LOW")
        assertThat(domain.incidentTypes).isEmpty()
        assertThat(domain.keywords).isEmpty()
        assertThat(domain.locations).isEmpty()
    }

    @Test
    @DisplayName("toDomainModel: 중간 분석 결과 - urgency + 3 types + 3 keywords + 3 locations")
    fun toDomainModel_mediumAnalysisResult() {
        val articleEntity = TestFixtures.createArticleEntity(articleId = "article-medium")
        val urgencyEntity = TestFixtures.createUrgencyEntity(name = "MEDIUM", level = 2)

        val entity = AnalysisResultEntity(
            articleId = articleEntity.articleId,
            urgencyMapping = UrgencyMappingEntity(urgencyType = urgencyEntity)
        ).apply {
            urgencyMapping!!.analysisResult = this

            // 3 incident types
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "fire", name = "산불")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "typhoon", name = "태풍")
                )
            )
            incidentTypeMappings.add(
                IncidentTypeMappingEntity(
                    analysisResult = this,
                    incidentType = TestFixtures.createIncidentTypeEntity(code = "flood", name = "홍수")
                )
            )

            // 3 keywords
            val keyword1 = ArticleKeywordEntity(keyword = "화재", priority = 10)
            keyword1.setupAnalysisResult(this)
            keywords.add(keyword1)

            val keyword2 = ArticleKeywordEntity(keyword = "대피", priority = 8)
            keyword2.setupAnalysisResult(this)
            keywords.add(keyword2)

            val keyword3 = ArticleKeywordEntity(keyword = "경고", priority = 5)
            keyword3.setupAnalysisResult(this)
            keywords.add(keyword3)

            // 3 locations
            for (i in 1..3) {
                val coordinateEntity = TestFixtures.createCoordinateEntity(
                    latitude = 37.4979 + i * 0.001,
                    longitude = 126.9270 + i * 0.001
                )
                val addressEntity = TestFixtures.createAddressEntity(code = "1111$i").apply {
                    this.coordinate = coordinateEntity
                    coordinateEntity.address = this
                }
                addressMappings.add(AddressMappingEntity(analysisResult = this, address = addressEntity))
            }
        }

        val domain = AnalysisResultMapper.toDomainModel(entity)

        assertThat(domain.articleId).isEqualTo("article-medium")
        assertThat(domain.urgency.name).isEqualTo("MEDIUM")
        assertThat(domain.incidentTypes).hasSize(3)
        assertThat(domain.keywords).hasSize(3)
        assertThat(domain.locations).hasSize(3)
    }
}