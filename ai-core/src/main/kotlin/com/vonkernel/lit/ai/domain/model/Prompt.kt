package com.vonkernel.lit.ai.domain.model

/**
 * LLM 프롬프트 실행 정보를 담는 도메인 모델
 *
 * @param I 입력 데이터 타입 (템플릿 변수 치환에 사용)
 * @param O 출력 데이터 타입 (LLM 응답 역직렬화 타입)
 * @property id 프롬프트 식별자 (resources/prompts 하위 경로, 예: "disaster-classification")
 * @property model 실행할 LLM 모델
 * @property template 프롬프트 템플릿 문자열 ({{variable}} 형태의 변수 포함 가능)
 * @property parameters LLM 실행 파라미터
 * @property inputType 입력 데이터의 타입 정보 (JSON 직렬화에 사용)
 * @property outputType 출력 데이터의 타입 정보 (JSON 역직렬화에 사용)
 */
data class Prompt<I, O>(
    val id: String,
    val model: LlmModel,
    val template: String,
    val parameters: PromptParameters,
    val inputType: Class<I>,
    val outputType: Class<O>
)
