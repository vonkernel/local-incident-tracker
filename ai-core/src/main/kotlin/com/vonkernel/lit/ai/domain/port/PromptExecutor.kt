package com.vonkernel.lit.ai.domain.port

import com.vonkernel.lit.ai.domain.model.LlmProvider
import com.vonkernel.lit.ai.domain.model.Prompt
import com.vonkernel.lit.ai.domain.model.PromptExecutionResult

/**
 * LLM 프롬프트를 실행하는 Port 인터페이스
 *
 * Hexagonal Architecture의 Port 역할:
 * - 도메인이 외부 LLM 서비스(OpenAI, Anthropic 등)를 호출하는 방법을 추상화
 * - 구현체는 infrastructure 계층에 위치 (예: OpenAiPromptExecutor)
 * - Provider별로 구현체를 제공하며, supports()로 지원 여부를 판단
 */
interface PromptExecutor {
    /**
     * 이 Executor가 특정 Provider를 지원하는지 확인
     *
     * @param provider LLM 제공자
     * @return 지원 여부
     */
    fun supports(provider: LlmProvider): Boolean

    /**
     * 프롬프트를 실행하여 결과 반환
     *
     * @param I 입력 데이터 타입
     * @param O 출력 데이터 타입
     * @param prompt 실행할 프롬프트
     * @param input 템플릿 변수 치환에 사용할 입력 데이터
     * @return 실행 결과
     * @throws com.vonkernel.lit.ai.domain.exception.LlmExecutionException 실행 실패 시
     */
    suspend fun <I, O> execute(
        prompt: Prompt<I, O>,
        input: I
    ): PromptExecutionResult<O>
}
