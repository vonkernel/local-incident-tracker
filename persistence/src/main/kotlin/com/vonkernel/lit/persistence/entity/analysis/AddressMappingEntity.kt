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
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "address_mapping",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["analysis_result_id", "address_id"], name = "uk_address_mapping")
    ]
)
class AddressMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    var analysisResult: AnalysisResultEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id", nullable = false)
    var address: AddressEntity? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now()
) {
    fun setAnalysisResult(analysisResult: AnalysisResultEntity) {
        this.analysisResult = analysisResult
        analysisResult.addressMappings.add(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressMappingEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
