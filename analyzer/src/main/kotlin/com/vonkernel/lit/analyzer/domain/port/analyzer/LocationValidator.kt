package com.vonkernel.lit.analyzer.domain.port.analyzer

import com.vonkernel.lit.analyzer.domain.port.analyzer.model.ExtractedLocation

interface LocationValidator {
    suspend fun validate(
        title: String,
        content: String,
        candidates: List<ExtractedLocation>
    ): List<ExtractedLocation>
}
