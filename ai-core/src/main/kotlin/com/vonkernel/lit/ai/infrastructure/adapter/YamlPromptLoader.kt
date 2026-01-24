package com.vonkernel.lit.ai.infrastructure.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.vonkernel.lit.ai.domain.exception.PromptLoadException
import com.vonkernel.lit.ai.domain.exception.PromptParseException
import com.vonkernel.lit.ai.domain.exception.PromptValidationException
import com.vonkernel.lit.ai.domain.model.LlmModel
import com.vonkernel.lit.ai.domain.model.Prompt
import com.vonkernel.lit.ai.domain.model.PromptParameters
import com.vonkernel.lit.ai.domain.port.PromptLoader
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

/**
 * YAML 파일에서 프롬프트를 로드하는 Adapter
 *
 * Hexagonal Architecture의 Adapter 역할:
 * - Domain Port(PromptLoader)의 구현체
 * - Spring ResourceLoader를 사용하여 classpath에서 YAML 파일 로드
 * - YAML 파싱 및 검증 수행
 *
 * @property resourceLoader Spring의 ResourceLoader (classpath 스캔)
 * @property yamlMapper YAML 파싱용 ObjectMapper
 */
@Component
class YamlPromptLoader(
    private val resourceLoader: ResourceLoader
) : PromptLoader {

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .findAndRegisterModules()

    override fun <I, O> load(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> {
        val yamlPath = "classpath:prompts/$promptId.yml"

        // 1. 리소스 존재 확인
        val resource = resourceLoader.getResource(yamlPath)
        if (!resource.exists()) {
            throw PromptLoadException(
                promptId = promptId,
                message = "Prompt file not found at $yamlPath"
            )
        }

        // 2. YAML 파싱
        val promptData = try {
            resource.inputStream.use { inputStream ->
                yamlMapper.readValue<YamlPromptData>(inputStream)
            }
        } catch (e: Exception) {
            throw PromptParseException(
                promptId = promptId,
                parseError = e.message ?: "Unknown parsing error",
                cause = e
            )
        }

        // 3. 검증
        validatePromptData(promptData)

        // 4. Domain 모델로 변환
        return convertToDomainModel(promptData, inputType, outputType)
    }

    /**
     * YAML 데이터 검증
     */
    private fun validatePromptData(data: YamlPromptData) {
        val errors = mutableListOf<String>()

        // ID 검증
        if (data.id.isBlank()) {
            errors.add("Prompt ID cannot be blank")
        }

        // 모델 검증
        try {
            LlmModel.valueOf(data.model)
        } catch (e: IllegalArgumentException) {
            errors.add("Invalid model: ${data.model}. Available models: ${LlmModel.entries.joinToString { it.name }}")
        }

        // 템플릿 검증
        if (data.template.isBlank()) {
            errors.add("Template cannot be blank")
        }

        // 파라미터 검증
        data.parameters?.let { params ->
            if (params.temperature < 0f || params.temperature > 2f) {
                errors.add("Temperature must be between 0.0 and 2.0, got ${params.temperature}")
            }

            params.topP?.let { topP ->
                if (topP < 0f || topP > 1f) {
                    errors.add("TopP must be between 0.0 and 1.0, got $topP")
                }
            }

            params.frequencyPenalty?.let { penalty ->
                if (penalty < -2f || penalty > 2f) {
                    errors.add("FrequencyPenalty must be between -2.0 and 2.0, got $penalty")
                }
            }

            params.presencePenalty?.let { penalty ->
                if (penalty < -2f || penalty > 2f) {
                    errors.add("PresencePenalty must be between -2.0 and 2.0, got $penalty")
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw PromptValidationException(
                promptId = data.id,
                validationErrors = errors
            )
        }
    }

    /**
     * YAML 데이터를 Domain Prompt 객체로 변환
     */
    private fun <I, O> convertToDomainModel(
        data: YamlPromptData,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> {
        val model = LlmModel.valueOf(data.model)

        val parameters = data.parameters?.let { params ->
            PromptParameters(
                temperature = params.temperature,
                maxTokens = params.maxTokens,
                topP = params.topP,
                frequencyPenalty = params.frequencyPenalty,
                presencePenalty = params.presencePenalty,
                stopSequences = params.stopSequences,
                topK = params.topK,
                providerSpecificOptions = params.providerSpecificOptions
            )
        } ?: PromptParameters()

        return Prompt(
            id = data.id,
            model = model,
            template = data.template,
            parameters = parameters,
            inputType = inputType,
            outputType = outputType
        )
    }
}
