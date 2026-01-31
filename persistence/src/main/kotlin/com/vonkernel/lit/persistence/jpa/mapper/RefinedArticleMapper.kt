package com.vonkernel.lit.persistence.jpa.mapper

import com.vonkernel.lit.core.entity.RefinedArticle
import com.vonkernel.lit.persistence.jpa.entity.analysis.RefinedArticleEntity
import java.time.ZoneOffset

object RefinedArticleMapper {

    fun toDomainModel(entity: RefinedArticleEntity): RefinedArticle =
        RefinedArticle(
            title = entity.title,
            content = entity.content,
            summary = entity.summary,
            writtenAt = entity.writtenAt.toInstant()
        )

    fun toPersistenceModel(domain: RefinedArticle): RefinedArticleEntity =
        RefinedArticleEntity(
            title = domain.title,
            content = domain.content,
            summary = domain.summary,
            writtenAt = domain.writtenAt.atZone(ZoneOffset.UTC)
        )
}
