package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.jpa.entity.article.ArticleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface JpaArticleRepository : JpaRepository<ArticleEntity, String> {
    @Query("SELECT a.articleId FROM ArticleEntity a WHERE a.articleId IN :articleIds")
    fun findExistingIds(articleIds: Collection<String>): List<String>
}
