package com.vonkernel.lit.ai.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.vonkernel.lit.ai.domain.exception.TemplateResolutionException
import com.vonkernel.lit.ai.domain.exception.UnsupportedProviderException
import com.vonkernel.lit.ai.domain.model.Prompt
import com.vonkernel.lit.ai.domain.model.PromptExecutionResult
import com.vonkernel.lit.ai.domain.port.PromptExecutor
import com.vonkernel.lit.ai.domain.port.PromptLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

/**
 * 프롬프트 실행을 조율하는 Application Service
 *
 * 역할:
 * 1. PromptLoader를 통해 프롬프트 로드
 * 2. 입력 객체를 JSON serialize하여 템플릿 변수 추출
 * 3. 템플릿 변수 치환
 * 4. Provider에 맞는 PromptExecutor 선택
 * 5. 실행 및 결과 반환
 *
 * @property executors 등록된 모든 PromptExecutor 구현체 (Spring이 자동 주입)
 * @property promptLoader 프롬프트 로더
 * @property objectMapper JSON 직렬화/역직렬화에 사용
 */
@Service
class PromptOrchestrator(
    private val executors: List<PromptExecutor>,
    private val promptLoader: PromptLoader,
    private val objectMapper: ObjectMapper
) {
    /**
     * 프롬프트 ID로 프롬프트를 로드하고 입력 데이터로 실행
     *
     * @param I 입력 데이터 타입
     * @param O 출력 데이터 타입
     * @param promptId 프롬프트 식별자
     * @param input 템플릿 변수 치환에 사용할 입력 데이터
     * @param inputType 입력 데이터의 타입 정보
     * @param outputType 출력 데이터의 타입 정보
     * @return 실행 결과
     */
    suspend fun <I, O> execute(
        promptId: String,
        input: I,
        inputType: Class<I>,
        outputType: Class<O>
    ): PromptExecutionResult<O> {
        // 1. 프롬프트 로드
        val prompt = promptLoader.load(promptId, inputType, outputType)

        // 2. 입력 객체를 JSON serialize하여 필드 추출
        val inputFields = extractInputFields(input)

        // 3. 템플릿 변수 치환
        val resolvedPrompt = resolveTemplate(prompt, inputFields)

        // 4. Provider에 맞는 Executor 선택
        val provider = prompt.model.provider
        val executor = executors.firstOrNull { it.supports(provider) }
            ?: throw UnsupportedProviderException(provider)

        // 5. 실행
        return executor.execute(resolvedPrompt, input)
    }

    /**
     * 여러 프롬프트를 병렬로 실행
     *
     * Kotlin Coroutines의 async/await 패턴을 사용하여 효율적인 병렬 실행
     *
     * @param I 입력 데이터 타입
     * @param O 출력 데이터 타입
     * @param requests 실행할 프롬프트 요청 목록
     * @return 실행 결과 목록 (요청 순서와 동일)
     */
    suspend fun <I, O> executeParallel(
        requests: List<PromptRequest<I, O>>
    ): List<PromptExecutionResult<O>> = coroutineScope {
        requests.map { request ->
            async {
                execute(
                    promptId = request.promptId,
                    input = request.input,
                    inputType = request.inputType,
                    outputType = request.outputType
                )
            }
        }.awaitAll()
    }

    /**
     * 입력 객체를 JSON serialize하여 템플릿 변수로 사용할 필드 Map 추출
     *
     * 예시:
     * - Input: DisasterInput(text = "화재", maxLength = 100)
     * - Output: {"text": "화재", "maxLength": 100}
     *
     * @param input 입력 객체
     * @return 필드명 → 값의 Map
     */
    private fun <I> extractInputFields(input: I): Map<String, Any> {
        val jsonNode = objectMapper.valueToTree<JsonNode>(input)
        val fieldsMap = mutableMapOf<String, Any>()

        jsonNode.fields().forEach { (key, value) ->
            fieldsMap[key] = when {
                value.isTextual -> value.asText()
                value.isNumber -> value.numberValue()
                value.isBoolean -> value.asBoolean()
                value.isArray || value.isObject -> value.toString()
                value.isNull -> "null"
                else -> value.asText()
            }
        }

        return fieldsMap
    }

    /**
     * 프롬프트 템플릿의 변수를 입력 필드로 치환
     *
     * 템플릿 형식: "{{variableName}}"
     *
     * 예시:
     * - Template: "다음 텍스트를 {{maxLength}}자로 요약: {{text}}"
     * - Input Fields: {"text": "화재", "maxLength": 100}
     * - Result: "다음 텍스트를 100자로 요약: 화재"
     *
     * @param prompt 원본 프롬프트
     * @param inputFields 입력 필드 Map
     * @return 변수가 치환된 프롬프트
     * @throws TemplateResolutionException 필요한 변수가 입력에 없는 경우
     */
    private fun <I, O> resolveTemplate(
        prompt: Prompt<I, O>,
        inputFields: Map<String, Any>
    ): Prompt<I, O> {
        // 템플릿에서 {{variable}} 패턴 찾기
        val variablePattern = Regex("""\{\{(\w+)\}\}""")
        val requiredVariables = variablePattern.findAll(prompt.template)
            .map { it.groupValues[1] }
            .toSet()

        // 누락된 변수 확인
        val missingVariables = requiredVariables - inputFields.keys
        if (missingVariables.isNotEmpty()) {
            throw TemplateResolutionException(
                promptId = prompt.id,
                missingVariables = missingVariables.toList()
            )
        }

        // 변수 치환
        val resolvedTemplate = variablePattern.replace(prompt.template) { matchResult ->
            val variableName = matchResult.groupValues[1]
            inputFields[variableName]?.toString() ?: matchResult.value
        }

        return prompt.copy(template = resolvedTemplate)
    }
}
