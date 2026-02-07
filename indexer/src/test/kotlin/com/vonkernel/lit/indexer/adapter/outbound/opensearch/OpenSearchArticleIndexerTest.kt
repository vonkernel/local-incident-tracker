package com.vonkernel.lit.indexer.adapter.outbound.opensearch

import com.vonkernel.lit.core.entity.*
import com.vonkernel.lit.indexer.domain.exception.BulkIndexingPartialFailureException
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.*
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem
import org.opensearch.client.opensearch._types.ErrorCause
import org.opensearch.client.opensearch._types.Result
import org.opensearch.client.util.ObjectBuilder
import java.time.Instant
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

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `findModifiedAtByArticleId returns instant when document exists`() = runTest {
        val getResponse = mockk<GetResponse<Any>>()
        every { getResponse.found() } returns true
        every { getResponse.source() } returns mapOf("modifiedAt" to "2026-01-30T08:33:30Z")

        every {
            openSearchClient.get(
                any<java.util.function.Function<GetRequest.Builder, ObjectBuilder<GetRequest>>>(),
                any<Class<*>>()
            )
        } returns getResponse as GetResponse<Nothing>

        val result = adapter.findModifiedAtByArticleId("test-001")

        assertEquals(Instant.parse("2026-01-30T08:33:30Z"), result)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `findModifiedAtByArticleId returns null when document not found`() = runTest {
        val getResponse = mockk<GetResponse<Any>>()
        every { getResponse.found() } returns false

        every {
            openSearchClient.get(
                any<java.util.function.Function<GetRequest.Builder, ObjectBuilder<GetRequest>>>(),
                any<Class<*>>()
            )
        } returns getResponse as GetResponse<Nothing>

        val result = adapter.findModifiedAtByArticleId("nonexistent")

        assertNull(result)
    }

    @Test
    fun `findModifiedAtByArticleId returns null when OpenSearch throws`() = runTest {
        every {
            openSearchClient.get(
                any<java.util.function.Function<GetRequest.Builder, ObjectBuilder<GetRequest>>>(),
                any<Class<*>>()
            )
        } throws RuntimeException("OpenSearch unavailable")

        val result = adapter.findModifiedAtByArticleId("test-001")

        assertNull(result)
    }

    @Test
    fun `indexAll throws BulkIndexingPartialFailureException on partial failure`() = runTest {
        val failedItem = mockk<BulkResponseItem>()
        val errorCause = mockk<ErrorCause>()
        every { failedItem.id() } returns "test-002"
        every { failedItem.error() } returns errorCause
        every { errorCause.reason() } returns "mapper_parsing_exception"

        val successItem = mockk<BulkResponseItem>()
        every { successItem.id() } returns "test-001"
        every { successItem.error() } returns null

        val bulkResponse = mockk<BulkResponse>()
        every { bulkResponse.errors() } returns true
        every { bulkResponse.items() } returns listOf(successItem, failedItem)

        every { openSearchClient.bulk(any<BulkRequest>()) } returns bulkResponse

        val exception = assertThrows(BulkIndexingPartialFailureException::class.java) {
            kotlinx.coroutines.runBlocking { adapter.indexAll(listOf(sampleDocument, sampleDocument2)) }
        }

        assertEquals(listOf("test-002"), exception.failedArticleIds)
        assertTrue(exception.message!!.contains("1/2"))
    }

    @Test
    fun `indexAll succeeds when bulk response has no errors`() = runTest {
        val bulkResponse = mockk<BulkResponse>()
        every { bulkResponse.errors() } returns false

        every { openSearchClient.bulk(any<BulkRequest>()) } returns bulkResponse

        adapter.indexAll(listOf(sampleDocument, sampleDocument2))

        verify { openSearchClient.bulk(any<BulkRequest>()) }
    }
}
