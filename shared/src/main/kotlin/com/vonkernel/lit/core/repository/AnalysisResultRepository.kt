package com.vonkernel.lit.core.repository

import com.vonkernel.lit.core.entity.AnalysisResult

interface AnalysisResultRepository {
    fun save(analysisResult: AnalysisResult): AnalysisResult
    fun existsByArticleId(articleId: String): Boolean
    fun deleteByArticleId(articleId: String)
}
