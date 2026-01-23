package com.vonkernel.lit.persistence

import com.vonkernel.lit.entity.Address
import com.vonkernel.lit.entity.AnalysisResult
import com.vonkernel.lit.entity.Article
import com.vonkernel.lit.entity.Coordinate
import com.vonkernel.lit.entity.IncidentType
import com.vonkernel.lit.entity.Keyword
import com.vonkernel.lit.entity.Location
import com.vonkernel.lit.entity.RegionType
import com.vonkernel.lit.entity.Urgency
import com.vonkernel.lit.persistence.entity.analysis.ArticleKeywordEntity
import com.vonkernel.lit.persistence.entity.analysis.AddressCoordinateEntity
import com.vonkernel.lit.persistence.entity.analysis.AddressEntity
import com.vonkernel.lit.persistence.entity.analysis.AnalysisResultEntity
import com.vonkernel.lit.persistence.entity.core.ArticleEntity
import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity
import com.vonkernel.lit.persistence.entity.core.UrgencyTypeEntity
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.random.Random

object TestFixtures {

    // ===== Article (Domain Model) =====
    fun createArticle(
        articleId: String = "test-article-${System.nanoTime()}",
        originId: String = "news-${Random.nextInt()}",
        sourceId: String = "yonhapnews",
        writtenAt: Instant = Instant.now(),
        modifiedAt: Instant = Instant.now(),
        title: String = "테스트 기사 제목",
        content: String = "테스트 기사 본문",
        sourceUrl: String? = "https://example.com/article"
    ) = Article(articleId, originId, sourceId, writtenAt, modifiedAt, title, content, sourceUrl)

    // ===== ArticleEntity (Persistence Model) =====
    fun createArticleEntity(
        articleId: String = "test-article-${System.nanoTime()}",
        originId: String = "news-123",
        sourceId: String = "yonhapnews",
        writtenAt: ZonedDateTime = ZonedDateTime.now(),
        modifiedAt: ZonedDateTime = ZonedDateTime.now(),
        title: String = "테스트 기사",
        content: String = "테스트 내용",
        sourceUrl: String? = null
    ) = ArticleEntity(
        articleId = articleId,
        originId = originId,
        sourceId = sourceId,
        writtenAt = writtenAt,
        modifiedAt = modifiedAt,
        title = title,
        content = content,
        sourceUrl = sourceUrl
    )

    // ===== AnalysisResult (Domain Model) =====
    fun createAnalysisResult(
        articleId: String = "test-article-1",
        incidentTypes: Set<IncidentType> = setOf(
            IncidentType("fire", "산불"),
            IncidentType("typhoon", "태풍")
        ),
        urgency: Urgency = Urgency("HIGH", 3),
        keywords: List<Keyword> = listOf(
            Keyword("화재", 10),
            Keyword("대피", 8)
        ),
        locations: List<Location> = listOf(createLocation())
    ) = AnalysisResult(articleId, incidentTypes, urgency, keywords, locations)

    // ===== Coordinate (Domain Model) =====
    fun createCoordinate(
        lat: Double = 37.4979,
        lon: Double = 126.9270
    ) = Coordinate(lat, lon)

    // ===== AddressCoordinateEntity (Persistence Model) =====
    fun createCoordinateEntity(
        latitude: Double = 37.4979,
        longitude: Double = 126.9270
    ) = AddressCoordinateEntity(
        latitude = latitude,
        longitude = longitude
    )

    // ===== Address (Domain Model) =====
    fun createAddress(
        regionType: RegionType = RegionType.BJDONG,
        code: String = "11110",
        addressName: String = "서울특별시 강남구",
        depth1Name: String? = "서울특별시",
        depth2Name: String? = "강남구",
        depth3Name: String? = "강남동"
    ) = Address(regionType, code, addressName, depth1Name, depth2Name, depth3Name)

    // ===== AddressEntity (Persistence Model) =====
    fun createAddressEntity(
        regionType: String = "B",
        code: String = "11110",
        addressName: String = "서울특별시 강남구",
        depth1Name: String? = "서울특별시",
        depth2Name: String? = "강남구",
        depth3Name: String? = "강남동"
    ) = AddressEntity(
        regionType = regionType,
        code = code,
        addressName = addressName,
        depth1Name = depth1Name,
        depth2Name = depth2Name,
        depth3Name = depth3Name
    )

    // ===== Location (Domain Model) =====
    fun createLocation(
        coordinate: Coordinate = createCoordinate(),
        address: Address = createAddress()
    ) = Location(coordinate, address)

    // ===== Urgency (Domain Model) =====
    fun createUrgency(name: String = "HIGH", level: Int = 3) = Urgency(name, level)

    // ===== UrgencyTypeEntity (Persistence Model) =====
    fun createUrgencyEntity(name: String = "HIGH", level: Int = 3) = UrgencyTypeEntity(
        name = name,
        level = level
    )

    // ===== IncidentType (Domain Model) =====
    fun createIncidentType(code: String = "fire", name: String = "산불") = IncidentType(code, name)

    // ===== IncidentTypeEntity (Persistence Model) =====
    fun createIncidentTypeEntity(code: String = "fire", name: String = "산불") = IncidentTypeEntity(
        code = code,
        name = name
    )

    // ===== Keyword (Domain Model) =====
    fun createKeyword(keyword: String = "화재", priority: Int = 10) = Keyword(keyword, priority)

    // ===== ArticleKeywordEntity (Persistence Model) =====
    fun createKeywordEntity(keyword: String = "화재", priority: Int = 10) = ArticleKeywordEntity(
        keyword = keyword,
        priority = priority
    )

    // ===== 대량 데이터 생성 =====
    fun createIncidentTypes(count: Int) = (1..count).map { i ->
        IncidentType("type_$i", "타입_$i")
    }.toSet()

    fun createKeywords(count: Int) = (1..count).map { i ->
        Keyword("keyword_$i", count - i)
    }

    fun createLocations(count: Int) = (1..count).map { i ->
        Location(
            coordinate = Coordinate(37.4979 + i * 0.001, 126.9270 + i * 0.001),
            address = Address(
                regionType = if (i % 2 == 0) RegionType.BJDONG else RegionType.HADONG,
                code = "1111${String.format("%02d", i)}",
                addressName = "주소_$i"
            )
        )
    }
}