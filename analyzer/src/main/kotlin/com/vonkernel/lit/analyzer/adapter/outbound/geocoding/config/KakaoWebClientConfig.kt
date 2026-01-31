package com.vonkernel.lit.analyzer.adapter.outbound.geocoding.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class KakaoWebClientConfig {

    @Bean("kakaoWebClient")
    fun kakaoWebClient(@Value("\${kakao.api.key}") apiKey: String): WebClient =
        WebClient.builder()
            .baseUrl("https://dapi.kakao.com")
            .defaultHeader("Authorization", "KakaoAK $apiKey")
            .build()
}
