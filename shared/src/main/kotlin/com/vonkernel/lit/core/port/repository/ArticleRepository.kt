package com.vonkernel.lit.core.port.repository

import com.vonkernel.lit.core.entity.Article

interface ArticleRepository {
    fun save(article: Article): Article

    fun saveAll(articles: Collection<Article>): List<Article>

    fun filterNonExisting(articleIds: Collection<String>): List<String>
}
