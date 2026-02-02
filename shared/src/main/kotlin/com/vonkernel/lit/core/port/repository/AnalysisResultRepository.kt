package com.vonkernel.lit.core.port.repository

import com.vonkernel.lit.core.entity.AnalysisResult
import java.time.Instant

interface AnalysisResultRepository {
    fun save(analysisResult: AnalysisResult, articleUpdatedAt: Instant? = null): AnalysisResult
    fun findArticleUpdatedAtByArticleId(articleId: String): Instant?
}
