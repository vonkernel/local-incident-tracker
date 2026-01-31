# ai-core 모듈

LLM(Large Language Model) 프롬프트 실행을 위한 독립적인 라이브러리 모듈로, Spring AI 기반의 타입 안전한 프롬프트 관리 및 실행 엔진을 제공합니다.

## 목적 및 역할

**ai-core**는 LLM 호출을 추상화하여 애플리케이션이 특정 LLM Provider(OpenAI, Anthropic 등)에 종속되지 않도록 설계된 모듈입니다. 프롬프트를 YAML 파일로 외부화하여 코드 수정 없이 프롬프트 관리가 가능하며, 타입 안전성과 비동기 병렬 실행을 지원합니다.

### 핵심 특징

- **Provider 독립성**: OpenAI, Anthropic 등 다양한 LLM 제공자를 동일한 인터페이스로 사용
- **프롬프트 외부화**: YAML 파일로 프롬프트를 관리하여 코드 변경 없이 수정 가능, 추후에 외부 프롬프트 관리 툴로 확장 가능
- **타입 안전성**: 제네릭을 활용한 컴파일 타임 타입 검증 (`Prompt<Input, Output>`)
- **비동기 실행**: Kotlin Coroutines 기반 suspend 함수로 효율적인 I/O 처리
- **병렬 실행**: 여러 프롬프트를 동시에 실행하여 전체 처리 시간 단축
- **명확한 예외 계층**: Sealed class 기반 예외로 단계별 오류 처리

---

## 핵심 개념

### 1. Hexagonal Architecture (Port-Adapter 패턴)

ai-core는 도메인 로직과 외부 의존성(Spring AI, LLM API)을 명확히 분리합니다.

**Port (인터페이스)**:
- `PromptExecutor`: LLM 실행을 추상화 (도메인 → 외부 LLM API)
- `PromptLoader`: 프롬프트 로딩을 추상화 (도메인 → YAML 파일)

**Adapter (구현체)**:
- `OpenAiPromptExecutor`: OpenAI API 호출 (Spring AI 통합)
- `YamlPromptLoader`: YAML 파일에서 프롬프트 로드

이를 통해 도메인 계층은 Spring AI나 특정 LLM Provider에 의존하지 않으며, 새로운 Provider 추가 시 Adapter만 구현하면 됩니다.

### 2. Provider 추상화

**LlmProvider** enum으로 제공자를 정의하고, **LlmModel** enum으로 각 Provider의 모델을 관리합니다.

```kotlin
enum class LlmProvider {
    OPENAI,
    // ANTHROPIC, GOOGLE 등 확장 가능
}

enum class LlmModel(val provider: LlmProvider, val modelId: String) {
    GPT_5_MINI(LlmProvider.OPENAI, "gpt-5-mini"),
    // 추가 모델 정의
}
```

각 `PromptExecutor` 구현체는 `supports(provider: LlmProvider)` 메서드로 지원 여부를 명시하며, `PromptOrchestrator`가 런타임에 적절한 Executor를 선택합니다.

### 3. 프롬프트 관리 전략

프롬프트는 **YAML 파일**로 정의되며, **도메인 모듈**(analyzer 등)의 `resources/prompts/` 디렉토리에 배치됩니다. ai-core는 실행 엔진만 제공하고, 실제 프롬프트는 각 도메인에서 관리하여 높은 응집도를 유지합니다.

**YAML 프롬프트 구조**:

```yaml
id: disaster-classification
model: GPT_5_MINI
template: |
  다음 뉴스 기사의 재난 유형을 분류하세요.

  기사 제목: {{title}}
  기사 내용: {{content}}

  응답 형식: JSON
  {
    "disasterType": "재난유형",
    "confidence": 0.95
  }

parameters:
  temperature: 0.3
  maxCompletionTokens: 500
  providerSpecificOptions:
    responseFormat: "json_object"
```

**장점**:
- 코드 변경 없이 프롬프트 수정 가능
- 도메인 전문가가 비즈니스 로직과 프롬프트를 함께 관리
- 프롬프트 버전 관리 용이 (Git)

### 4. 타입 안전한 입출력

