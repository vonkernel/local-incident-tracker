package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.RefinedArticle

interface RefineArticleAnalyzer {
    suspend fun analyze(article: Article): RefinedArticle
}
