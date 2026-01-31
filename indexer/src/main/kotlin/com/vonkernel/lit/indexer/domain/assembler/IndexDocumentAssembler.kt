package com.vonkernel.lit.indexer.domain.assembler

import com.vonkernel.lit.core.entity.AnalysisResult
import com.vonkernel.lit.core.entity.ArticleIndexDocument
import java.time.ZoneOffset
import java.time.ZonedDateTime

object IndexDocumentAssembler {

    fun assemble(analysisResult: AnalysisResult, contentEmbedding: ByteArray? = null): ArticleIndexDocument {
        val writtenAt = ZonedDateTime.ofInstant(analysisResult.refinedArticle.writtenAt, ZoneOffset.UTC)

        return ArticleIndexDocument(
            articleId = analysisResult.articleId,
            title = analysisResult.refinedArticle.title,
            content = analysisResult.refinedArticle.content,
            keywords = analysisResult.keywords.map { it.keyword },
            contentEmbedding = contentEmbedding,
            incidentTypes = analysisResult.incidentTypes,
            urgency = analysisResult.urgency,
            incidentDate = writtenAt,
            geoPoints = analysisResult.locations.mapNotNull { it.coordinate },
            addresses = analysisResult.locations.map { it.address },
            jurisdictionCodes = analysisResult.locations
                .map { it.address.code }
                .filter { it != "UNKNOWN" }
                .toSet(),
            writtenAt = writtenAt,
        )
    }
}
