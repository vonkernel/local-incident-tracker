package com.vonkernel.lit.persistence.entity.analysis

import com.vonkernel.lit.persistence.entity.core.ArticleEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "analysis_result")
class AnalysisResultEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false, unique = true)
    var article: ArticleEntity? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),

    @OneToOne(mappedBy = "analysisResult", fetch = FetchType.LAZY)
    var urgencyMapping: UrgencyMappingEntity? = null,

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY)
    var incidentTypeMappings: MutableSet<IncidentTypeMappingEntity> = mutableSetOf(),

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY)
    var addressMappings: MutableSet<AddressMappingEntity> = mutableSetOf(),

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY)
    var keywords: MutableSet<ArticleKeywordEntity> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalysisResultEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
