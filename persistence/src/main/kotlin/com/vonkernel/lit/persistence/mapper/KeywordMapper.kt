package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.entity.Keyword
import com.vonkernel.lit.persistence.entity.analysis.ArticleKeywordEntity

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