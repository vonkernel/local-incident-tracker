package com.vonkernel.lit.ai.domain.exception

/**
 * 프롬프트 파일을 찾을 수 없거나 로드에 실패한 경우
 *
 * @property promptId 로드 실패한 프롬프트 ID
 */
class PromptLoadException(
    val promptId: String,
    message: String,
    cause: Throwable? = null
) : AiCoreException("Failed to load prompt '$promptId': $message", cause)
