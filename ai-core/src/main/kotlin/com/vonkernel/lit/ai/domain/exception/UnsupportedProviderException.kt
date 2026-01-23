package com.vonkernel.lit.ai.domain.exception

import com.vonkernel.lit.ai.domain.model.LlmProvider

/**
 * 지원하지 않는 Provider인 경우
 *
 * @property provider 지원하지 않는 Provider
 */
class UnsupportedProviderException(
    val provider: LlmProvider,
    message: String = "No executor available for provider: $provider"
) : AiCoreException(message)
