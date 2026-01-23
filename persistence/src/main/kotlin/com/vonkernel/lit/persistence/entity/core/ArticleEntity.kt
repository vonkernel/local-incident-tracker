package com.vonkernel.lit.persistence.entity.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "article")
class ArticleEntity(
    @Id
    @Column(name = "article_id", length = 255)
    var articleId: String,

    @Column(name = "origin_id", length = 255, nullable = false)
    var originId: String,

    @Column(name = "source_id", length = 255, nullable = false)
    var sourceId: String,

    @Column(name = "written_at", nullable = false)
    var writtenAt: ZonedDateTime,

    @Column(name = "modified_at", nullable = false)
    var modifiedAt: ZonedDateTime,

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    var title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "source_url", length = 2048)
    var sourceUrl: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArticleEntity) return false
        return articleId == other.articleId
    }

    override fun hashCode(): Int = articleId.hashCode()
}