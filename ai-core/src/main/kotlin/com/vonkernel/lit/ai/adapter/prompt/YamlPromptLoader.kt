package com.vonkernel.lit.ai.adapter.prompt

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
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * YAML 파일에서 프롬프트를 로드하는 Adapter
 *
 * Hexagonal Architecture의 Adapter 역할:
 * - Domain Port(PromptLoader)의 구현체
 * - Spring ResourceLoader를 사용하여 classpath에서 YAML 파일 로드
 * - YAML 파싱 및 검증 수행
 * - 로드된 프롬프트를 메모리 캐시하여 성능 최적화
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

    /**
     * 프롬프트 캐시
     *
     * Key: "$promptId-${inputType.name}-${outputType.name}"
     * Value: Prompt<*, *> (제네릭 타입은 런타임에 소거되므로 Any로 저장)
     *
     * Thread-safe: ConcurrentHashMap 사용
     * 성능: 파일 I/O, 파싱, 검증 비용 제거 (첫 로드 후 캐시 히트)
     */
    private val promptCache = ConcurrentHashMap<String, Prompt<*, *>>()

    @Suppress("UNCHECKED_CAST")
    override fun <I, O> load(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> =
        promptCache.computeIfAbsent(buildCacheKey(promptId, inputType, outputType)) {
            loadFromYaml(promptId, inputType, outputType)
        } as Prompt<I, O>

    /**
     * 전체 프롬프트 캐시 초기화
     *
     * 주로 개발/테스트 환경에서 프롬프트 파일 수정 후 재로드를 위해 사용
     */
    fun clearCache() {
        promptCache.clear()
    }

    /**
     * 특정 프롬프트의 캐시 무효화
     *
     * promptId에 해당하는 모든 타입 조합의 캐시를 제거
     * (동일 promptId이지만 다른 타입으로 로드된 경우 모두 제거)
     *
     * @param promptId 캐시를 무효화할 프롬프트 ID
     * @return 제거된 캐시 항목 수
     */
    fun evictPrompt(promptId: String): Int =
        promptCache.keys
            .filter { it.startsWith("$promptId-") }
            .onEach { promptCache.remove(it) }
            .size

    /**
     * 현재 캐시된 프롬프트 수 조회
     *
     * @return 캐시에 저장된 프롬프트 개수
     */
    fun getCacheSize(): Int = promptCache.size

    private fun buildCacheKey(promptId: String, inputType: Class<*>, outputType: Class<*>): String =
        "$promptId-${inputType.name}-${outputType.name}"

    private fun <I, O> loadFromYaml(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> =
        loadResourceOrThrow(promptId)
            .let { parseYamlOrThrow(it, promptId) }
            .also { validatePromptData(it) }
            .let { convertToDomainModel(it, inputType, outputType) }

    private fun loadResourceOrThrow(promptId: String) =
        "classpath:prompts/$promptId.yml".let { path ->
            resourceLoader.getResource(path)
                .takeIf { it.exists() }
                ?: throw PromptLoadException(promptId, "Prompt file not found at $path")
        }

    private fun parseYamlOrThrow(resource: Resource, promptId: String): YamlPromptData =
        runCatching { resource.inputStream.use { yamlMapper.readValue<YamlPromptData>(it) } }
            .getOrElse { e -> throw PromptParseException(promptId, e.message ?: "Unknown parsing error", e) }

    private fun validatePromptData(data: YamlPromptData) {
        collectErrors(data)
            .takeIf { it.isNotEmpty() }
            ?.let { throw PromptValidationException(data.id, it) }
    }

    private fun collectErrors(data: YamlPromptData): List<String> =
        validateRequiredFields(data) + validateModel(data.model) + validateParameters(data.parameters)

    private fun validateRequiredFields(data: YamlPromptData): List<String> = listOfNotNull(
        "Prompt ID cannot be blank".takeIf { data.id.isBlank() },
        "Template cannot be blank".takeIf { data.template.isBlank() },
    )

    private fun validateModel(model: String): List<String> =
        runCatching { LlmModel.valueOf(model) }
            .fold(
                onSuccess = { emptyList() },
                onFailure = { listOf("Invalid model: $model. Available models: ${LlmModel.entries.joinToString { it.name }}") }
            )

    private fun validateParameters(params: YamlPromptParameters?): List<String> =
        params?.let {
            listOfNotNull(
                validateRange("Temperature", it.temperature, 0f..2f),
                it.topP?.let { v -> validateRange("TopP", v, 0f..1f) },
                it.frequencyPenalty?.let { v -> validateRange("FrequencyPenalty", v, -2f..2f) },
                it.presencePenalty?.let { v -> validateRange("PresencePenalty", v, -2f..2f) },
            )
        } ?: emptyList()

    private fun validateRange(name: String, value: Float, range: ClosedFloatingPointRange<Float>): String? =
        "$name must be between ${range.start} and ${range.endInclusive}, got $value"
            .takeIf { value !in range }

    /**
     * YAML 데이터를 Domain Prompt 객체로 변환
     */
    private fun <I, O> convertToDomainModel(
        data: YamlPromptData,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> =
        Prompt(
            id = data.id,
            model = LlmModel.valueOf(data.model),
            template = data.template,
            parameters = data.parameters?.let { params ->
                PromptParameters(
                    temperature = params.temperature,
                    maxTokens = params.maxTokens,
                    maxCompletionTokens = params.maxCompletionTokens,
                    topP = params.topP,
                    frequencyPenalty = params.frequencyPenalty,
                    presencePenalty = params.presencePenalty,
                    stopSequences = params.stopSequences,
                    topK = params.topK,
                    providerSpecificOptions = params.providerSpecificOptions
                )
            } ?: PromptParameters(),
            inputType = inputType,
            outputType = outputType
        )
}
