package com.vonkernel.lit.searcher.adapter.outbound.opensearch.config

import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.core5.http.HttpHost
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenSearchClientConfig(
    @param:Value("\${opensearch.host:localhost}") private val host: String,
    @param:Value("\${opensearch.port:9200}") private val port: Int,
) {

    @Bean
    fun openSearchClient(): OpenSearchClient =
        buildTransport()
            .let { OpenSearchClient(it) }

    private fun buildTransport() =
        ApacheHttpClient5TransportBuilder
            .builder(HttpHost("http", host, port))
            .setMapper(JacksonJsonpMapper())
            .setHttpClientConfigCallback { builder ->
                builder.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create().build())
            }
            .build()
}