프롬프트는 제네릭 타입 `Prompt<I, O>`로 정의되며, 입력(`I`)과 출력(`O`)의 타입이 컴파일 타임에 검증됩니다.

**Input DTO**:
- 프롬프트 템플릿의 `{{variable}}` 이름과 필드명이 일치해야 함
- JSON serialize되어 템플릿 변수 치환에 사용

**Output DTO**:
- LLM 응답 JSON 구조와 일치해야 함
- Jackson을 사용하여 자동 역직렬화

**사용 예시**:

```kotlin
data class DisasterInput(
    val title: String,   // {{title}}에 매핑
    val content: String  // {{content}}에 매핑
)

data class DisasterOutput(
    val disasterType: String,
    val confidence: Double
)

// 실행
val result = promptOrchestrator.execute(
    promptId = "disaster-classification",
    input = DisasterInput(title = "...", content = "..."),
    inputType = DisasterInput::class.java,
    outputType = DisasterOutput::class.java
)
// result.response: DisasterOutput
```

### 5. 비동기 및 병렬 실행

**비동기 실행**: Kotlin Coroutines의 `suspend` 함수로 I/O 작업 시 스레드를 블로킹하지 않습니다.

**병렬 실행**: `executeParallel()` 메서드로 여러 프롬프트를 동시에 실행하여 전체 처리 시간을 단축합니다.

```kotlin
suspend fun analyzeArticle(article: Article): AnalysisResult {
    val results = promptOrchestrator.executeParallel(
        listOf(
            PromptRequest("disaster-type", disasterInput, ...),
            PromptRequest("location", locationInput, ...),
            PromptRequest("urgency", urgencyInput, ...)
        )
    )
    // 3개 프롬프트가 병렬로 실행됨
}
```

---

## 아키텍처

### 계층별 책임

```
┌──────────────────────────────────────────────┐
│        Domain Service Layer                  │
│  (PromptOrchestrator)                        │
│  - Load Prompt → Template Substitution       │
│  - Select Executor → Execute → Return Result │
└──────────────────────────────────────────────┘
                    ↓ uses
┌──────────────────────────────────────────────┐
│        Domain Layer                          │
│   ┌─────────────┐       ┌──────────────┐     │
│   │ Prompt<I,O> │       │ LlmModel     │     │
│   │ (Model)     │       │ LlmProvider  │     │
│   └─────────────┘       └──────────────┘     │
│                                              │
│   ┌───────────────────────────────────┐      │
│   │ PromptExecutor (Port)             │      │
│   │ PromptLoader (Port)               │      │
│   └───────────────────────────────────┘      │
│                                              │
│   ┌───────────────────────────────────┐      │
│   │ AiCoreException (Sealed Class)    │      │
│   └───────────────────────────────────┘      │
└──────────────────────────────────────────────┘
                    ↑ implements
┌──────────────────────────────────────────────┐
│        Adapter Layer                         │
│   ┌──────────────────────────────┐           │
│   │ OpenAiPromptExecutor         │           │
│   │ (Spring AI Integration)      │           │
│   └──────────────────────────────┘           │
│                                              │
│   ┌──────────────────────────────┐           │
│   │ YamlPromptLoader             │           │
│   │ (Parse & Validate YAML)      │           │
│   └──────────────────────────────┘           │
└──────────────────────────────────────────────┘
```

**Domain Layer**: 비즈니스 로직과 추상화 (Port, Model, Exception)
**Domain Service Layer**: 도메인 조율 및 워크플로우 (PromptOrchestrator)
**Adapter Layer**: 외부 시스템 연동 (Spring AI, YAML 파일)

### 핵심 컴포넌트

#### PromptOrchestrator

프롬프트 실행의 전체 워크플로우를 조율하는 Application Service입니다.

**실행 흐름**:
1. `PromptLoader`로 YAML 파일에서 `Prompt<I, O>` 로드
2. Input 객체를 JSON serialize하여 필드 추출
3. 템플릿 변수(`{{variable}}`) 치환 및 누락 변수 검증
4. `Prompt.model.provider`에 맞는 `PromptExecutor` 선택
5. Executor를 통해 LLM 호출 및 결과 반환

