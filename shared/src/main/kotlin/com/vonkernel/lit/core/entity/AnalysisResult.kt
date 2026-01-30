package com.vonkernel.lit.core.entity

data class AnalysisResult(
    val articleId: String,
    val refinedArticle: RefinedArticle,
    val incidentTypes: Set<IncidentType>,
    val urgency: Urgency,
    val keywords: List<Keyword>,
    val topic: Topic,
    val locations: List<Location>
)
