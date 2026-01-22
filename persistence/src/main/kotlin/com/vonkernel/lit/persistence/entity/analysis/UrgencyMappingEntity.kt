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
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "urgency_mapping")
data class UrgencyMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false, unique = true)
    val analysisResult: AnalysisResultEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urgency_type_id", nullable = false)
    val urgencyType: UrgencyTypeEntity,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now()
)