package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.Keyword
import com.vonkernel.lit.persistence.jpa.entity.analysis.ArticleKeywordEntity

object KeywordMapper {

    fun toDomainModel(entity: ArticleKeywordEntity): Keyword =
        Keyword(
            keyword = entity.keyword,
            priority = entity.priority
        )

    fun toPersistenceModel(domain: Keyword): ArticleKeywordEntity =
        ArticleKeywordEntity(
            keyword = domain.keyword,
            priority = domain.priority
        )
}