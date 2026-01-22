package com.vonkernel.lit.persistence.entity.analysis

import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "incident_type_mapping",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["analysis_result_id", "incident_type_id"], name = "uk_incident_type_mapping")
    ]
)
class IncidentTypeMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    var analysisResult: AnalysisResultEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_type_id", nullable = false)
    var incidentType: IncidentTypeEntity? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.incidentTypeMappings.add(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IncidentTypeMappingEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
