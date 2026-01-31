package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.core.entity.Topic

interface TopicAnalyzer {
    suspend fun analyze(summary: String): Topic
}
