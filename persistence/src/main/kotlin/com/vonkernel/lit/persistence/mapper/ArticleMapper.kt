package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.core.entity.Article
import com.vonkernel.lit.persistence.entity.core.ArticleEntity
import java.time.ZonedDateTime
import java.time.ZoneId

object ArticleMapper {

    fun toDomainModel(entity: ArticleEntity): Article =
        Article(
            articleId = entity.articleId,
            originId = entity.originId,
            sourceId = entity.sourceId,
            writtenAt = entity.writtenAt.toInstant(),
            modifiedAt = entity.modifiedAt.toInstant(),
            title = entity.title,
            content = entity.content,
            sourceUrl = entity.sourceUrl
        )

    fun toPersistenceModel(domain: Article): ArticleEntity =
        ArticleEntity(
            articleId = domain.articleId,
            originId = domain.originId,
            sourceId = domain.sourceId,
            writtenAt = ZonedDateTime.ofInstant(domain.writtenAt, ZoneId.systemDefault()),
            modifiedAt = ZonedDateTime.ofInstant(domain.modifiedAt, ZoneId.systemDefault()),
            title = domain.title,
            content = domain.content,
            sourceUrl = domain.sourceUrl
        )
}