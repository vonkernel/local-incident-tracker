package com.vonkernel.lit.collector.adapter.outbound

import com.vonkernel.lit.entity.Article
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val SOURCE_ID = "yonhapnews"
private val publishedAtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val createdAtFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSSSSSSSS")
private val seoulZone = ZoneId.of("Asia/Seoul")

fun YonhapnewsArticle.toArticle(): Article {
    return Article(
        articleId = UUID.randomUUID().toString(),
        originId = this.articleNo.toString(),
        sourceId = SOURCE_ID,
        writtenAt = parsePublishedAt(this.publishedAt),
        modifiedAt = parseCreatedAt(this.createdAt),
        title = this.title.trim(),
        content = this.content.trim(),
        sourceUrl = null
    )
}

private fun parsePublishedAt(dateStr: String): Instant {
    return LocalDateTime.parse(dateStr, publishedAtFormatter).atZone(seoulZone).toInstant()
}

private fun parseCreatedAt(dateStr: String): Instant {
    return LocalDateTime.parse(dateStr, createdAtFormatter).atZone(seoulZone).toInstant()
}
