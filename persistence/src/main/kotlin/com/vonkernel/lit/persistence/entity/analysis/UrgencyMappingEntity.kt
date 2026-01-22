package com.vonkernel.lit.persistence.entity.analysis

import com.vonkernel.lit.persistence.entity.core.UrgencyTypeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "urgency_mapping")
class UrgencyMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false, unique = true)
    var analysisResult: AnalysisResultEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urgency_type_id", nullable = false)
    var urgencyType: UrgencyTypeEntity? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.urgencyMapping = this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UrgencyMappingEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}