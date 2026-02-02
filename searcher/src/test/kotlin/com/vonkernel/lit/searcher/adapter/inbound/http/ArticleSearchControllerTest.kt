package com.vonkernel.lit.searcher.adapter.inbound.http

import com.vonkernel.lit.core.entity.ArticleIndexDocument
import com.vonkernel.lit.searcher.domain.exception.ArticleSearchException
import com.vonkernel.lit.searcher.domain.exception.InvalidSearchRequestException
import com.vonkernel.lit.searcher.domain.model.SearchResult
import com.vonkernel.lit.searcher.domain.model.SearchResultItem
import com.vonkernel.lit.searcher.domain.model.SortType
import com.vonkernel.lit.searcher.adapter.inbound.http.dto.SearchRequest
import com.vonkernel.lit.searcher.domain.service.ArticleSearchService
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArticleSearchControllerTest {

    private val articleSearchService = mockk<ArticleSearchService>()
    private val controller = ArticleSearchController(articleSearchService)
    private val globalExceptionHandler = GlobalExceptionHandler()

    @Test
    fun `search returns 200 with results`() {
        val result = SearchResult(
            items = listOf(
                SearchResultItem(
                    document = ArticleIndexDocument(articleId = "art-001", title = "테스트"),
                    score = 1.0f,
                )
            ),
            totalHits = 1,
            page = 0,
            size = 20,
        )
        coEvery { articleSearchService.search(any()) } returns result

        val request = SearchRequest(query = "화재", sortBy = "DATE")
        val response = controller.search(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1, response.body?.totalHits)
        assertEquals("art-001", response.body?.items?.first()?.articleId)
    }

    @Test
    fun `search maps parameters correctly`() {
        coEvery { articleSearchService.search(any()) } returns SearchResult(
            items = emptyList(), totalHits = 0, page = 0, size = 10,
        )

        val request = SearchRequest(
            query = "폭우",
            semanticSearch = true,
            jurisdictionCode = "11680",
            addressQuery = "서울 강남",
            depth1Name = "서울",
            incidentTypes = setOf("heavy_rain"),
            urgencyLevel = 3,
            sortBy = "DATE",
            page = 0,
            size = 10,
        )
        val response = controller.search(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.totalHits)
    }

    // --- SearchRequest 유효성 검증 ---

    @Test
    fun `partial proximity fields throws InvalidSearchRequestException`() {
        val request = SearchRequest(latitude = 37.5, longitude = 126.9)

        val exception = assertThrows<InvalidSearchRequestException> { request.toCriteria() }
        assertTrue(exception.message!!.contains("distanceKm"))
    }

    @Test
    fun `negative distanceKm throws InvalidSearchRequestException`() {
        val request = SearchRequest(latitude = 37.5, longitude = 126.9, distanceKm = -1.0)

        assertThrows<InvalidSearchRequestException> { request.toCriteria() }
    }

    @Test
    fun `negative page throws InvalidSearchRequestException`() {
        val request = SearchRequest(page = -1)

        assertThrows<InvalidSearchRequestException> { request.toCriteria() }
    }

    @Test
    fun `size exceeding 100 throws InvalidSearchRequestException`() {
        val request = SearchRequest(size = 101)

        assertThrows<InvalidSearchRequestException> { request.toCriteria() }
    }

    // --- GlobalExceptionHandler ---

    @Test
    fun `GlobalExceptionHandler handles InvalidSearchRequestException as 400`() {
        val exception = InvalidSearchRequestException("proximity 필터에는 latitude, longitude, distanceKm이 모두 필요합니다.")

        val response = globalExceptionHandler.handleInvalidSearchRequest(exception)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body?.get("error")!!.contains("proximity"))
    }

    @Test
    fun `GlobalExceptionHandler handles ArticleSearchException as 500`() {
        val exception = ArticleSearchException("Search execution failed")

        val response = globalExceptionHandler.handleArticleSearchException(exception)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Search execution failed", response.body?.get("error"))
    }

    @Test
    fun `GlobalExceptionHandler handles unexpected Exception as 500`() {
        val exception = RuntimeException("unexpected")

        val response = globalExceptionHandler.handleUnexpectedException(exception)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.get("error"))
    }

    // --- SearchRequest → SearchCriteria 변환 ---

    @Test
    fun `SearchRequest toCriteria converts correctly`() {
        val request = SearchRequest(
            query = "화재",
            semanticSearch = true,
            latitude = 37.5,
            longitude = 126.9,
            distanceKm = 10.0,
            depth1Name = "서울",
            sortBy = "RELEVANCE",
            page = 2,
            size = 15,
        )

        val criteria = request.toCriteria()

        assertEquals("화재", criteria.query)
        assertEquals(true, criteria.semanticSearch)
        assertNotNull(criteria.proximity)
        assertEquals(37.5, criteria.proximity!!.latitude)
        assertNotNull(criteria.region)
        assertEquals("서울", criteria.region!!.depth1Name)
        assertEquals(SortType.RELEVANCE, criteria.sortBy)
        assertEquals(2, criteria.page)
        assertEquals(15, criteria.size)
    }
}
