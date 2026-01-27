package com.vonkernel.lit.collector.fake

import com.vonkernel.lit.entity.Article
import com.vonkernel.lit.repository.ArticleRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fake implementation of ArticleRepository for testing.
 *
 * Uses ConcurrentHashMap for thread-safe operations during integration tests.
 */
class FakeArticleRepository : ArticleRepository {
    private val storage = ConcurrentHashMap<String, Article>()

    override fun save(article: Article): Article {
        storage[article.articleId] = article
        return article
    }

    override fun saveAll(articles: Collection<Article>): List<Article> {
        return articles.map { save(it) }
    }

    override fun filterNonExisting(articleIds: Collection<String>): List<String> {
        return articleIds.filter { it !in storage.keys }
    }

    // Test helper methods
    fun findById(articleId: String): Article? = storage[articleId]

    fun findAll(): List<Article> = storage.values.toList()

    fun clear() = storage.clear()

    fun size(): Int = storage.size
}
