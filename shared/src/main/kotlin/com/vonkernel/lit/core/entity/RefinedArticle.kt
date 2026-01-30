package com.vonkernel.lit.core.entity

import java.time.Instant

data class RefinedArticle(
    val title: String,
    val content: String,
    val summary: String,
    val writtenAt: Instant
)
