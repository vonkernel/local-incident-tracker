# Architecture & Development Paradigm

## 설계 철학

이 프로젝트는 다음 아키텍처 원칙을 기반으로 설계되었습니다:

- **CQRS**: 데이터 쓰기(Write)와 조회(Read)를 분리하여 각각 최적화
- **이벤트 기반 아키텍처**: 서비스 간 느슨한 결합을 통한 독립적 확장
- **Polyglot Persistence**: 용도에 맞는 최적의 저장소 활용
- **Hexagonal Architecture**: 역할 기반 인터페이스로 도메인 로직과 인프라를 분리

---

## OOP + FP Hybrid 전략

**설계 전략**: Spring 컴포넌트 라이프사이클을 활용한 인터페이스 기반 OOP, 비즈니스 로직은 순수 데이터 변환을 사용하는 함수형 코어

### 컴포넌트 계층 구조

```
LAYER 1: Component Contracts (OOP)
  - 컴포넌트 간 관계를 정의하는 Kotlin 인터페이스
  - IoC 라이프사이클을 관리하는 Spring @Service Bean
  - 생성자를 통한 의존성 주입
  - Adapter Layer (이벤트 리스너, 컨트롤러, 레포지토리)
        ↓
LAYER 2: Domain Logic (FP)
  - 순수 함수: 동일 입력 → 동일 출력, 외부 상태 변경 없음
  - 함수 단위 단일 책임: 각 함수는 하나의 변환만 수행
  - 체이닝 기반 합성: 단일 책임 함수들을 체이닝으로 조합
  - 지역 변수 최소화: 중간 변수를 줄여 부수효과 삽입 여지를 구조적으로 차단
  - sealed class / Result 타입을 통한 에러 처리
  - Spring core만 사용 (Spring Data, DB 프레임워크 금지)
        ↓
LAYER 3: Data Models (Immutable)
  - 순수 data class (`shared` 모듈에 정의)
  - 비즈니스 의미를 가진 Value Object
  - 모든 모듈에서 공유하는 경계 객체
```

### 함수형 원칙

1. **Purity**: 동일 입력에 대해 동일 결과 반환, 외부 상태 변경 없음
2. **Immutability**: 모든 입출력은 불변 data class, `val` 사용
3. **Function-level SRP**: 각 함수는 하나의 변환 또는 하나의 부수효과만 수행하여 체이닝 가능한 크기 유지
4. **Composition & Minimal Local Variables**: 단일 책임 함수들을 체이닝으로 합성하고, 중간 지역 변수를 최소화하여 부수효과가 끼어들 여지를 구조적으로 차단
5. **Side Effect Isolation**: 부수효과는 별도 함수로 추출하여 메인 체인에 인라인하지 않음
   - **데이터 시스템 적용** (DB, Kafka, OpenSearch 등): 별도 함수로 추출하고, 해당 동작의 예외 처리(로깅, 재시도, fallback)도 함수 내부에 캡슐화. 함수 이름으로 어떤 시스템에 무슨 동작을 하는지 표현
   - **예외 발생**: 메인 체인에 `runCatching`/`onFailure`/`throw`를 인라인하지 않음
     - 복구 가능 → 함수 내부에서 처리 후 안전한 값 반환 (`embedOrNull`, `findOrDefault`)
     - 복구 불가능 → 함수가 throw하되 이름에 의도 표현 (`indexOrThrow`, `parseOrThrow`)
6. **Concurrency**: Coroutines를 통한 병렬 실행도 함수형 순수성 유지

### 의존성 제약 (Domain Core Layer)

- ✅ Spring framework (core, context) — `@Service`, `@Component` 등 stereotype 어노테이션을 통한 Bean 등록 허용
- ✅ Kotlin coroutines
- ❌ Spring Data (JPA, MongoDB 등)
- ❌ Domain 로직 내 ORM 프레임워크
- ❌ 데이터베이스 관련 라이브러리
- ❌ Side effect를 유발하는 프레임워크

**참고**: 이 제약은 **Domain Core Layer**에만 적용됩니다. Domain 서비스 클래스는 Spring Bean으로 등록(`@Service`)하여 IoC 컨테이너의 DI를 활용할 수 있으나, 비즈니스 로직 자체는 순수 함수로 유지해야 합니다. **Adapter Layer**에서는 Spring Data, 데이터베이스 드라이버 등 인프라 프레임워크를 사용할 수 있습니다.

