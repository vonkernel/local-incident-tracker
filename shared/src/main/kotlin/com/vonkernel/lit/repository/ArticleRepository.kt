package com.vonkernel.lit.repository

import com.vonkernel.lit.entity.Article

interface ArticleRepository {
    fun save(article: Article): Article

    fun saveAll(articles: Collection<Article>): List<Article>

    fun filterNonExisting(articleIds: Collection<String>): List<String>
}
