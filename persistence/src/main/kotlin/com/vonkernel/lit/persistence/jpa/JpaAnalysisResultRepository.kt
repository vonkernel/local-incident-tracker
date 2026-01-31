package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.jpa.entity.analysis.AnalysisResultEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaAnalysisResultRepository : JpaRepository<AnalysisResultEntity, Long> {
    fun findByArticleId(articleId: String): AnalysisResultEntity?
}