package com.vonkernel.lit.ai.domain.exception

import com.vonkernel.lit.ai.domain.model.LlmProvider

/**
 * Provider별 옵션 변환 실패 (providerSpecificOptions → 데이터 클래스 변환 시)
 *
 * @property provider 옵션 변환 실패한 Provider
 * @property optionKey 변환 실패한 옵션 키
 * @property expectedType 기대한 타입
 * @property actualValue 실제 값
 */
class ProviderOptionsConversionException(
    val provider: LlmProvider,
    val optionKey: String,
    val expectedType: String,
    val actualValue: Any?,
    message: String = "Failed to convert option '$optionKey' to $expectedType for provider $provider. Actual value: $actualValue"
) : AiCoreException(message)
