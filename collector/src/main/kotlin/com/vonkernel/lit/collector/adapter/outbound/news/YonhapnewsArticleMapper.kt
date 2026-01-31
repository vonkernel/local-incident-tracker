package com.vonkernel.lit.collector.adapter.outbound.news

import com.vonkernel.lit.collector.adapter.outbound.news.model.YonhapnewsArticle
import com.vonkernel.lit.core.entity.Article
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private const val SOURCE_ID = "yonhapnews"
private val publishedAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val publishedDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val createdAtFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSSSSSSSS")
private val seoulZone = ZoneId.of("Asia/Seoul")

fun YonhapnewsArticle.toArticle(): Article {
    return Article(
        articleId = "${parsePublishedDate(this.publishedAt)}-${this.articleNo}",
        originId = this.articleNo.toString(),
        sourceId = SOURCE_ID,
        writtenAt = parsePublishedAt(this.publishedAt),
        modifiedAt = parseCreatedAt(this.createdAt),
        title = this.title.trim(),
        content = this.content.trim(),
        sourceUrl = null
    )
}

private fun parsePublishedDate(dateStr: String): String =
    parsePublishedAt(dateStr)
        .atZone(ZoneId.of("Asia/Seoul"))
        .format(publishedDateFormatter.withLocale(Locale.KOREA))


private fun parsePublishedAt(dateStr: String): Instant =
    LocalDateTime.parse(dateStr, publishedAtFormatter).atZone(seoulZone).toInstant()

private fun parseCreatedAt(dateStr: String): Instant =
    LocalDateTime.parse(dateStr, createdAtFormatter).atZone(seoulZone).toInstant()
