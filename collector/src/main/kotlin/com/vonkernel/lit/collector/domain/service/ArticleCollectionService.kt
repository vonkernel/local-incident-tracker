package com.vonkernel.lit.collector.domain.service

import java.time.LocalDate

interface ArticleCollectionService {
    suspend fun collectArticlesForDate(date: LocalDate)
}
