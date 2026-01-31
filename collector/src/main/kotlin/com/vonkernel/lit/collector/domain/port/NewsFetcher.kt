package com.vonkernel.lit.collector.domain.port

import com.vonkernel.lit.collector.domain.model.ArticlePage

interface NewsFetcher {
    suspend fun fetchArticles(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int
    ): ArticlePage
}
