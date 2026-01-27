package com.vonkernel.lit.collector.domain.service

import com.vonkernel.lit.entity.Article
import java.time.LocalDate

interface ArticleCollectionService {
    suspend fun collectArticlesForDate(date: LocalDate, pageSize: Int)
}