#### PromptExecutor (Port)

LLM 실행을 추상화하는 인터페이스로, Provider별 구현체를 제공합니다.

**주요 메서드**:
- `supports(provider: LlmProvider)`: 지원 여부 확인
- `suspend fun execute<I, O>(prompt, input)`: 프롬프트 실행

**구현체**: `OpenAiPromptExecutor` (Spring AI 기반 OpenAI 호출)

#### PromptLoader (Port)

YAML 파일에서 프롬프트를 로드하는 인터페이스입니다.

**구현체**: `YamlPromptLoader` (Jackson YAML 파싱, 검증 로직 포함)

### 데이터 흐름

```
1. 사용자 코드 (analyzer 등)
   ↓ execute(promptId, input, inputType, outputType)

2. PromptOrchestrator
   ↓ load(promptId)

3. YamlPromptLoader
   ↓ Prompt<I, O>

4. PromptOrchestrator
   ↓ JSON serialize input → Map<String, Any>
   ↓ resolveTemplate(prompt, inputFields)
   ↓ 템플릿 변수 치환 ({{title}} → "화재 발생")

5. PromptOrchestrator
   ↓ select executor by provider

6. OpenAiPromptExecutor
   ↓ buildChatOptions(parameters)
   ↓ Spring AI ChatModel.call(prompt, options)

7. OpenAI API
   ↓ JSON Response

8. OpenAiPromptExecutor
   ↓ parse JSON → Output DTO
   ↓ PromptExecutionResult<O>

9. 사용자 코드
   ↓ result.response (타입 안전한 Output)
```

---

## 설계 원칙 및 아키텍처 고민

### 1. 왜 Hexagonal Architecture인가?

**문제**: LLM Provider(OpenAI, Anthropic, Google 등)가 다양하고, 각각의 API가 다름
**해결**: Port-Adapter 패턴으로 도메인 로직과 외부 의존성 분리

**효과**:
- 새로운 Provider 추가 시 `PromptExecutor` 구현만 추가하면 됨
- 도메인 계층은 Spring AI나 특정 LLM API에 의존하지 않음
- 테스트 시 Mock Executor로 쉽게 대체 가능

### 2. 왜 프롬프트를 YAML로 관리하는가?

**문제**: 프롬프트 수정 시마다 코드 변경 및 재배포 필요
**해결**: YAML 파일로 외부화하여 설정처럼 관리

**효과**:
- 프롬프트 엔지니어가 코드 수정 없이 프롬프트 개선 가능
- Git으로 프롬프트 버전 관리 및 롤백 용이
- 도메인 모듈(analyzer)에서 프롬프트를 직접 관리하여 응집도 향상

### 3. 왜 타입 안전성이 중요한가?

**문제**: LLM 응답은 문자열(JSON)이므로 타입 오류가 런타임에 발생
**해결**: `Prompt<I, O>` 제네릭으로 컴파일 타임에 타입 검증

**효과**:
- Input과 Output의 타입 불일치를 컴파일 시점에 발견
- IDE 자동완성으로 개발 생산성 향상
- 리팩토링 시 타입 변경이 전체 코드에 즉시 반영

### 4. Provider별 고유 옵션 처리

**문제**: OpenAI의 `responseFormat`, Anthropic의 `topK` 등 Provider별 고유 옵션 존재
**해결**: `PromptParameters.providerSpecificOptions: Map<String, Any>?`로 유연하게 수용

**구현 방식**:
- YAML에서는 Map으로 자유롭게 정의
- Executor 구현체에서 타입 안전한 data class로 변환 (`OpenAiSpecificOptions`)
- 잘못된 타입 시 `ProviderOptionsConversionException` 발생

**예시**:

```yaml
parameters:
  temperature: 0.3
  providerSpecificOptions:
    responseFormat: "json_object"  # OpenAI 전용
    seed: 42
```

### 5. 예외 처리 전략

**문제**: LLM 호출 실패 원인이 다양함 (네트워크, API 키, Rate Limit, 파싱 오류 등)
**해결**: Sealed class 기반 예외 계층으로 단계별 예외 구분

**예외 계층**:

