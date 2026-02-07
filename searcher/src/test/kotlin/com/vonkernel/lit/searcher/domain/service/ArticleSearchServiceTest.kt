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
    fun `전문 검색은 임베딩 없이 실행한다`() = runTest {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.RELEVANCE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertEquals(0, result.totalHits)
        coVerify(exactly = 0) { embedder.embed(any()) }
        coVerify(exactly = 1) { articleSearcher.search(criteria, null) }
    }

    @Test
    fun `시맨틱 검색은 임베딩을 생성하여 검색기에 전달한다`() = runTest {
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
    fun `시맨틱 검색은 임베딩 실패 시 전문 검색으로 폴백한다`() = runTest {
        val criteria = SearchCriteria(query = "산불", semanticSearch = true, sortBy = SortType.RELEVANCE)
        coEvery { embedder.embed("산불") } throws RuntimeException("API error")
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertNotNull(result)
        coVerify(exactly = 1) { articleSearcher.search(criteria, null) }
    }

    @Test
    fun `RELEVANCE 정렬에 쿼리 없으면 예외 발생`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.RELEVANCE)

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `DISTANCE 정렬에 proximity 없으면 예외 발생`() = runTest {
        val criteria = SearchCriteria(query = "화재", sortBy = SortType.DISTANCE)

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `검색 실패 시 ArticleSearchException으로 전파`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } throws RuntimeException("connection refused")

        assertThrows<ArticleSearchException> { service.search(criteria) }
    }

    @Test
    fun `DATE 정렬에 쿼리 없으면 임베딩 건너뜀`() = runTest {
        val criteria = SearchCriteria(sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        val result = service.search(criteria)

        assertEquals(0, result.totalHits)
        coVerify(exactly = 0) { embedder.embed(any()) }
    }

    @Test
    fun `시맨틱 검색에 빈 쿼리면 임베더 호출하지 않음`() = runTest {
        val criteria = SearchCriteria(query = "  ", semanticSearch = true, sortBy = SortType.DATE)
        coEvery { articleSearcher.search(criteria, null) } returns emptyResult

        service.search(criteria)

        coVerify(exactly = 0) { embedder.embed(any()) }
    }
}