### 구현 예시

**Orchestration Service (병렬 합성)**:
```kotlin
@Service
class ArticleAnalysisService(
    private val articleRefiner: ArticleRefiner,
    private val incidentTypeExtractor: IncidentTypeExtractor,
    private val urgencyExtractor: UrgencyExtractor,
    private val locationsExtractor: LocationsExtractor,
    private val keywordsExtractor: KeywordsExtractor,
    private val topicExtractor: TopicExtractor,
    private val analysisResultRepository: AnalysisResultRepository,
) {
    suspend fun analyze(article: Article) {
        analyzeArticle(article)
            .let { analysisResultRepository.save(it) }
    }

    private suspend fun analyzeArticle(article: Article): AnalysisResult = coroutineScope {
        val refinedArticle = articleRefiner.process(article)

        val incidentTypes = async { incidentTypeExtractor.process(article.articleId, refinedArticle.title, refinedArticle.content) }
        val urgency = async { urgencyExtractor.process(article.articleId, refinedArticle.title, refinedArticle.content) }
        val locations = async { locationsExtractor.process(article.articleId, refinedArticle.title, refinedArticle.content) }
        val keywords = async { keywordsExtractor.process(article.articleId, refinedArticle.summary) }
        val topic = async { topicExtractor.process(article.articleId, refinedArticle.summary) }

        AnalysisResult(
            articleId = article.articleId,
            refinedArticle = refinedArticle,
            incidentTypes = incidentTypes.await(),
            urgency = urgency.await(),
            keywords = keywords.await(),
            topic = topic.await(),
            locations = locations.await()
        )
    }
}
```

**Extractor 구현 (FP + 재시도)**:
```kotlin
abstract class RetryableAnalysisService {
    protected suspend fun <T> withRetry(operationName: String, articleId: String, block: suspend () -> T): T =
        executeWithRetry(maxRetries = 2, block = block)
}

@Service
class KeywordsExtractor(
    private val keywordAnalyzer: KeywordAnalyzer,
) : RetryableAnalysisService() {

    suspend fun process(articleId: String, summary: String): List<Keyword> =
        withRetry("extract-keywords", articleId) {
            keywordAnalyzer.analyze(summary)
        }
}
```

**부수효과 격리 (Adapter Layer)**:
```kotlin
@Service
class ArticleIndexingService(
    private val embedder: Embedder,
    private val articleIndexer: ArticleIndexer,
) {
    // 메인 함수: 체이닝으로 흐름만 표현
    suspend fun index(analysisResult: AnalysisResult, analyzedAt: Instant?) {
        if (isStale(analysisResult.articleId, analyzedAt)) return

        embedOrNull(analysisResult.refinedArticle.content)
            .let { embedding -> IndexDocumentAssembler.assemble(analysisResult, embedding, analyzedAt) }
            .also { document -> indexOrThrow(document) }
    }

    // 복구 가능한 부수효과: 안전한 값 반환
    private suspend fun embedOrNull(content: String): ByteArray? =
        runCatching { embedder.embed(content) }
            .onFailure { log.warn("Embedding failed: {}", it.message) }
            .getOrNull()

    // 복구 불가능한 부수효과: 이름에 throw 의도 표현
    private suspend fun indexOrThrow(document: ArticleIndexDocument) {
        runCatching { articleIndexer.index(document) }
            .onSuccess { log.info("Indexed: {}", document.articleId) }
            .getOrElse { e -> throw ArticleIndexingException(document.articleId, cause = e) }
    }
}
```

---

## 아키텍처 스타일

### Hexagonal Architecture (역할 기반 인터페이스 설계)

도메인 로직과 인프라를 인터페이스로 분리합니다. 클래스/인터페이스 이름에 `Port`, `Adapter` 같은 아키텍처 접미사를 사용하지 않고, 역할과 구현 기술로 의도를 표현합니다.

**의존성 방향**:
```
Inbound Adapter → Domain Service → Outbound Interface ← Outbound Adapter
(EventListener)    (IndexingService)  (ArticleIndexer)    (OpenSearchArticleIndexer)
```
- Domain은 인터페이스만 참조, 구현체를 알지 못함
- Adapter는 인터페이스를 구현하거나 Domain Service를 호출
- Adapter 간 직접 참조 금지 (다른 모듈의 adapter 내부 구현에 접근하지 않음)

