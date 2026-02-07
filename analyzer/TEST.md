# Analyzer Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | Line 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 18 | 60 | 74.7% |
| 통합 테스트 | 6 | (포함) | (포함) |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../domain/service/` | Extractor, Refiner, Service 테스트 |
| 단위 테스트 | `src/test/kotlin/.../domain/analyzer/` | Analyzer, Validator 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/inbound/` | Listener, DTO 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/outbound/` | Geocoding Client 테스트 |
| 통합 테스트 | `src/test/kotlin/.../domain/analyzer/` | 실제 LLM/API 호출 테스트 |
| 통합 테스트 | `src/test/kotlin/.../adapter/outbound/` | Kakao API 호출 테스트 |

## 테스트 실행

```bash
# 전체 단위 테스트
./gradlew analyzer:test

# 통합 테스트 (실제 API 호출)
./gradlew analyzer:integrationTest

# 특정 테스트 클래스
./gradlew analyzer:test --tests LocationsExtractorTest
```

## 단위 테스트 체크리스트

### ArticleAnalysisService

2단계 파이프라인 오케스트레이션 검증.

- [x] 2단계 파이프라인: refine 후 5개 분석 결과가 AnalysisResult에 올바르게 조합되어 저장된다
- [x] 분석 결과 저장 시 repository.save()가 정확히 1번 호출된다

### ArticleRefiner

기사 정제 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 및 RefinedArticle 변환
- [x] 프롬프트 실행 실패 시 예외 발생

### IncidentTypeExtractor

사건 유형 추출 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 및 IncidentType Set 변환
- [x] 참조 데이터 조회 후 프롬프트에 포함

### UrgencyExtractor

긴급도 추출 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 및 Urgency 변환
- [x] 참조 데이터 조회 후 프롬프트에 포함

### KeywordsExtractor

키워드 추출 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 및 Keyword List 변환
- [x] 가중치 기반 정렬 검증

### TopicExtractor

토픽 추출 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 및 Topic 변환

### LocationsExtractor

위치 추출 3단계 파이프라인 검증.

- [x] ADDRESS 타입 - geocodeByAddress로 해소된다
- [x] ADDRESS 타입 - geocodeByAddress 빈 결과 시 unresolvedLocation 반환
- [x] LANDMARK 타입 - geocodeByKeyword 우선 호출, 빈 결과 시 geocodeByAddress fallback
- [x] UNRESOLVABLE 타입 - geocoding API 호출 없이 UNKNOWN Location 생성
- [x] 여러 ExtractedLocation이 병렬 geocoding 후 flatten된다
- [x] LocationValidator가 일부 위치를 필터링하고 정규화하면, 필터링된 결과만 geocoding된다

### DefaultIncidentTypeAnalyzer

사건 유형 LLM 분석 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 성공 시 IncidentType Set 반환
- [x] 프롬프트 실행 실패 시 예외 발생

### DefaultUrgencyAnalyzer

긴급도 LLM 분석 로직 검증 (Mock 기반).

- [x] 프롬프트 실행 성공 시 Urgency 반환
- [x] 프롬프트 실행 실패 시 예외 발생

### DefaultLocationValidator

위치 검증 LLM 로직 검증 (Mock 기반).

- [x] LLM이 유효한 위치만 반환하면 해당 위치만 포함
- [x] LLM이 위치를 정규화하면 정규화된 결과 반환
- [x] LLM이 빈 결과 반환 시 빈 리스트

### KakaoGeocodingClient

Kakao API 호출 및 응답 매핑 검증 (Mock 기반).

**geocodeByAddress**:
- [x] 주소 검색 성공 (b_code + h_code) - HADONG 우선 1개 Location
- [x] 주소 검색 성공 (b_code만) - BJDONG 1개 Location
- [x] 주소 검색 성공 (h_code만) - HADONG 1개 Location
- [x] 주소 검색 성공 (address null) - 좌표 기반 UNKNOWN Location
- [x] 주소 검색 결과 없음 (단일 토큰) - broader fallback 없이 빈 리스트
- [x] 주소 검색 결과 없음 (다중 토큰) - 마지막 토큰 제거 후 broader 재검색 성공
- [x] 주소 검색 결과 없음 (다중 토큰) - broader 재검색도 실패 시 빈 리스트
- [x] API 예외 발생 시 예외가 그대로 전파된다

**geocodeByKeyword**:
- [x] 키워드 검색 성공 후 주소 검색으로 HADONG Location을 반환한다
- [x] 키워드 검색 성공 + 주소 검색 실패 시 좌표 기반 UNKNOWN Location을 반환한다
- [x] 키워드 검색 결과 없음 시 빈 리스트를 반환한다
- [x] API 예외 발생 시 예외가 그대로 전파된다

### CachedGeocoder

캐싱 데코레이터 동작 검증.

- [x] 캐시 히트 시 실제 geocoder 호출 안 함
- [x] 캐시 미스 시 실제 geocoder 호출 후 캐싱

### ArticleEventListener

Kafka 이벤트 수신 및 처리 검증.

- [x] op=c 이벤트 수신 시 articleAnalysisService.analyze()를 호출한다
- [x] op=u 이벤트 수신 시 analyze()를 호출하지 않는다
- [x] op=d 이벤트 수신 시 analyze()를 호출하지 않는다
- [x] after가 null인 create 이벤트 수신 시 analyze()를 호출하지 않는다
- [x] JSON 역직렬화 실패 시 해당 레코드만 실패하고 DLQ로 발행된다
- [x] 분석 실패 시 DLQ로 발행된다

### DlqEventListener

DLQ 재처리 로직 검증.

- [x] 최대 재시도 횟수 초과 시 메시지를 폐기한다
- [x] 정상 재처리 시 analyze()를 호출한다
- [x] 이벤트가 기존 분석보다 오래된 경우 폐기한다
- [x] 재처리 실패 시 retry count를 증가시켜 DLQ에 다시 발행한다
- [x] non-create 이벤트는 무시한다
- [x] retry count 헤더가 없으면 0으로 처리한다

### DebeziumArticleEvent

DTO 역직렬화 검증.

- [x] Debezium envelope 역직렬화 성공
- [x] Article payload → Article 변환 성공

## 통합 테스트 체크리스트

### DefaultIncidentTypeAnalyzerIntegrationTest

실제 LLM API 호출 검증.

- [x] 실제 LLM 호출 및 IncidentType 추출

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### DefaultUrgencyAnalyzerIntegrationTest

실제 LLM API 호출 검증.

- [x] 실제 LLM 호출 및 Urgency 추출

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### DefaultLocationAnalyzerIntegrationTest

실제 LLM API 호출 검증.

- [x] 실제 LLM 호출 및 ExtractedLocation 추출

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### DefaultLocationValidatorIntegrationTest

실제 LLM API 호출 검증.

- [x] 실제 LLM 호출 및 위치 검증/정규화

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### DefaultKeywordAnalyzerIntegrationTest

실제 LLM API 호출 검증.

- [x] 실제 LLM 호출 및 Keyword 추출

**필요 환경**:
- `SPRING_AI_OPENAI_API_KEY` 환경변수 필수
- 네트워크 연결 필요

### KakaoGeocodingClientIntegrationTest

실제 Kakao API 호출 검증.

- [x] 실제 주소 검색 API 호출 및 Location 변환
- [x] 실제 키워드 검색 API 호출 및 Location 변환

**필요 환경**:
- `KAKAO_REST_API_KEY` 환경변수 필수
- 네트워크 연결 필요

## 테스트 환경

### 단위 테스트
- Spring Context 없이 실행
- MockK를 사용한 의존성 모킹
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- WebClient Mono 응답 모킹

### 통합 테스트
- `@Tag("integration")` 태그로 분리
- `@SpringBootTest` 사용 (`IntegrationTestApplication`)
- 실제 OpenAI/Kakao API 호출
- `.env.local` 파일에서 API Key 로드
- LLM 응답은 예측 불가능하므로 **구조만 검증** (내용 검증 X)

## 테스트 지원 클래스

| 클래스 | 역할 |
|--------|------|
| `IntegrationTestApplication` | 통합 테스트용 Spring Boot Application |
| `DebeziumEnvelope` | Debezium CDC 이벤트 테스트용 DTO |
| `ArticlePayload` | Article 이벤트 테스트용 DTO |
