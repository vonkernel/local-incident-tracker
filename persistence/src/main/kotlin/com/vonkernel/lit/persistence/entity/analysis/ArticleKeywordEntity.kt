package com.vonkernel.lit.persistence.entity.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "article_keywords")
class ArticleKeywordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    var analysisResult: AnalysisResultEntity? = null,

    @Column(name = "keyword", length = 500, nullable = false)
    var keyword: String,

    @Column(name = "priority", nullable = false)
    var priority: Int,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setupAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.keywords.add(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArticleKeywordEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
