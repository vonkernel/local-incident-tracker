package com.vonkernel.lit.ai.domain.port

import com.vonkernel.lit.ai.domain.model.Prompt

/**
 * 프롬프트를 로드하는 Port 인터페이스
 *
 * Hexagonal Architecture의 Port 역할:
 * - 도메인이 외부 리소스(YAML 파일 등)로부터 프롬프트를 가져오는 방법을 추상화
 * - 구현체는 infrastructure 계층에 위치 (예: YamlPromptLoader)
 */
interface PromptLoader {
    /**
     * 프롬프트 ID로 프롬프트를 로드
     *
     * @param I 입력 데이터 타입
     * @param O 출력 데이터 타입
     * @param promptId 프롬프트 식별자 (resources/prompts 하위 경로)
     * @param inputType 입력 데이터의 타입 정보
     * @param outputType 출력 데이터의 타입 정보
     * @return 로드된 Prompt 객체
     * @throws com.vonkernel.lit.ai.domain.exception.PromptLoadException 로드 실패 시
     */
    fun <I, O> load(
        promptId: String,
        inputType: Class<I>,
        outputType: Class<O>
    ): Prompt<I, O>
}
