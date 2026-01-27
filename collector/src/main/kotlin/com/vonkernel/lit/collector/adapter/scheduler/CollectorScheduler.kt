package com.vonkernel.lit.collector.adapter.scheduler

import com.vonkernel.lit.collector.domain.service.ArticleCollectionService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

@Component
class CollectorScheduler(
    private val articleCollectionService: ArticleCollectionService
) {

    private val logger = LoggerFactory.getLogger(CollectorScheduler::class.java)

    @Scheduled(fixedRate = 600000)
    fun collectTodayArticles() = runBlocking {
        LocalDate.now(ZoneId.of("Asia/Seoul")).let { today ->
            logger.info("Starting collection for date: $today")
            runCatching { articleCollectionService.collectArticlesForDate(today, PAGE_SIZE) }
                .onSuccess { logger.info("Successfully collected articles for $today") }
                .onFailure { e -> logger.error("Failed to collect articles for $today", e) }
        }
    }

    companion object {
        private const val PAGE_SIZE = 1000
    }
}
