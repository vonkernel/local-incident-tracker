package com.vonkernel.lit.persistence.entity.analysis

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "analysis_result")
class AnalysisResultEntity(

    @Column(name = "article_id", nullable = false, unique = true)
    var articleId: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),

    @OneToOne(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var urgencyMapping: UrgencyMappingEntity? = null,

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var incidentTypeMappings: MutableSet<IncidentTypeMappingEntity> = mutableSetOf(),

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var addressMappings: MutableSet<AddressMappingEntity> = mutableSetOf(),

    @OneToMany(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var keywords: MutableSet<ArticleKeywordEntity> = mutableSetOf(),

    @OneToOne(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var refinedArticle: RefinedArticleEntity? = null,

    @OneToOne(mappedBy = "analysisResult", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var topicAnalysis: TopicAnalysisEntity? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnalysisResultEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
