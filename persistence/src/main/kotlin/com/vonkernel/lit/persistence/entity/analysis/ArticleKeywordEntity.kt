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
data class ArticleKeywordEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    val analysisResult: AnalysisResultEntity,

    @Column(name = "keyword", length = 500, nullable = false)
    val keyword: String,

    @Column(name = "priority", nullable = false)
    val priority: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now()
)
