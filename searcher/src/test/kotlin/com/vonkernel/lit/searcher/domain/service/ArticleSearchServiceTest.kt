package com.vonkernel.lit.searcher.domain.service

import com.vonkernel.lit.core.entity.ArticleIndexDocument
import com.vonkernel.lit.searcher.domain.exception.ArticleSearchException
import com.vonkernel.lit.searcher.domain.model.*
import com.vonkernel.lit.searcher.domain.port.ArticleSearcher
import com.vonkernel.lit.searcher.domain.port.Embedder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArticleSearchServiceTest {

    private val articleSearcher = mockk<ArticleSearcher>()
    private val embedder = mockk<Embedder>()
    private val service = ArticleSearchService(articleSearcher, embedder)

    private val emptyResult = SearchResult(items = emptyList(), totalHits = 0, page = 0, size = 20)

    @Test
    fun `full-text search executes without embedding`() = runTest {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.RELEVANCE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertEquals(0, result.totalHits)
        coVerify(exactly = 0) { embedder.embed(any()) }
        coVerify(exactly = 1) { articleSearcher.search(criteria, null) }
    }

    @Test
    fun `semantic search generates embedding and passes to searcher`() = runTest {
        val criteria = SearchCriteria(query = "서울 폭우", semanticSearch = true, sortBy = SortType.RELEVANCE)
        val embedding = ByteArray(512) { 0 }
        coEvery { embedder.embed("서울 폭우") } returns embedding
        coEvery { articleSearcher.search(criteria, embedding) } returns emptyResult

        val result = service.search(criteria)

        assertNotNull(result)
        coVerify(exactly = 1) { embedder.embed("서울 폭우") }
        coVerify(exactly = 1) { articleSearcher.search(criteria, embedding) }
    }

    @Test
    fun `semantic search falls back to full-text when embedding fails`() = runTest {
        val criteria = SearchCriteria(query = "산불", semanticSearch = true, sortBy = SortType.RELEVANCE)
        coEvery { embedder.embed("산불") } throws RuntimeException("API error")
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertNotNull(result)
        coVerify(exactly = 1) { articleSearcher.search(criteria, null) }
    }

    @Test
    fun `RELEVANCE sort without query throws exception`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.RELEVANCE)

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `DISTANCE sort without proximity throws exception`() = runTest {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.DISTANCE)

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `search failure propagates as ArticleSearchException`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } throws RuntimeException("connection refused")

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `DATE sort without query skips embedding`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertEquals(0, result.totalHits)
        coVerify(exactly = 0) { embedder.embed(any()) }
    }

    @Test
    fun `semantic search with blank query does not call embedder`() = runTest {
        val criteria = SearchCriteria(query = "  ", semanticSearch = true, sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        service.search(criteria)

        coVerify(exactly = 0) { embedder.embed(any()) }
    }
}
