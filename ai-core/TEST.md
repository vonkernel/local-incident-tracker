# ai-core Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 4 | 17 | - |
| 통합 테스트 | 3 | 4 | - |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../infrastructure/adapter/` | Executor, Loader 테스트 |
| 단위 테스트 | `src/test/kotlin/.../service/` | Orchestrator 테스트 |
| 통합 테스트 | `src/test/kotlin/.../infrastructure/adapter/` | 실제 API 호출 테스트 |
| 통합 테스트 | `src/test/kotlin/.../service/` | 전체 플로우 테스트 |

## 테스트 실행

```bash
# 전체 단위 테스트
./gradlew ai-core:test

# 통합 테스트 (실제 API 호출)
./gradlew ai-core:integrationTest

# 특정 테스트 클래스
./gradlew ai-core:test --tests YamlPromptLoaderTest
```

## 단위 테스트 체크리스트

### YamlPromptLoader

YAML 프롬프트 로딩 및 캐싱 검증.

- [x] summarize 프롬프트 로딩 성공
- [x] 존재하지 않는 프롬프트 로딩 실패
- [x] 동일한 프롬프트 여러 번 로드 시 캐시 사용
- [x] 캐시 초기화 후 프롬프트 재로드
- [x] 특정 프롬프트 캐시 무효화

### OpenAiPromptExecutor

OpenAI Chat API 호출 및 응답 파싱 검증 (Mock 기반).

- [x] 프롬프트 실행 성공 및 JSON 응답 파싱
- [x] LLM 응답 파싱 실패 시 ResponseParsingException 발생

### OpenAiEmbeddingExecutor

OpenAI Embedding API 호출 및 응답 처리 검증 (Mock 기반).

- [x] 단건 임베딩 성공
- [x] 배치 임베딩 성공 (embedAll)
- [x] 차원 수 지정 시 올바르게 적용
- [x] 빈 텍스트 입력 시 예외 발생
- [x] API 에러 시 LlmExecutionException 발생
- [x] Rate Limit 에러 시 LlmRateLimitException 발생
- [x] 인증 에러 시 LlmAuthenticationException 발생
- [x] 지원하지 않는 Provider 확인

### PromptOrchestrator

프롬프트 실행 전체 플로우 검증 (Mock 기반).

- [x] 프롬프트 로드 → 템플릿 치환 → 실행 성공
- [x] 템플릿 변수 누락 시 TemplateResolutionException 발생

## 통합 테스트 체크리스트

### OpenAiPromptExecutorIntegrationTest

실제 OpenAI Chat API 호출 검증.

- [x] 실제 API 호출 및 응답 파싱

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요
- API 비용 발생 주의

### OpenAiEmbeddingExecutorIntegrationTest

실제 OpenAI Embedding API 호출 검증.

- [x] 실제 API 호출 및 벡터 반환

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### PromptOrchestratorIntegrationTest

전체 플로우 실제 API 검증.

- [x] YAML 로드 → 템플릿 치환 → 실제 LLM 호출 → 응답 파싱
- [x] 병렬 실행 검증

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- `.env.local` 파일에서 API Key 로드

## 테스트 환경

### 단위 테스트
- Spring Context 없이 실행
- MockK를 사용한 의존성 모킹
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- 테스트용 YAML 프롬프트: `src/test/resources/prompts/summarize.yml`

### 통합 테스트
- `@Tag("integration")` 태그로 분리
- `@SpringBootTest` 사용 (`IntegrationTestApplication`)
- 실제 OpenAI API 호출 (Spring AI AutoConfiguration)
- `.env.local` 파일에서 API Key 로드
- LLM 응답은 예측 불가능하므로 **구조만 검증** (내용 검증 X)

## 테스트 지원 클래스

| 클래스 | 역할 |
|--------|------|
| `IntegrationTestApplication` | 통합 테스트용 Spring Boot Application |
| `SummarizeInput` | 테스트용 입력 DTO |
| `SummarizeOutput` | 테스트용 출력 DTO |
