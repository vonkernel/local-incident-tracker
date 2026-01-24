package com.vonkernel.lit.ai.application

/**
 * 프롬프트 실행 요청 정보
 *
 * @param I 입력 데이터 타입
 * @param O 출력 데이터 타입
 * @property promptId 프롬프트 식별자
 * @property input 입력 데이터
 * @property inputType 입력 타입 정보
 * @property outputType 출력 타입 정보
 */
data class PromptRequest<I, O>(
    val promptId: String,
    val input: I,
    val inputType: Class<I>,
    val outputType: Class<O>
)
