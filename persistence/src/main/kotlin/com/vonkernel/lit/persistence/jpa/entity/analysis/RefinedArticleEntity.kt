package com.vonkernel.lit.persistence.jpa.entity.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "refined_article")
class RefinedArticleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false, unique = true)
    var analysisResult: AnalysisResultEntity? = null,

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    var title: String,

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    var summary: String,

    @Column(name = "written_at", nullable = false)
    var writtenAt: ZonedDateTime,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setupAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.refinedArticle = this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefinedArticleEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
