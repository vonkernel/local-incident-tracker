package com.vonkernel.lit.collector.adapter.api

import com.vonkernel.lit.collector.domain.service.ArticleCollectionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/collector")
class CollectorController(
    private val articleCollectionService: ArticleCollectionService
) {

    @PostMapping("/backfill")
    suspend fun backfill(@RequestBody request: BackfillRequest): ResponseEntity<*> =
        LocalDate.parse(request.startDate).run {
            articleCollectionService.collectArticlesForDate(this, PAGE_SIZE)
            ResponseEntity.noContent().build<Unit>()
        }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}
