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
        /**
         * Map을 OpenAiSpecificOptions로 변환
         *
         * @param map providerSpecificOptions Map
         * @return OpenAiSpecificOptions 인스턴스 (null이면 null 반환)
         * @throws ProviderOptionsConversionException 타입 변환 실패 시
         */
        fun fromMap(map: Map<String, Any>?): OpenAiSpecificOptions? {
            if (map == null) return null

            return try {
                OpenAiSpecificOptions(
                    responseFormat = map["responseFormat"]?.toString(),
                    seed = map["seed"]?.let { value ->
                        (value as? Number)?.toInt()
                            ?: throw ProviderOptionsConversionException(
                                provider = LlmProvider.OPENAI,
                                optionKey = "seed",
                                expectedType = "Number",
                                actualValue = value
                            )
                    },
                    n = map["n"]?.let { value ->
                        (value as? Number)?.toInt()
                            ?: throw ProviderOptionsConversionException(
                                provider = LlmProvider.OPENAI,
                                optionKey = "n",
                                expectedType = "Number",
                                actualValue = value
                            )
                    },
                    user = map["user"]?.toString(),
                    streamUsage = map["streamUsage"]?.let { value ->
                        (value as? Boolean)
                            ?: throw ProviderOptionsConversionException(
                                provider = LlmProvider.OPENAI,
                                optionKey = "streamUsage",
                                expectedType = "Boolean",
                                actualValue = value
                            )
                    },
                    tools = map["tools"] as? List<Any>,
                    toolChoice = map["toolChoice"]?.toString()
                )
            } catch (e: ProviderOptionsConversionException) {
                throw e
            } catch (e: Exception) {
                throw ProviderOptionsConversionException(
                    provider = LlmProvider.OPENAI,
                    optionKey = "unknown",
                    expectedType = "Unknown",
                    actualValue = map
                )
            }
        }
    }
}
