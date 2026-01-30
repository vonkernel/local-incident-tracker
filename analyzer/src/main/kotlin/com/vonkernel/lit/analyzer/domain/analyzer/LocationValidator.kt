package com.vonkernel.lit.analyzer.domain.analyzer

import com.vonkernel.lit.analyzer.domain.model.ExtractedLocation

interface LocationValidator {
    suspend fun validate(
        title: String,
        content: String,
        candidates: List<ExtractedLocation>
    ): List<ExtractedLocation>
}