```
AiCoreException (sealed class)
├─ PromptLoadException (YAML 로딩 실패)
├─ PromptValidationException (프롬프트 검증 실패)
├─ TemplateResolutionException (템플릿 변수 누락)
├─ UnsupportedProviderException (Provider 미지원)
├─ ProviderOptionsConversionException (옵션 타입 오류)
├─ LlmExecutionException (LLM 호출 실패)
│  ├─ LlmApiException (4xx, 5xx)
│  ├─ LlmTimeoutException
│  ├─ LlmRateLimitException
│  └─ LlmAuthenticationException
└─ ResponseParsingException (응답 파싱 실패)
```

**효과**:
- 예외 발생 단계를 명확히 파악 가능
- Sealed class로 when 표현식에서 컴파일 타임 완전성 검증
- 단계별로 적절한 복구 전략 수립 가능 (재시도, Fallback 등)

---

## 사용 방법

### 1. 프롬프트 정의 (YAML)

도메인 모듈의 `src/main/resources/prompts/` 디렉토리에 YAML 파일 생성:

```yaml
# resources/prompts/disaster-classification.yml
id: disaster-classification
model: GPT_5_MINI
template: |
  다음 뉴스 기사의 재난 유형을 분류하세요.

  기사 제목: {{title}}
  기사 내용: {{content}}

parameters:
  temperature: 0.3
  maxCompletionTokens: 500
  providerSpecificOptions:
    responseFormat: "json_object"
```

### 2. Input/Output DTO 정의

```kotlin
data class DisasterInput(
    val title: String,
    val content: String
)

data class DisasterOutput(
    val disasterType: String,
    val confidence: Double
)
```

### 3. PromptOrchestrator 실행

```kotlin
@Service
class ArticleAnalyzer(
    private val promptOrchestrator: PromptOrchestrator
) {
    suspend fun classifyDisaster(article: Article): DisasterOutput {
        val result = promptOrchestrator.execute(
            promptId = "disaster-classification",
            input = DisasterInput(
                title = article.title,
                content = article.content
            ),
            inputType = DisasterInput::class.java,
            outputType = DisasterOutput::class.java
        )
        return result.response
    }
}
```

### 4. 병렬 실행

```kotlin
suspend fun analyzeArticle(article: Article): AnalysisResult {
    val results = promptOrchestrator.executeParallel(
        listOf(
            PromptRequest("disaster-type", disasterInput, ...),
            PromptRequest("location", locationInput, ...),
            PromptRequest("urgency", urgencyInput, ...)
        )
    )

    return AnalysisResult(
        disasterType = results[0].response.disasterType,
        locations = results[1].response.locations,
        urgency = results[2].response.urgency
    )
}
```

---

## 프로젝트 구조

