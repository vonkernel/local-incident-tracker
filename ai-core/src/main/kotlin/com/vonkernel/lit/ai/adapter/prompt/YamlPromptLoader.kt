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

    override fun <I, O> load(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> {
        // 캐시 키 생성 (promptId + 타입 정보)
        val cacheKey = buildCacheKey(promptId, inputType, outputType)

        // 캐시에서 조회, 없으면 로드
        @Suppress("UNCHECKED_CAST")
        val cachedPrompt = promptCache.computeIfAbsent(cacheKey) {
            loadFromYaml(promptId, inputType, outputType)
        } as Prompt<I, O>

        return cachedPrompt
    }

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

    /**
     * 캐시 키 생성
     *
     * promptId와 제네릭 타입 정보를 조합하여 유니크한 키 생성
     * 동일한 promptId라도 inputType/outputType이 다르면 다른 Prompt 객체
     */
    private fun buildCacheKey(
        promptId: String,
        inputType: Class<*>,
        outputType: Class<*>
    ): String {
        return "$promptId-${inputType.name}-${outputType.name}"
    }

    /**
     * YAML 파일에서 프롬프트 로드 (캐시 미스 시 호출)
     */
    private fun <I, O> loadFromYaml(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O> =
        "classpath:prompts/$promptId.yml"
            .let { yamlPath ->
                resourceLoader.getResource(yamlPath)
                    .takeIf { it.exists() }
                    ?: throw PromptLoadException(promptId, "Prompt file not found at $yamlPath")
            }
            .let { resource ->
                runCatching {
                    resource.inputStream.use { yamlMapper.readValue<YamlPromptData>(it) }
                }.getOrElse { e ->
                    throw PromptParseException(promptId, e.message ?: "Unknown parsing error", e)
                }
            }
            .also { validatePromptData(it) }
            .let { convertToDomainModel(it, inputType, outputType) }

    /**
     * YAML 데이터 검증
     */
    private fun validatePromptData(data: YamlPromptData) {
        buildList {
            if (data.id.isBlank()) add("Prompt ID cannot be blank")

            runCatching { LlmModel.valueOf(data.model) }
                .onFailure { add("Invalid model: ${data.model}. Available models: ${LlmModel.entries.joinToString { it.name }}") }

            if (data.template.isBlank()) add("Template cannot be blank")

            data.parameters?.let { params ->
                if (params.temperature !in 0f..2f) {
                    add("Temperature must be between 0.0 and 2.0, got ${params.temperature}")
                }

                params.topP?.takeIf { it !in 0f..1f }
                    ?.let { add("TopP must be between 0.0 and 1.0, got $it") }

                params.frequencyPenalty?.takeIf { it !in -2f..2f }
                    ?.let { add("FrequencyPenalty must be between -2.0 and 2.0, got $it") }

                params.presencePenalty?.takeIf { it !in -2f..2f }
                    ?.let { add("PresencePenalty must be between -2.0 and 2.0, got $it") }
            }
        }.takeIf { it.isNotEmpty() }
            ?.let { errors -> throw PromptValidationException(data.id, errors) }
    }

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
