package com.vonkernel.lit.indexer.adapter.outbound.opensearch

import com.vonkernel.lit.core.entity.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.*
import org.opensearch.client.opensearch._types.Result
import org.opensearch.client.util.ObjectBuilder
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OpenSearchArticleIndexerTest {

    private val openSearchClient = mockk<OpenSearchClient>()
    private lateinit var adapter: OpenSearchArticleIndexer

    private val sampleDocument = ArticleIndexDocument(
        articleId = "test-001",
        title = "테스트 기사",
        content = "본문",
        keywords = listOf("테스트"),
        incidentTypes = setOf(IncidentType(code = "FIRE", name = "화재")),
        urgency = Urgency(name = "긴급", level = 8),
        incidentDate = ZonedDateTime.of(2026, 1, 30, 3, 21, 55, 0, ZoneOffset.UTC),
        geoPoints = listOf(Coordinate(lat = 37.5665, lon = 126.9780)),
        addresses = listOf(
            Address(
                regionType = RegionType.BJDONG,
                code = "1111000000",
                addressName = "서울특별시"
            )
        ),
        jurisdictionCodes = setOf("1111000000"),
        writtenAt = ZonedDateTime.of(2026, 1, 30, 3, 21, 55, 0, ZoneOffset.UTC),
    )

    private val sampleDocument2 = ArticleIndexDocument(
        articleId = "test-002",
        title = "테스트 기사 2",
        content = "본문 2",
        keywords = listOf("홍수"),
        incidentTypes = setOf(IncidentType(code = "FLOOD", name = "홍수")),
        urgency = Urgency(name = "보통", level = 5),
        incidentDate = ZonedDateTime.of(2026, 1, 30, 4, 0, 0, 0, ZoneOffset.UTC),
        geoPoints = emptyList(),
        addresses = emptyList(),
        jurisdictionCodes = emptySet(),
        writtenAt = ZonedDateTime.of(2026, 1, 30, 4, 0, 0, 0, ZoneOffset.UTC),
    )

    @BeforeEach
    fun setUp() {
        adapter = OpenSearchArticleIndexer(openSearchClient, "articles")
    }

    @Test
    fun `index calls OpenSearch client with correct index and document id`() = runTest {
        val indexResponse = mockk<IndexResponse>()
        every { indexResponse.result() } returns Result.Created

        every { openSearchClient.index(any<IndexRequest<Map<String, Any?>>>()) } returns indexResponse

        adapter.index(sampleDocument)

        verify {
            openSearchClient.index(match<IndexRequest<Map<String, Any?>>> { request ->
                request.index() == "articles" && request.id() == "test-001"
            })
        }
    }

    @Test
    fun `delete calls OpenSearch client with correct index and id`() = runTest {
        val deleteResponse = mockk<DeleteResponse>()
        every { deleteResponse.result() } returns Result.Deleted

        every {
            openSearchClient.delete(any<java.util.function.Function<DeleteRequest.Builder, ObjectBuilder<DeleteRequest>>>())
        } returns deleteResponse

        adapter.delete("test-001")

        verify {
            openSearchClient.delete(any<java.util.function.Function<DeleteRequest.Builder, ObjectBuilder<DeleteRequest>>>())
        }
    }

    @Test
    fun `indexAll calls OpenSearch bulk API with all documents`() = runTest {
        val bulkResponse = mockk<BulkResponse>()
        every { bulkResponse.errors() } returns false
        every { bulkResponse.items() } returns emptyList()

        every { openSearchClient.bulk(any<BulkRequest>()) } returns bulkResponse

        adapter.indexAll(listOf(sampleDocument, sampleDocument2))

        verify {
            openSearchClient.bulk(any<BulkRequest>())
        }
    }

    @Test
    fun `indexAll does nothing for empty list`() = runTest {
        adapter.indexAll(emptyList())

        verify(exactly = 0) { openSearchClient.bulk(any<BulkRequest>()) }
    }
}
