package com.vonkernel.lit.ai.domain.exception

/**
 * 템플릿 변수 치환 실패 (필요한 변수가 입력에 없는 경우)
 *
 * @property promptId 템플릿 치환 실패한 프롬프트 ID
 * @property missingVariables 누락된 변수 목록
 */
class TemplateResolutionException(
    val promptId: String,
    val missingVariables: List<String>,
    message: String = "Missing template variables: ${missingVariables.joinToString(", ")}"
) : AiCoreException("Template resolution failed for prompt '$promptId': $message")
