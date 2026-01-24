package com.vonkernel.lit.collector.adapter.outbound

import com.vonkernel.lit.collector.adapter.outbound.model.SafetyDataApiResponse
import com.vonkernel.lit.collector.domain.model.ArticlePage
import com.vonkernel.lit.collector.domain.port.NewsApiPort
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class SafetyDataApiAdapter(
    @param:Value("\${safety-data.api.url}") private val apiUrl: String,
    @param:Value("\${safety-data.api.key}") private val apiKey: String,
    private val webClient: WebClient
) : NewsApiPort {

    override suspend fun fetchArticles(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int
    ): ArticlePage {
        val response = webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/DSSP-IF-00051")
                    .queryParam("serviceKey", apiKey)
                    .queryParam("inqDt", inqDt)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .queryParam("returnType", "json")
                    .build()
            }
            .retrieve()
            .bodyToMono<SafetyDataApiResponse>()
            .awaitSingle()

        return ArticlePage(
            articles = response.body.map(YonhapnewsArticle::toArticle),
            totalCount = response.totalCount,
            pageNo = response.pageNo,
            numOfRows = response.numOfRows
        )
    }
}