package com.vonkernel.lit.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "article")
data class ArticleEntity(
    @Id
    @Column(name = "article_id", length = 255)
    val articleId: String,

    @Column(name = "origin_id", length = 255, nullable = false)
    val originId: String,

    @Column(name = "source_id", length = 255, nullable = false)
    val sourceId: String,

    @Column(name = "written_at", nullable = false)
    val writtenAt: ZonedDateTime,

    @Column(name = "modified_at", nullable = false)
    val modifiedAt: ZonedDateTime,

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    val title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "source_url", length = 2048)
    val sourceUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: ZonedDateTime = ZonedDateTime.now()
)