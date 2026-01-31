package com.vonkernel.lit.analyzer.domain.service;

import com.vonkernel.lit.analyzer.domain.port.analyzer.RefineArticleAnalyzer;
import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.core.entity.RefinedArticle
import org.springframework.stereotype.Service;

@Service
public class ArticleRefiner(
    private val refineArticleAnalyzer: RefineArticleAnalyzer
) : RetryableAnalysisService() {

    suspend fun process(article: Article): RefinedArticle =
        withRetry("refine-article", article.articleId) {
            refineArticleAnalyzer.analyze(article)
        }
}
