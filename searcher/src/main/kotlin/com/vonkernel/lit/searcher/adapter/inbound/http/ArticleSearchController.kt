package com.vonkernel.lit.searcher.adapter.inbound.http

import com.vonkernel.lit.searcher.adapter.inbound.http.dto.SearchRequest
import com.vonkernel.lit.searcher.adapter.inbound.http.dto.SearchResponse
import com.vonkernel.lit.searcher.domain.service.ArticleSearchService
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/articles")
class ArticleSearchController(
    private val articleSearchService: ArticleSearchService,
) {

    @GetMapping("/search")
    fun search(@ModelAttribute request: SearchRequest): ResponseEntity<SearchResponse> = runBlocking {
        request.toCriteria()
            .let { articleSearchService.search(it) }
            .let { ResponseEntity.ok(SearchResponse.from(it)) }
    }
}