**네이밍 컨벤션**:
- **인터페이스**: 역할을 나타내는 이름 — `Embedder`, `ArticleIndexer`, `Geocoder`, `KeywordAnalyzer`
- **구현 클래스**: 구현 기술을 접두사로 표현 — `OpenSearchArticleIndexer`, `KafkaDlqPublisher`, `OpenAiPromptExecutor`

**패키지 구조**:
```
{module}/
  domain/
    port/        ← 인터페이스 (역할 기반 네이밍)
    service/     ← 도메인 서비스
    model/       ← 도메인 모델
    exception/   ← 도메인 예외
  adapter/
    inbound/     ← 외부 → 시스템 (컨트롤러, 이벤트 리스너)
    outbound/    ← 시스템 → 외부 (DB, API, 메시징)
```

### CQRS 계층화

- **Write Side** (PostgreSQL): Single source of truth, 트랜잭션 일관성
- **Read Side** (OpenSearch): 비정규화된 검색 인덱스, CDC 이벤트로 업데이트
- **Eventual Consistency**: CDC가 Write/Read 모델을 연결

### Event-Driven 결합

- 서비스 간 동기 호출 없음
- 모든 통신은 Debezium CDC가 발행하는 Kafka 토픽을 통해 수행
- 서비스들은 느슨하게 결합되어 독립 배포 가능
- Outbox 패턴으로 analyzer의 다중 테이블 삽입을 원자적으로 발행

---

## 개발 흐름

### Phase 1: 인터페이스 설계 (OOP)
모듈 내 각 컴포넌트에 대해 Kotlin 인터페이스 정의:
- **인터페이스**: 서비스 계약 및 의존성
- **Domain 타입**: Value Object, Aggregate Root (data class로)
- **Adapter 인터페이스**: 데이터베이스, 메시징, 외부 API 계약

**산출물**:
- `{Module}Service` 인터페이스 (모듈이 하는 일)
- 역할 기반 인터페이스 (예: `NewsFetcher`, `Geocoder`, `PromptExecutor`)
- `{DomainType}` data class (모듈이 생산/소비하는 것)

### Phase 2: 구현 (FP + Spring)
인터페이스 설계를 기반으로 구현:

1. **Domain Service**: 인터페이스를 주입받아 비즈니스 로직 수행
   ```kotlin
   @Service
   class KeywordsExtractor(
       private val keywordAnalyzer: KeywordAnalyzer,
   ) : RetryableAnalysisService() {
       suspend fun process(articleId: String, summary: String): List<Keyword> =
           withRetry("extract-keywords", articleId) { keywordAnalyzer.analyze(summary) }
   }
   ```

2. **Orchestration Service**: 여러 Domain Service 조합
   ```kotlin
   @Service
   class ArticleAnalysisService(
       private val articleRefiner: ArticleRefiner,
       private val keywordsExtractor: KeywordsExtractor,
       // ... 기타 extractor
   ) {
       suspend fun analyze(article: Article) { /* 2-phase 오케스트레이션 */ }
   }
   ```

3. **Adapter**: 인터페이스 구현, 인프라 연결
   ```kotlin
   @Service
   class OpenAiKeywordAnalyzer(
       private val promptOrchestrator: PromptOrchestrator,
   ) : KeywordAnalyzer {
       override suspend fun analyze(text: String): List<Keyword> =
           promptOrchestrator.execute(KeywordPrompt(text))
   }
   ```

### Phase 3: 테스트
주요 로직 및 실행 검증을 위한 테스트 코드 작성:

- **Unit Test**: 순수 함수의 입출력 검증
  - 동일 입력 → 동일 출력 (함수형 순수성)
  - `data class` 동등성으로 변환 검증
  - 외부 의존성은 Mock 처리

- **Integration Test**: 외부 의존성과의 실제 연동 검증
  - DB (PostgreSQL): 쿼리, 트랜잭션, CDC 이벤트 발행이 의도대로 동작하는지
  - 외부 API (Kakao, OpenAI 등): 실제 요청/응답 흐름 검증
  - 메시징 (Kafka): 이벤트 발행/소비, 멱등성 보장
  - 검색 (OpenSearch): 인덱싱, 쿼리 결과 정합성
