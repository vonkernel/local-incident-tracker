package com.vonkernel.lit.ai.domain.exception

/**
 * 프롬프트 파일 파싱 실패 (잘못된 YAML 형식 등)
 *
 * @property promptId 파싱 실패한 프롬프트 ID
 * @property parseError 파싱 오류 상세 정보
 */
class PromptParseException(
    val promptId: String,
    val parseError: String,
    cause: Throwable? = null
) : AiCoreException("Failed to parse prompt '$promptId': $parseError", cause)
