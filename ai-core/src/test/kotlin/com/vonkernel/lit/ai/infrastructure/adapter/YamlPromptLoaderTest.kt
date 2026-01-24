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
 */
class YamlPromptLoaderTest {

    private val resourceLoader = PathMatchingResourcePatternResolver()
    private val promptLoader = YamlPromptLoader(resourceLoader)

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
        assertEquals(500, prompt.parameters.maxTokens)
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
}