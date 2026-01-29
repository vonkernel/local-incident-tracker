package com.vonkernel.lit.analyzer.adapter.inbound.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.vonkernel.lit.core.entity.Article
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumEnvelope(
    val before: ArticlePayload?,
    val after: ArticlePayload?,
    val op: String,
    val source: DebeziumSource? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DebeziumSource(
    val table: String? = null,
    val connector: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArticlePayload(
    @param:JsonProperty("article_id")
    val articleId: String,

    @param:JsonProperty("origin_id")
    val originId: String,

    @param:JsonProperty("source_id")
    val sourceId: String,

    @param:JsonProperty("written_at")
    val writtenAt: String,

    @param:JsonProperty("modified_at")
    val modifiedAt: String,

    val title: String,

    val content: String,

    @param:JsonProperty("source_url")
    val sourceUrl: String? = null
)

fun ArticlePayload.toArticle(): Article =
    Article(
        articleId = articleId,
        originId = originId,
        sourceId = sourceId,
        writtenAt = Instant.parse(writtenAt),
        modifiedAt = Instant.parse(modifiedAt),
        title = title,
        content = content,
        sourceUrl = sourceUrl
    )
