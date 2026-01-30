package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.Urgency

interface UrgencyAnalyzer {
    suspend fun analyze(title: String, content: String): Urgency
}
