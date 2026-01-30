package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.core.entity.Topic

interface TopicAnalyzer {
    suspend fun analyze(summary: String): Topic
}
