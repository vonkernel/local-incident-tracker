package com.vonkernel.lit.indexer.domain.assembler

import com.vonkernel.lit.core.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class IndexDocumentAssemblerTest {

    private val sampleAnalysisResult = AnalysisResult(
        articleId = "2026-01-30-4903",
        refinedArticle = RefinedArticle(
            title = "경부선 열차사고",
            content = "30일 대구지법에서 열차사고 재판이 열렸다.",
            summary = "열차사고 재판 요약",
            writtenAt = Instant.parse("2026-01-30T03:21:55Z")
        ),
        incidentTypes = setOf(
            IncidentType(code = "TRAFFIC_ACCIDENT", name = "교통사고"),
            IncidentType(code = "DEATH", name = "사망")
        ),
        urgency = Urgency(name = "심각", level = 7),
        keywords = listOf(
            Keyword(keyword = "열차사고", priority = 10),
            Keyword(keyword = "사망", priority = 8)
        ),
        topic = Topic(topic = "경부선 열차사고"),
        locations = listOf(
            Location(
                coordinate = Coordinate(lat = 35.647, lon = 128.734),
                address = Address(
                    regionType = RegionType.HADONG,
                    code = "4782000000",
                    addressName = "경상북도 청도군",
                    depth1Name = "경북",
                    depth2Name = "청도군"
                )
            ),
            Location(
                coordinate = Coordinate(lat = 36.210, lon = 127.996),
                address = Address(
                    regionType = RegionType.HADONG,
                    code = "4374033500",
                    addressName = "경부선",
                    depth1Name = "충북",
                    depth2Name = "영동군",
                    depth3Name = "추풍령면"
                )
            )
        )
    )

    @Test
    fun `assemble은 모든 필드를 올바르게 매핑한다`() {
        val embedding = byteArrayOf(1, 2, 3, 4)
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult, embedding)

        assertEquals("2026-01-30-4903", document.articleId)
        assertEquals("경부선 열차사고", document.title)
        assertEquals("30일 대구지법에서 열차사고 재판이 열렸다.", document.content)
        assertEquals(listOf("열차사고", "사망"), document.keywords)
        assertArrayEquals(embedding, document.contentEmbedding)
        assertEquals(2, document.incidentTypes!!.size)
        assertEquals("심각", document.urgency!!.name)
        assertEquals(7, document.urgency!!.level)
    }

    @Test
    fun `assemble은 writtenAt Instant를 UTC ZonedDateTime으로 변환한다`() {
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult)

        val expected = ZonedDateTime.ofInstant(Instant.parse("2026-01-30T03:21:55Z"), ZoneOffset.UTC)
        assertEquals(expected, document.writtenAt)
        assertEquals(expected, document.incidentDate)
    }

    @Test
    fun `assemble은 좌표가 있는 위치에서 geoPoints를 추출한다`() {
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult)

        assertEquals(2, document.geoPoints!!.size)
        assertEquals(35.647, document.geoPoints!![0].lat)
        assertEquals(128.734, document.geoPoints!![0].lon)
    }

    @Test
    fun `assemble은 위치에서 주소를 추출한다`() {
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult)

        assertEquals(2, document.addresses!!.size)
        assertEquals("경상북도 청도군", document.addresses!![0].addressName)
        assertEquals("추풍령면", document.addresses!![1].depth3Name)
    }

    @Test
    fun `assemble은 jurisdictionCodes에서 UNKNOWN 코드를 필터링한다`() {
        val analysisResult = sampleAnalysisResult.copy(
            locations = listOf(
                Location(
                    coordinate = null,
                    address = Address(
                        regionType = RegionType.UNKNOWN,
                        code = "UNKNOWN",
                        addressName = "알 수 없음"
                    )
                ),
                Location(
                    coordinate = Coordinate(lat = 35.0, lon = 128.0),
                    address = Address(
                        regionType = RegionType.HADONG,
                        code = "4782000000",
                        addressName = "경상북도 청도군"
                    )
                )
            )
        )

        val document = IndexDocumentAssembler.assemble(analysisResult)

        assertEquals(setOf("4782000000"), document.jurisdictionCodes)
    }

    @Test
    fun `assemble은 null 좌표를 geoPoints에서 제외한다`() {
        val analysisResult = sampleAnalysisResult.copy(
            locations = listOf(
                Location(
                    coordinate = null,
                    address = Address(
                        regionType = RegionType.HADONG,
                        code = "4782000000",
                        addressName = "경상북도 청도군"
                    )
                )
            )
        )

        val document = IndexDocumentAssembler.assemble(analysisResult)

        assertTrue(document.geoPoints!!.isEmpty())
        assertEquals(1, document.addresses!!.size)
    }

    @Test
    fun `assemble은 빈 위치 목록을 처리한다`() {
        val analysisResult = sampleAnalysisResult.copy(locations = emptyList())

        val document = IndexDocumentAssembler.assemble(analysisResult)

        assertTrue(document.geoPoints!!.isEmpty())
        assertTrue(document.addresses!!.isEmpty())
        assertTrue(document.jurisdictionCodes!!.isEmpty())
    }

    @Test
    fun `assemble은 선택 필드를 null로 설정한다`() {
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult)

        assertNull(document.sourceId)
        assertNull(document.originId)
        assertNull(document.modifiedAt)
    }

    @Test
    fun `임베딩 없이 assemble하면 contentEmbedding이 null이다`() {
        val document = IndexDocumentAssembler.assemble(sampleAnalysisResult)

        assertNull(document.contentEmbedding)
    }
}
