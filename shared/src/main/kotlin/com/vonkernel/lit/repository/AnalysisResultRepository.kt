package com.vonkernel.lit.repository

import com.vonkernel.lit.entity.AnalysisResult

interface AnalysisResultRepository {
    fun save(analysisResult: AnalysisResult): AnalysisResult
}
