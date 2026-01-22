package com.vonkernel.lit.entity

data class AnalysisResult(
    val articleId: String,
    val incidentTypes: Set<IncidentType>,
    val urgency: Urgency,
    val keywords: List<Keyword>,
    val locations: List<Location>
)
