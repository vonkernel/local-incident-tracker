package com.vonkernel.lit.ai.domain.model

/**
 * 지원하는 LLM 모델 목록
 *
 * @property provider LLM 제공자
 * @property modelId 모델의 식별자 (API 호출 시 사용)
 */
enum class LlmModel(
    val provider: LlmProvider,
    val modelId: String
) {
    // OpenAI Models
    /**
     * GPT-5 Mini - OpenAI의 가장 최신 소형 모델
     */
    GPT_5_MINI(LlmProvider.OPENAI, "gpt-5-mini"),

    // 향후 확장 예시:
    // GPT_5(LlmProvider.OPENAI, "gpt-5"),
    // GPT_4_TURBO(LlmProvider.OPENAI, "gpt-4-turbo"),
    // CLAUDE_3_5_SONNET(LlmProvider.ANTHROPIC, "claude-3-5-sonnet-latest"),
    // GEMINI_PRO(LlmProvider.GOOGLE, "gemini-pro"),
}