```
ai-core/
├── src/main/kotlin/com/vonkernel/lit/ai/
│   ├── adapter/                        # 어댑터 계층 (외부 연동)
│   │   ├── executor/
│   │   │   └── openai/
│   │   │       ├── OpenAiPromptExecutor.kt      # OpenAI 구현체
│   │   │       └── OpenAiSpecificOptions.kt     # OpenAI 전용 옵션
│   │   └── prompt/
│   │       ├── YamlPromptLoader.kt              # YAML 로더
│   │       └── YamlPromptData.kt                # YAML 파싱용 DTO
│   ├── config/
│   │   └── AiCoreConfiguration.kt               # Spring 설정
│   └── domain/                         # 도메인 계층 (비즈니스 로직)
│       ├── model/                      # 도메인 모델
│       │   ├── Prompt.kt               # 프롬프트 정의 (제네릭)
│       │   ├── PromptParameters.kt     # 실행 파라미터
│       │   ├── PromptExecutionResult.kt # 실행 결과
│       │   ├── LlmProvider.kt          # Provider enum
│       │   ├── LlmModel.kt             # Model enum
│       │   ├── ExecutionMetadata.kt    # 실행 메타데이터
│       │   └── TokenUsage.kt           # 토큰 사용량
│       ├── port/                       # Port 인터페이스
│       │   ├── PromptExecutor.kt       # LLM 실행 추상화
│       │   └── PromptLoader.kt         # 프롬프트 로딩 추상화
│       ├── service/                    # 도메인 서비스
│       │   ├── PromptOrchestrator.kt   # 프롬프트 실행 조율
│       │   └── PromptRequest.kt        # 요청 DTO
│       └── exception/                  # 예외 계층
│           ├── AiCoreException.kt      # 최상위 예외 (sealed class)
│           ├── PromptLoadException.kt
│           ├── TemplateResolutionException.kt
│           ├── LlmExecutionException.kt
│           └── ...
├── src/test/
│   ├── kotlin/com/vonkernel/lit/ai/
│   │   ├── domain/model/
│   │   │   ├── SummarizeInput.kt        # 테스트용 Input
│   │   │   └── SummarizeOutput.kt       # 테스트용 Output
│   │   ├── application/
│   │   │   ├── PromptOrchestratorTest.kt            # Unit Test
│   │   │   └── PromptOrchestratorIntegrationTest.kt # Integration Test
│   │   ├── infrastructure/adapter/
│   │   │   ├── YamlPromptLoaderTest.kt
│   │   │   ├── OpenAiPromptExecutorTest.kt
│   │   │   └── OpenAiPromptExecutorIntegrationTest.kt
│   │   └── IntegrationTestApplication.kt        # Test용 Spring Boot App
│   └── resources/
│       └── prompts/
│           └── summarize.yml            # 테스트용 프롬프트
└── build.gradle.kts
```

---

## 테스트

ai-core는 **라이브러리 모듈**이므로 Spring Boot 애플리케이션 없이 테스트합니다.

### 1. Unit Test (Mock 기반)

**특징**:
- Spring 없이 순수 Kotlin/JUnit으로 실행
- Mock ChatModel 사용으로 실제 API 호출 없음
- CI/CD에서 항상 실행 가능
- 빠른 실행 속도

**실행 방법**:
```bash
# 전체 단위 테스트 실행
./gradlew ai-core:test

# 특정 테스트 클래스만 실행
./gradlew ai-core:test --tests YamlPromptLoaderTest
./gradlew ai-core:test --tests OpenAiPromptExecutorTest
./gradlew ai-core:test --tests PromptOrchestratorTest
```

**테스트 파일**:
- `YamlPromptLoaderTest.kt`: 프롬프트 YAML 로딩 검증
- `OpenAiPromptExecutorTest.kt`: Mock 응답 파싱 검증
- `PromptOrchestratorTest.kt`: 전체 플로우 (로드 → 치환 → 실행) 검증

### 2. Integration Test (실제 API 호출)

**특징**:
- `@Tag("integration")` 태그로 분리
- `@SpringBootTest`로 Spring context 활성화
- 실제 OpenAI API 호출 (Spring AI AutoConfiguration 사용)
- `.env.local` 파일에서 API Key 로드
- 수동 실행 전용 (CI/CD에서 기본 제외)

**실행 준비**:
```bash
# 1. .env.local.example을 복사하여 .env.local 생성
cp ai-core/.env.local.example ai-core/.env.local

# 2. .env.local 파일에 실제 API Key 입력
# SPRING_AI_OPENAI_API_KEY=sk-your-actual-api-key
```

**실행 방법**:
```bash
# Integration Test 실행 (별도 task)
./gradlew ai-core:integrationTest
```

**테스트 파일**:
- `OpenAiPromptExecutorIntegrationTest.kt`: 실제 OpenAI API 호출 및 응답 파싱
- `PromptOrchestratorIntegrationTest.kt`: 전체 플로우 실제 API 실행
- `IntegrationTestApplication.kt`: 테스트용 Spring Boot Application

**주의사항**:
- 실제 API 호출로 비용 발생 가능
- API Key가 없으면 Spring Boot context 시작 실패
- LLM 응답은 예측 불가능하므로 **구조만 검증** (내용 검증 X)

### 3. 테스트 구조

