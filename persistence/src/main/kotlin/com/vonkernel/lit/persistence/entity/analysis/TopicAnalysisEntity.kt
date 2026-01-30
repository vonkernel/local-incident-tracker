package com.vonkernel.lit.persistence.entity.analysis

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
@Table(name = "topic_analysis")
class TopicAnalysisEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false, unique = true)
    var analysisResult: AnalysisResultEntity? = null,

    @Column(name = "topic", length = 500, nullable = false)
    var topic: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setupAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.topicAnalysis = this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TopicAnalysisEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
