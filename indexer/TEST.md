# Indexer Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | Line 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 7 | 61 | 82.6% |
| 통합 테스트 | - | - | - |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../domain/assembler/` | 문서 변환 순수 함수 테스트 |
| 단위 테스트 | `src/test/kotlin/.../domain/service/` | 인덱싱 서비스 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/inbound/consumer/` | Kafka 리스너 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/outbound/` | OpenSearch, Embedding 어댑터 테스트 |

## 테스트 실행

```bash
# 전체 단위 테스트
./gradlew indexer:test

# 특정 테스트 클래스
./gradlew indexer:test --tests IndexDocumentAssemblerTest
./gradlew indexer:test --tests ArticleIndexingServiceTest
```

## 단위 테스트 체크리스트

### IndexDocumentAssembler

`AnalysisResult` → `ArticleIndexDocument` 변환 순수 함수 검증.

- [x] assemble은 모든 필드를 올바르게 매핑한다
- [x] assemble은 writtenAt Instant를 UTC ZonedDateTime으로 변환한다
- [x] assemble은 좌표가 있는 위치에서 geoPoints를 추출한다
- [x] assemble은 위치에서 주소를 추출한다
- [x] assemble은 jurisdictionCodes에서 UNKNOWN 코드를 필터링한다
- [x] assemble은 null 좌표를 geoPoints에서 제외한다
- [x] assemble은 빈 위치 목록을 처리한다
- [x] assemble은 선택 필드를 null로 설정한다
- [x] 임베딩 없이 assemble하면 contentEmbedding이 null이다

### ArticleIndexingService

인덱싱 파이프라인 오케스트레이션 검증.

**단건 인덱싱**:
- [x] index는 임베딩과 함께 문서를 조립하여 인덱싱한다
- [x] 임베더 실패 시 null 임베딩으로 인덱싱 진행
- [x] 인덱서 실패 시 ArticleIndexingException 발생
- [x] 기존 문서가 더 최신이면 이벤트 건너뜀
- [x] 기존 문서와 타임스탬프가 같으면 이벤트 건너뜀
- [x] 기존 문서가 더 오래되면 인덱싱 진행
- [x] analyzedAt 없으면 신선도 검사 생략하고 인덱싱 진행
- [x] 신선도 검사 실패 시 인덱싱 진행
- [x] 일시적 실패 시 재시도 후 성공
- [x] 재시도 소진 후 예외 발생

**배치 인덱싱**:
- [x] indexAll은 배치 임베딩으로 문서를 조립하여 인덱싱한다
- [x] 배치 임베딩 실패 시 null 임베딩으로 인덱싱 진행
- [x] 인덱서 실패 시 예외 전파
- [x] 빈 리스트에 대해 아무 작업도 하지 않음
- [x] 배치에서 오래된 이벤트 필터링
- [x] 모든 이벤트가 오래되면 전부 건너뜀
- [x] indexAll 일시적 실패 시 재시도 후 성공

### AnalysisResultEventListener

Kafka 배치 이벤트 처리 검증.

- [x] CREATE 이벤트를 배치로 처리한다
- [x] CREATE가 아닌 이벤트는 무시한다
- [x] 유효하지 않은 레코드는 DLQ로 발행하고 유효한 레코드는 배치 처리한다
- [x] 배치 실패 시 레코드별 폴백 후 여전히 실패하는 레코드는 DLQ로 전송한다
- [x] 배치 실패 후 모든 레코드별 폴백이 성공하면 DLQ에 전송하지 않는다
- [x] 부분 bulk 실패 시 실패한 articleId만 개별 재시도한다
- [x] DLQ 발행 실패 시 오프셋 커밋 방지를 위해 예외 발생
- [x] 역직렬화 실패 시 원시 레코드를 DLQ에 발행한다
- [x] 역직렬화 DLQ 발행 실패 시 오프셋 커밋 방지를 위해 예외 발생
- [x] 모든 레코드가 CREATE가 아니면 indexAll을 호출하지 않는다

### DlqEventListener

DLQ 재처리 로직 검증.

- [x] DLQ 이벤트 재인덱싱 성공
- [x] 최대 재시도 초과 시 이벤트 폐기
- [x] 실패 시 재시도 횟수 증가하여 DLQ에 재발행
- [x] CREATE가 아닌 이벤트는 무시한다
- [x] 재시도 헤더 누락 시 0으로 처리
- [x] DLQ 재발행 실패 시 오프셋 커밋 방지를 위해 예외 발생
- [x] 재시도 횟수가 0보다 크면 백오프 지연 적용
- [x] 재시도 횟수가 0이면 백오프 지연 없음

### EmbeddingAdapter

ai-core EmbeddingExecutor 위임 및 ByteArray 변환 검증.

- [x] embed 성공 시 FloatArray를 ByteArray로 변환하여 반환
- [x] embedAll 성공 시 각 FloatArray를 ByteArray로 변환
- [x] embedAll에 빈 리스트 전달 시 빈 리스트 반환
- [x] embedAll 실행 실패 시 null 리스트 반환

### OpenSearchArticleIndexer

OpenSearch 클라이언트 호출 검증 (Mock 기반).

- [x] index는 올바른 인덱스와 문서 ID로 OpenSearch 클라이언트를 호출한다
- [x] delete는 올바른 인덱스와 ID로 OpenSearch 클라이언트를 호출한다
- [x] indexAll은 모든 문서로 OpenSearch bulk API를 호출한다
- [x] indexAll은 빈 리스트에 대해 아무 작업도 하지 않는다
- [x] findModifiedAtByArticleId는 문서 존재 시 Instant를 반환한다
- [x] findModifiedAtByArticleId는 문서 미발견 시 null을 반환한다
- [x] findModifiedAtByArticleId는 OpenSearch 예외 시 null을 반환한다
- [x] indexAll은 부분 실패 시 BulkIndexingPartialFailureException을 발생시킨다
- [x] indexAll은 bulk 응답에 오류가 없으면 성공한다

### DebeziumOutboxEvent

CDC 이벤트 역직렬화 검증.

- [x] Debezium envelope를 올바르게 역직렬화한다
- [x] payload JSON 문자열로 OutboxPayload를 역직렬화한다
- [x] toAnalysisResult는 이중 역직렬화를 수행한다
- [x] envelope의 알 수 없는 필드를 무시한다

## 테스트 환경

### 단위 테스트
- Spring Context 없이 실행
- MockK를 사용한 의존성 모킹
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- OpenSearch Client 응답 모킹

### 통합 테스트 (추후 추가 예정)
- `@Tag("integration")` 태그로 분리
- Testcontainers 또는 로컬 OpenSearch 인스턴스 사용
- 실제 OpenAI Embedding API 호출 (API Key 필요)

## 테스트 지원 클래스

| 클래스 | 역할 |
|--------|------|
| `DebeziumOutboxEnvelope` | Debezium CDC 이벤트 테스트용 DTO |
| `OutboxPayload` | Outbox 페이로드 테스트용 DTO |
| `BulkIndexingPartialFailureException` | Bulk 인덱싱 부분 실패 예외 |
