package com.vonkernel.lit.collector.domain.model

import com.vonkernel.lit.entity.Article

data class ArticlePage(
    val articles: List<Article>,
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int
)
