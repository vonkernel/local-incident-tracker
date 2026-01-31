package com.vonkernel.lit.ai.domain.model

/**
 * 지원하는 임베딩 모델 목록
 *
 * @property provider LLM 제공자
 * @property modelId 모델의 식별자 (API 호출 시 사용)
 * @property defaultDimensions 모델의 기본 출력 차원 수 (정보성 필드)
 */
enum class EmbeddingModel(
    val provider: LlmProvider,
    val modelId: String,
    val defaultDimensions: Int
) {
    // OpenAI Models
    /**
     * Text Embedding 3 Small - OpenAI의 소형 임베딩 모델
     */
    TEXT_EMBEDDING_3_SMALL(LlmProvider.OPENAI, "text-embedding-3-small", 1536),
}
