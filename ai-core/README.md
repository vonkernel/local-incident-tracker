# ai-core 모듈

Spring AI 기반 LLM 프롬프트 실행 라이브러리 모듈

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
│   └── application/
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
