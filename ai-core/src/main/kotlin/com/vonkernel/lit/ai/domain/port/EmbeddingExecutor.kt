package com.vonkernel.lit.ai.domain.port

import com.vonkernel.lit.ai.domain.model.EmbeddingModel
import com.vonkernel.lit.ai.domain.model.LlmProvider

/**
 * 텍스트 임베딩을 실행하는 Port 인터페이스
 *
 * Hexagonal Architecture의 Port 역할:
 * - 도메인이 외부 임베딩 서비스(OpenAI 등)를 호출하는 방법을 추상화
 * - 구현체는 infrastructure 계층에 위치 (예: OpenAiEmbeddingExecutor)
 * - Provider별로 구현체를 제공하며, supports()로 지원 여부를 판단
 */
interface EmbeddingExecutor {
    /**
     * 이 Executor가 특정 Provider를 지원하는지 확인
     *
     * @param provider LLM 제공자
     * @return 지원 여부
     */
    fun supports(provider: LlmProvider): Boolean

    /**
     * 텍스트를 임베딩 벡터로 변환
     *
     * @param text 임베딩할 텍스트
     * @param model 사용할 임베딩 모델
     * @param dimensions 출력 벡터 차원 수
     * @return 임베딩 벡터
     * @throws com.vonkernel.lit.ai.domain.exception.LlmExecutionException 실행 실패 시
     */
    suspend fun embed(text: String, model: EmbeddingModel, dimensions: Int): FloatArray
}