```
ai-core/src/test/
├── kotlin/com/vonkernel/lit/ai/
│   ├── IntegrationTestApplication.kt                  # Integration Test용 @SpringBootApplication
│   ├── domain/model/
│   │   ├── SummarizeInput.kt          # 테스트용 입력 DTO
│   │   └── SummarizeOutput.kt         # 테스트용 출력 DTO
│   ├── infrastructure/adapter/
│   │   ├── YamlPromptLoaderTest.kt                    # Unit Test
│   │   ├── OpenAiPromptExecutorTest.kt                # Unit Test (Mock)
│   │   └── OpenAiPromptExecutorIntegrationTest.kt     # Integration Test (실제 API)
│   └── domain/service/
│       ├── PromptOrchestratorTest.kt                  # Unit Test (Mock)
│       └── PromptOrchestratorIntegrationTest.kt       # Integration Test (실제 API)
└── resources/
    └── prompts/
        └── summarize.yml        # 테스트용 프롬프트
```

**참고**: Spring AI 2.0부터는 환경 변수(`SPRING_AI_OPENAI_API_KEY`)만 설정하면 AutoConfiguration이 자동으로 처리하므로 별도의 `application.yml` 파일이 필요하지 않습니다.

### 4. CI/CD 구성

**기본 설정 (Unit Test만)**:
```yaml
# .github/workflows/test.yml
- name: Run Tests
  run: ./gradlew ai-core:test
```

**선택적 Integration Test**:
```yaml
# .github/workflows/integration-test.yml
- name: Run Integration Tests
  env:
    SPRING_AI_OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  run: ./gradlew ai-core:integrationTest
```

---

## 확장 가능성

### 새로운 Provider 추가

1. **LlmProvider enum에 추가**:

```kotlin
enum class LlmProvider {
    OPENAI,
    ANTHROPIC  // 추가
}
```

2. **LlmModel enum에 모델 추가**:

```kotlin
enum class LlmModel(val provider: LlmProvider, val modelId: String) {
    GPT_5_MINI(LlmProvider.OPENAI, "gpt-5-mini"),
    CLAUDE_3_5_SONNET(LlmProvider.ANTHROPIC, "claude-3-5-sonnet-latest")  // 추가
}
```

3. **PromptExecutor 구현체 작성**:

`AnthropicPromptExecutor.kt`:

```kotlin
@Component
class AnthropicPromptExecutor(
    // Anthropic SDK 또는 Spring AI Anthropic 모듈 주입
) : PromptExecutor {
    override fun supports(provider: LlmProvider) =
        provider == LlmProvider.ANTHROPIC

    override suspend fun <I, O> execute(
        prompt: Prompt<I, O>,
        input: I
    ): PromptExecutionResult<O> {
        // Anthropic API 호출 로직
    }
}
```

4. **Configuration 추가**:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
```

**완료**: `PromptOrchestrator`가 자동으로 새 Executor를 인식하여 사용

### 새로운 프롬프트 추가

1. **YAML 파일 작성**: `resources/prompts/새프롬프트.yml`
2. **Input/Output DTO 정의**: Kotlin data class
3. **PromptOrchestrator 호출**: `execute("새프롬프트", input, ...)`

---

## 주요 의사결정 기록

| 의사결정 | 근거 |
|---------|------|
| Hexagonal Architecture 채택 | Provider 독립성, 테스트 용이성, 확장성 |
| YAML 프롬프트 관리 | 코드 변경 없이 수정 가능, Git 버전 관리, 도메인 응집도 향상 |
| 제네릭 타입 `Prompt<I, O>` | 컴파일 타임 타입 안전성, IDE 지원, 리팩토링 안전성 |
| Kotlin Coroutines 사용 | 비동기 I/O 효율성, 병렬 실행 단순화, Spring WebFlux 호환 |
| Sealed class 예외 계층 | 컴파일 타임 완전성 검증, 단계별 예외 구분, 명확한 복구 전략 |
| Provider별 옵션 Map 사용 | 유연성과 타입 안전성 균형, 새 Provider 추가 용이 |
| 도메인 모듈에서 프롬프트 관리 | 높은 응집도, 프롬프트-도메인 로직 동시 관리, 독립적 배포 |

---

**Tech Stack**: JDK 21 | Kotlin 2.2 | Spring Boot 4.0 | Spring AI 2.0 | Jackson
