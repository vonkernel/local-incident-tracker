package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.persistence.jpa.JpaArticleRepository
import com.vonkernel.lit.persistence.jpa.mapper.ArticleMapper
import com.vonkernel.lit.core.port.repository.ArticleRepository
import org.springframework.stereotype.Repository

@Repository
class ArticleRepositoryAdapter(
    private val jpaRepository: JpaArticleRepository
) : ArticleRepository {

    override fun save(article: Article): Article =
        ArticleMapper.toPersistenceModel(article)
            .let { jpaRepository.save(it) }
            .let { ArticleMapper.toDomainModel(it) }

    override fun saveAll(articles: Collection<Article>): List<Article> =
        articles.map { ArticleMapper.toPersistenceModel(it) }
            .let { jpaRepository.saveAll(it) }
            .map { ArticleMapper.toDomainModel(it) }

    override fun filterNonExisting(articleIds: Collection<String>): List<String> =
        jpaRepository.findExistingIds(articleIds).toSet()
            .let { existingIds -> articleIds.filterNot { it in existingIds } }
}