package com.vonkernel.lit.ai.domain.exception

/**
 * 프롬프트 검증 실패 (필수 필드 누락, 잘못된 모델 등)
 *
 * @property promptId 검증 실패한 프롬프트 ID
 * @property validationErrors 검증 오류 목록
 */
class PromptValidationException(
    val promptId: String,
    val validationErrors: List<String>,
    message: String = validationErrors.joinToString("; ")
) : AiCoreException("Prompt validation failed for '$promptId': $message")
