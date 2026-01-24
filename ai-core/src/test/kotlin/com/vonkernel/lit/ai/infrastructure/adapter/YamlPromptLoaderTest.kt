package com.vonkernel.lit.ai.infrastructure.adapter

import com.vonkernel.lit.ai.domain.exception.PromptLoadException
import com.vonkernel.lit.ai.domain.model.LlmModel
import com.vonkernel.lit.ai.domain.model.SummarizeInput
import com.vonkernel.lit.ai.domain.model.SummarizeOutput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * YamlPromptLoader 단위 테스트 (Spring 없이 순수 테스트)
 *
 * 검증 항목:
 * 1. summarize.yml 프롬프트 로딩 성공
 * 2. 필수 필드 검증 (id, model, template)
 * 3. 존재하지 않는 프롬프트 → PromptLoadException
 * 4. 프롬프트 캐싱 동작 검증
 */
class YamlPromptLoaderTest {

    private val resourceLoader = PathMatchingResourcePatternResolver()
    private val promptLoader = YamlPromptLoader(resourceLoader)

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        // 각 테스트 전에 캐시 초기화
        promptLoader.clearCache()
    }

    @Test
    fun `summarize 프롬프트 로딩 성공`() {
        // When
        val prompt = promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then
        assertNotNull(prompt)
        assertEquals("summarize", prompt.id)
        assertEquals(LlmModel.GPT_5_MINI, prompt.model)
        assert(prompt.template.contains("{{text}}"))
        assert(prompt.template.contains("{{maxLength}}"))
        assertEquals(1.0f, prompt.parameters.temperature)
        assertEquals(2048, prompt.parameters.maxCompletionTokens)
    }

    @Test
    fun `존재하지 않는 프롬프트 로딩 실패`() {
        // When & Then
        val exception = assertThrows<PromptLoadException> {
            promptLoader.load(
                promptId = "non-existent-prompt",
                inputType = SummarizeInput::class.java,
                outputType = SummarizeOutput::class.java
            )
        }

        assert(exception.message!!.contains("not found"))
    }

    @Test
    fun `동일한 프롬프트 여러 번 로드 시 캐시 사용`() {
        // Given
        assertEquals(0, promptLoader.getCacheSize())

        // When - 첫 번째 로드
        val prompt1 = promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then - 캐시에 저장됨
        assertEquals(1, promptLoader.getCacheSize())

        // When - 두 번째 로드 (동일 promptId, 동일 타입)
        val prompt2 = promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then - 캐시에서 반환 (동일 객체 참조)
        assertEquals(1, promptLoader.getCacheSize())
        assert(prompt1 === prompt2) // 동일 객체 참조
    }

    @Test
    fun `캐시 초기화 후 프롬프트 재로드`() {
        // Given - 프롬프트 로드하여 캐시에 저장
        promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )
        assertEquals(1, promptLoader.getCacheSize())

        // When - 캐시 초기화
        promptLoader.clearCache()

        // Then - 캐시 비워짐
        assertEquals(0, promptLoader.getCacheSize())

        // When - 재로드
        val prompt = promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )

        // Then - 새로 로드되어 캐시에 다시 저장
        assertNotNull(prompt)
        assertEquals(1, promptLoader.getCacheSize())
    }

    @Test
    fun `특정 프롬프트 캐시 무효화`() {
        // Given - summarize 프롬프트 로드
        promptLoader.load(
            promptId = "summarize",
            inputType = SummarizeInput::class.java,
            outputType = SummarizeOutput::class.java
        )
        assertEquals(1, promptLoader.getCacheSize())

        // When - summarize 캐시 무효화
        val evictedCount = promptLoader.evictPrompt("summarize")

        // Then - 해당 프롬프트만 제거됨
        assertEquals(1, evictedCount)
        assertEquals(0, promptLoader.getCacheSize())
    }
}