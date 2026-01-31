package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.core.entity.Urgency

interface UrgencyAnalyzer {
    suspend fun analyze(urgencies: List<Urgency>, title: String, content: String): Urgency
}
