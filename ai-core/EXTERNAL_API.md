# ai-core External APIs

이 모듈이 호출하는 외부 API 레퍼런스.

## 목차

- [API 1: OpenAI Chat Completions API](#api-1-openai-chat-completions-api)
- [API 2: OpenAI Embeddings API](#api-2-openai-embeddings-api)

---

## API 1: OpenAI Chat Completions API

### 개요

| 항목 | 값 |
|------|------|
| 공식 문서 | [OpenAI API Reference - Chat](https://platform.openai.com/docs/api-reference/chat) |
| Base URL | `https://api.openai.com/v1` |
| 인증 방식 | Bearer Token |
| 환경변수 | `SPRING_AI_OPENAI_API_KEY` |

### 인증

```
Authorization: Bearer sk-your-api-key
```

### 엔드포인트

#### Chat Completions

**요청**

```
POST /chat/completions
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|:----:|------|
| `model` | string | O | 모델 ID (예: `gpt-5-mini`) |
| `messages` | array | O | 대화 메시지 배열 |
| `temperature` | number | - | 응답 다양성 (0.0~2.0) |
| `max_completion_tokens` | integer | - | 최대 응답 토큰 수 |
| `response_format` | object | - | 응답 형식 지정 |

**messages 배열 항목**

| 필드 | 타입 | 설명 |
|------|------|------|
| `role` | string | `system`, `user`, `assistant` 중 하나 |
| `content` | string | 메시지 내용 |

**응답**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1706745600,
  "model": "gpt-5-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "{\"summary\": \"요약 내용\"}"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 50,
    "completion_tokens": 30,
    "total_tokens": 80
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `choices[0].message.content` | string | LLM 응답 텍스트 (JSON 형식으로 파싱) |
| `usage.prompt_tokens` | integer | 입력 토큰 수 |
| `usage.completion_tokens` | integer | 출력 토큰 수 |

**요청 예시**

```bash
curl -X POST "https://api.openai.com/v1/chat/completions" \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-5-mini",
    "messages": [
      {"role": "user", "content": "다음 텍스트를 요약하세요: ..."}
    ],
    "temperature": 0.3,
    "max_completion_tokens": 500,
    "response_format": {"type": "json_object"}
  }'
```

### 에러 처리

| 상태 코드 | 원인 | 대응 |
|:---------:|------|------|
| 400 | 잘못된 요청 파라미터 | 요청 형식 확인 |
| 401 | 잘못된 API 키 | `LlmAuthenticationException` 발생 |
| 429 | Rate Limit 초과 | `LlmRateLimitException` 발생, 재시도 (지수 백오프) |
| 500 | OpenAI 서버 에러 | `LlmApiException` 발생, 재시도 |

### 모듈 내 사용

| 컴포넌트 | 호출 엔드포인트 | 용도 |
|---------|---------------|------|
| `OpenAiPromptExecutor` | `/chat/completions` | 프롬프트 실행 및 JSON 응답 파싱 |

### Provider 옵션 매핑

YAML 프롬프트의 `providerSpecificOptions` → OpenAI API 파라미터 변환:

| YAML 옵션 | API 파라미터 | 설명 |
|----------|-------------|------|
| `responseFormat` | `response_format.type` | `json_object` 또는 `text` |
| `seed` | `seed` | 결정적 응답을 위한 시드 값 |

---

## API 2: OpenAI Embeddings API

### 개요

| 항목 | 값 |
|------|------|
| 공식 문서 | [OpenAI API Reference - Embeddings](https://platform.openai.com/docs/api-reference/embeddings) |
| Base URL | `https://api.openai.com/v1` |
| 인증 방식 | Bearer Token |
| 환경변수 | `SPRING_AI_OPENAI_API_KEY` |

### 인증

```
Authorization: Bearer sk-your-api-key
```

### 엔드포인트

#### Create Embeddings

**요청**

```
POST /embeddings
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|:----:|------|
| `model` | string | O | 모델 ID (예: `text-embedding-3-small`) |
| `input` | string \| array | O | 임베딩할 텍스트 (단건 또는 배열) |
| `dimensions` | integer | - | 출력 벡터 차원 수 (기본: 모델별 상이) |

**응답**

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.0023064255, -0.009327292, ...]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 8,
    "total_tokens": 8
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `data[].embedding` | array | 벡터 값 배열 (FloatArray) |
| `data[].index` | integer | 입력 텍스트 인덱스 (배치 시 사용) |
| `usage.prompt_tokens` | integer | 입력 토큰 수 |

**요청 예시 (단건)**

```bash
curl -X POST "https://api.openai.com/v1/embeddings" \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": "서울에서 화재가 발생했습니다.",
    "dimensions": 128
  }'
```

**요청 예시 (배치)**

```bash
curl -X POST "https://api.openai.com/v1/embeddings" \
  -H "Authorization: Bearer sk-your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "text-embedding-3-small",
    "input": ["텍스트 1", "텍스트 2", "텍스트 3"],
    "dimensions": 128
  }'
```

### 에러 처리

| 상태 코드 | 원인 | 대응 |
|:---------:|------|------|
| 400 | 잘못된 요청 (빈 텍스트 등) | 입력 검증 |
| 401 | 잘못된 API 키 | `LlmAuthenticationException` 발생 |
| 429 | Rate Limit 초과 | `LlmRateLimitException` 발생, 재시도 (지수 백오프) |
| 500 | OpenAI 서버 에러 | `LlmExecutionException` 발생, 재시도 |

### 모듈 내 사용

| 컴포넌트 | 호출 엔드포인트 | 용도 |
|---------|---------------|------|
| `OpenAiEmbeddingExecutor` | `/embeddings` | 텍스트 → 벡터 변환 (단건/배치) |

### 사용 모델

| 모델 | 기본 차원 | 사용 차원 | 용도 |
|------|:--------:|:--------:|------|
| `text-embedding-3-small` | 1536 | 128 | 문서 임베딩 (indexer, searcher) |

차원 수를 128로 축소하여 저장 공간 및 검색 성능 최적화.

### Spring AI 통합

ai-core는 OpenAI API를 직접 호출하지 않고 **Spring AI**를 통해 추상화된 방식으로 호출한다.

```kotlin
// OpenAiEmbeddingExecutor
suspend fun embed(text: String, model: EmbeddingModel, dimensions: Int?): FloatArray {
    val options = OpenAiEmbeddingOptions.builder()
        .withModel(model.modelId)
        .withDimensions(dimensions)
        .build()

    val response = embeddingModel.call(
        EmbeddingRequest(listOf(text), options)
    )

    return response.result.output
}
```

Spring AI AutoConfiguration이 `SPRING_AI_OPENAI_API_KEY` 환경변수를 자동으로 읽어 설정한다.
