package com.vonkernel.lit.ai.infrastructure.adapter

/**
 * YAML 파일에서 읽어온 프롬프트 데이터
 *
 * YAML 파일의 구조와 매핑되는 데이터 클래스
 */
data class YamlPromptData(
    val id: String,
    val model: String,
    val template: String,
    val parameters: YamlPromptParameters? = null
)

/**
 * YAML 파일의 parameters 섹션
 */
data class YamlPromptParameters(
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val stopSequences: List<String>? = null,
    val topK: Int? = null,
    val providerSpecificOptions: Map<String, Any>? = null
)
