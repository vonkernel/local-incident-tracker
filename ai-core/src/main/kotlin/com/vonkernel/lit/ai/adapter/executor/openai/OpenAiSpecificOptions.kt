package com.vonkernel.lit.ai.adapter.executor.openai

import com.vonkernel.lit.ai.domain.exception.ProviderOptionsConversionException
import com.vonkernel.lit.ai.domain.model.LlmProvider

/**
 * OpenAI 전용 설정 옵션
 *
 * providerSpecificOptions Map을 타입 안전하게 변환하여 사용
 *
 * @property responseFormat 응답 형식 (예: "json_object")
 * @property seed 결정적 샘플링을 위한 시드 값
 * @property n 생성할 완성(completion) 개수
 * @property user 사용자 식별자 (abuse 모니터링용)
 * @property streamUsage 스트리밍 시 토큰 사용량 포함 여부
 * @property tools Function calling 도구 목록 (향후 구현)
 * @property toolChoice Tool 선택 방식 ("none", "auto" 등)
 */
data class OpenAiSpecificOptions(
    val responseFormat: String? = null,
    val seed: Int? = null,
    val n: Int? = null,
    val user: String? = null,
    val streamUsage: Boolean? = null,
    val tools: List<Any>? = null,
    val toolChoice: String? = null
) {
    companion object {
        fun fromMap(map: Map<String, Any>?): OpenAiSpecificOptions? =
            map?.let { convertOrThrow(it) }

        private fun convertOrThrow(map: Map<String, Any>): OpenAiSpecificOptions =
            runCatching {
                OpenAiSpecificOptions(
                    responseFormat = map["responseFormat"]?.toString(),
                    seed = extractNumberOrThrow(map, "seed"),
                    n = extractNumberOrThrow(map, "n"),
                    user = map["user"]?.toString(),
                    streamUsage = extractBooleanOrThrow(map, "streamUsage"),
                    tools = map["tools"] as? List<Any>,
                    toolChoice = map["toolChoice"]?.toString()
                )
            }.getOrElse { e ->
                when (e) {
                    is ProviderOptionsConversionException -> throw e
                    else -> throwConversionException("unknown", "Unknown", map)
                }
            }

        private fun extractNumberOrThrow(map: Map<String, Any>, key: String): Int? =
            map[key]?.let { value ->
                (value as? Number)?.toInt() ?: throwConversionException(key, "Number", value)
            }

        private fun extractBooleanOrThrow(map: Map<String, Any>, key: String): Boolean? =
            map[key]?.let { value ->
                (value as? Boolean) ?: throwConversionException(key, "Boolean", value)
            }

        private fun throwConversionException(optionKey: String, expectedType: String, actualValue: Any): Nothing =
            throw ProviderOptionsConversionException(
                provider = LlmProvider.OPENAI,
                optionKey = optionKey,
                expectedType = expectedType,
                actualValue = actualValue
            )
    }
}
