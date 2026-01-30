package com.vonkernel.lit.analyzer.domain.model

data class UrgencyAssessmentInput(
    val title: String,
    val content: String,
    val urgencyTypeList: String
)