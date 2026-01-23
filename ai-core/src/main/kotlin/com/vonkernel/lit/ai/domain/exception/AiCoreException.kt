package com.vonkernel.lit.ai.domain.exception

import com.vonkernel.lit.ai.domain.model.LlmProvider

/**
 * ai-core 모듈의 최상위 예외 클래스
 *
 * Sealed class로 정의하여 컴파일 타임에 모든 예외 유형을 파악 가능
 */
sealed class AiCoreException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// ============================================================================
// 1. 프롬프트 로딩 관련 예외
// ============================================================================

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

// ============================================================================
// 2. 템플릿 처리 관련 예외
// ============================================================================

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

// ============================================================================
// 3. Provider 관련 예외
// ============================================================================

/**
 * 지원하지 않는 Provider인 경우
 *
 * @property provider 지원하지 않는 Provider
 */
class UnsupportedProviderException(
    val provider: LlmProvider,
    message: String = "No executor available for provider: $provider"
) : AiCoreException(message)

/**
 * Provider별 옵션 변환 실패 (providerSpecificOptions → 데이터 클래스 변환 시)
 *
 * @property provider 옵션 변환 실패한 Provider
 * @property optionKey 변환 실패한 옵션 키
 */
class ProviderOptionsConversionException(
    val provider: LlmProvider,
    val optionKey: String,
    val expectedType: String,
    val actualValue: Any?,
    message: String = "Failed to convert option '$optionKey' to $expectedType for provider $provider. Actual value: $actualValue"
) : AiCoreException(message)

// ============================================================================
// 4. LLM 실행 관련 예외
// ============================================================================

/**
 * LLM 실행 관련 예외의 상위 클래스
 */
sealed class LlmExecutionException(
    message: String,
    cause: Throwable? = null
) : AiCoreException(message, cause)

/**
 * LLM API 호출 실패 (HTTP 에러, 네트워크 오류 등)
 *
 * @property statusCode HTTP 상태 코드 (있을 경우)
 * @property errorBody API 응답 본문 (있을 경우)
 */
class LlmApiException(
    val statusCode: Int?,
    val errorBody: String?,
    message: String,
    cause: Throwable? = null
) : LlmExecutionException("LLM API error${statusCode?.let { " [$it]" } ?: ""}: $message", cause)

/**
 * LLM API 타임아웃
 *
 * @property timeoutMs 타임아웃 시간 (밀리초)
 */
class LlmTimeoutException(
    val timeoutMs: Long,
    message: String = "LLM API call timed out after ${timeoutMs}ms"
) : LlmExecutionException(message)

/**
 * LLM API Rate Limit 초과
 *
 * @property retryAfterSeconds 재시도 가능 시간 (초, 있을 경우)
 */
class LlmRateLimitException(
    val retryAfterSeconds: Int?,
    message: String = "LLM API rate limit exceeded${retryAfterSeconds?.let { ". Retry after $it seconds" } ?: ""}"
) : LlmExecutionException(message)

/**
 * LLM API 인증 실패 (잘못된 API 키 등)
 */
class LlmAuthenticationException(
    message: String = "LLM API authentication failed. Please check your API key."
) : LlmExecutionException(message)

// ============================================================================
// 5. 응답 처리 관련 예외
// ============================================================================

/**
 * LLM 응답을 파싱/역직렬화하는데 실패한 경우
 *
 * @property promptId 응답 파싱 실패한 프롬프트 ID
 * @property responseContent LLM의 원본 응답 내용
 * @property targetType 역직렬화하려던 타입
 */
class ResponseParsingException(
    val promptId: String,
    val responseContent: String,
    val targetType: String,
    message: String,
    cause: Throwable? = null
) : AiCoreException("Failed to parse LLM response for prompt '$promptId' into $targetType: $message", cause)
