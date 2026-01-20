package com.vonkernel.lit.entity

import java.time.Instant

data class Article(
    val articleId: String,
    val originId: String,
    val sourceId: String,
    val writtenAt: Instant,
    val modifiedAt: Instant,
    val title: String,
    val content: String,
    val sourceUrl: String? = null
)