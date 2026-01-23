package com.vonkernel.lit.ai.domain.model

/**
 * 프롬프트 실행 파라미터
 *
 * Spring AI의 ChatOptions에 대응하는 도메인 모델
 * 모든 LLM 제공자에 공통적으로 적용 가능한 파라미터들을 포함
 *
 * @property temperature 생성 다양성 제어 (0.0 ~ 1.0, 높을수록 창의적)
 * @property maxTokens 생성할 최대 토큰 수
 * @property topP Nucleus sampling 임계값 (0.0 ~ 1.0)
 * @property frequencyPenalty 빈도 기반 페널티 (-2.0 ~ 2.0)
 * @property presencePenalty 존재 기반 페널티 (-2.0 ~ 2.0)
 * @property stopSequences 생성 중단 시퀀스 목록
 * @property topK Top-K sampling (일부 모델만 지원)
 * @property providerSpecificOptions 제공자별 고유 옵션 (예: OpenAI의 responseFormat)
 */
data class PromptParameters(
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val stopSequences: List<String>? = null,
    val topK: Int? = null,
    val providerSpecificOptions: Map<String, Any>? = null
)
