# Collector Architecture

## 역할 및 책임

**collector**는 연합뉴스 재난 API로부터 사건사고 데이터를 수집하여 정규화하고 PostgreSQL에 저장하는 데이터 수집 파이프라인의 첫 단계입니다.

### 핵심 책임

- 외부 API(연합뉴스)로부터 원본 데이터 수집
- 외부 데이터 형식을 내부 표준 형식(Article)으로 정규화
- 데이터 유효성 검증 및 중복 제거
- PostgreSQL에 Article 저장 (CDC를 통해 analyzer로 자동 전달)

### 데이터 흐름

```
Yonhapnews API (안전행정부 재난 API)
    ↓ (HTTP GET with pagination)
[collector]
    ↓ (normalize & validate)
Article (shared model)
    ↓ (filterNonExisting - shared)
    ↓ (persist only new articles)
PostgreSQL (articles table)
    ↓ (CDC via Debezium)
Kafka (article-events topic)
    ↓
[analyzer]
```

### API 특성 및 제약사항

**API 엔드포인트**: `https://www.safetydata.go.kr/V2/api/DSSP-IF-00051`

**주요 파라미터**:
- `inqDt`: 조회 날짜 (YYYYMMDD) - **시스템 등록일(CRT_DT) 기준**
- `pageNo`: 페이지 번호 (1부터 시작)
- `numOfRows`: 페이지당 결과 수 (최대 1000)
- `serviceKey`: API 인증 키

**API 응답 필드**:
- `YNA_NO`: 연합뉴스 기사 고유 ID (**originId로 사용**)
- `YNA_YMD`: 기사 작성/발행 일시 (**writtenAt으로 사용**)
- `CRT_DT`: 시스템 등록 일시 (API 필터링 기준)
- `YNA_TTL`: 기사 제목
- `YNA_CN`: 기사 본문
- `YNA_WRTR_NM`: 작성자 (연합뉴스)

**중요한 API 동작 특성** (실제 관찰 결과):

1. **inqDt 파라미터의 불명확한 필터링**:
   - `inqDt=20260112`: totalCount=753, 첫 번째 기사 YNA_NO=3845 (YNA_YMD=2026-01-12 09:09:54)
   - `inqDt=20260111`: totalCount=794, 첫 번째 기사 YNA_NO=3845 (YNA_YMD=2026-01-12 09:09:54)
   - `inqDt=20260115`: totalCount=570, 첫 번째 기사 YNA_NO=4082 (YNA_YMD=2026-01-15 21:03:15)
   - 서로 다른 `inqDt`로 조회해도 동일한 기사가 첫 번째로 반환됨
   - `inqDt=20260112` 조회 결과에 1월 12일, 13일, 14일, 15일 작성 기사 모두 포함

2. **정렬 순서**: 명확한 정렬 기준 없음
   - CRT_DT, YNA_YMD, YNA_NO 모두 일관된 정렬 순서 보이지 않음

**대응 전략**:
- `YNA_NO` (originId) 기반 중복 제거 필수
- 정기적 재수집으로 늦게 등록된 기사 보완
- 빠른 페이지네이션으로 데이터 변경 최소화

---

## 아키텍처 패턴

collector는 **Hexagonal Architecture (Ports & Adapters)**를 기반으로 설계되며, 프로젝트 전체의 **OOP + FP Hybrid** 전략을 따릅니다.

### 계층 구조

```
┌───────────────────────────────────────────┐
│ Adapter Layer (OOP - Spring Beans)        │
│ - HTTP Client (Yonhapnews API)            │
│ - Repository Adapter (PostgreSQL)         │
│ - Scheduler (periodic collection)         │
│ - External API Models:                    │
│   └─ SafetyDataApiResponse                │
│   └─ YonhapnewsArticle                    │
└──────────────┬────────────────────────────┘
               ↓
┌───────────────────────────────────────────┐
│ Port Interfaces (Contracts)               │
│ - NewsApiPort: external API contract      │
│ - ArticleRepository: persistence (shared) │
└──────────────┬────────────────────────────┘
               ↓
┌──────────────────────────────────────────┐
│ Domain Core (FP - Pure Functions)        │
│ - normalizeApiResponse(): normalization  │
│ - validateArticle(): validation          │
└──────────────┬───────────────────────────┘
               ↓
┌──────────────────────────────────────────┐
│ Domain Models (Immutable)                │
│ - Article (shared module)                │
└──────────────────────────────────────────┘
```

**Layer Responsibilities**:
- **Adapter Layer**: External system interaction, framework-specific code, API response models
- **Port Interfaces**: Contracts for dependencies (defined in shared or collector)
- **Domain Core**: Pure business logic, no side effects
- **Domain Models**: Core business entities (defined in shared)

### 핵심 컴포넌트

#### 1. Domain Core (Pure Functions)

**Core business logic without side effects**:

```kotlin
// Normalize external API response to domain model
fun normalizeApiResponse(response: YonhapnewsArticle): Article

// Validate article business rules
fun validateArticle(article: Article): Result<Article>
```

**Characteristics**:
- Immutable inputs and outputs
- No side effects (no external state mutation)
- Deterministic (same input → same output)
- Easy to test (pure function testing)

**Note**: Deduplication is handled by `ArticleRepository.filterNonExisting()`, not in domain core

#### 2. Port Interfaces

**ArticleRepository** (defined in `shared` module):
```kotlin
// com.vonkernel.lit.repository.ArticleRepository
interface ArticleRepository {
    fun save(article: Article): Article
    fun saveAll(articles: Collection<Article>): List<Article>
    fun filterNonExisting(articleIds: Collection<String>): List<String>
}
```

**Usage**:
- `filterNonExisting(articleIds)`: returns list of articleIds that don't exist in DB
- Used for deduplication before saving articles

**NewsApiPort** (collector-specific):
```kotlin
interface NewsApiPort {
    suspend fun fetchArticles(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int
    ): SafetyDataApiResponse
}
```

#### 3. Adapter Layer (Spring Components)

**HTTP Client Adapter**:
```kotlin
@Service
class YonhapnewsApiClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${safetydata.api.base-url}") private val baseUrl: String,
    @Value("\${safetydata.api.key}") private val apiKey: String
) : NewsApiPort {

    private val webClient: WebClient = webClientBuilder
        .baseUrl(baseUrl)
        .build()

    override suspend fun fetchArticles(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int
    ): SafetyDataApiResponse {
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/V2/api/DSSP-IF-00051")
                    .queryParam("inqDt", inqDt)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", numOfRows)
                    .queryParam("serviceKey", apiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(SafetyDataApiResponse::class.java)
            .timeout(Duration.ofMillis(500))  // 500ms timeout
            .awaitSingle()  // Kotlin coroutines extension (kotlinx-coroutines-reactor)
    }
}
```

**Configuration** (application.yaml):
```yaml
safetydata:
  api:
    base-url: https://www.safetydata.go.kr
    key: ${SAFETYDATA_API_KEY}
```

**Dependencies** (build.gradle.kts):
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
```

**Repository Adapter** (implementation in `persistence` module):
```kotlin
// ArticleRepository interface is in shared module
// Implementation uses JPA repositories
```

**Scheduler**:
```kotlin
@Service
class ArticleCollectionScheduler(
    private val newsApiPort: NewsApiPort,
    private val articleRepository: ArticleRepository
) {
    @EventListener(ApplicationReadyEvent::class)
    suspend fun initialCollection() = collectToday()

    @Scheduled(cron = "0 */10 * * * *")
    suspend fun scheduledCollection() = collectToday()

    private suspend fun collectToday() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        collectArticlesForDate(today)
    }
}
```

---

## 개발 흐름 (TDD)

### Phase 1: Interface Design

**1. Import Domain Models** (from shared module):
   - `Article` from `com.vonkernel.lit.entity`
   - `ArticleRepository` from `com.vonkernel.lit.repository`

**2. Define Port Interfaces** (collector-specific):
   ```kotlin
   interface NewsApiPort {
       suspend fun fetchArticles(
           inqDt: String,
           pageNo: Int,
           numOfRows: Int
       ): SafetyDataApiResponse
   }
   ```

**Deliverables**:
- Port interfaces defined
- Dependency contracts established
- Domain model usage clarified

### Phase 2: Test-First Development

**Unit Tests** (Pure Functions):
```kotlin
@Test
fun `normalizeApiResponse should convert API response to Article`()

@Test
fun `validateArticle should reject invalid articles`()

@Test
fun `validateArticle should accept valid articles`()
```

**Integration Tests**:
```kotlin
@Test
fun `ArticleRepository filterNonExisting should return only new article IDs`()

@Test
fun `Collection flow should save only new articles`()
```

### Phase 3: Implementation

**1. Define Adapter Layer Models**:
   - `SafetyDataApiResponse`: API response structure
   - `YonhapnewsArticle`: External API article format
   - Located in adapter layer (not domain)

**2. Implement Pure Functions** (domain core):
   - `normalizeApiResponse()`: YonhapnewsArticle → Article
   - `validateArticle()`: Business rule validation
   - `parseYnaYmd()`: String → Instant conversion

**3. Implement Adapters**:
   - HTTP Client: `YonhapnewsApiClient` (implements NewsApiPort)
   - Repository: Implementation in persistence module
   - Scheduler: `ArticleCollectionScheduler`

**4. Wire Components**:
   - Spring dependency injection
   - Configuration properties
   - Orchestration logic

**Deliverables**:
- Working adapters
- Tested pure functions
- Integrated system

---

## Dependency Constraints

### Domain Core Layer
**Allowed**:
- ✅ Kotlin stdlib
- ✅ Kotlin coroutines
- ✅ Domain models from shared module

**Prohibited**:
- ❌ Spring Framework (Data, Web, etc.)
- ❌ JPA / Database libraries
- ❌ HTTP client libraries
- ❌ Any infrastructure-specific dependencies

**Rationale**: Domain core must remain pure and framework-independent

### Adapter Layer
**Allowed**:
- ✅ Spring Boot, Spring Web (MVC)
- ✅ Spring Data JPA (via persistence module)
- ✅ WebClient (reactive HTTP client)
- ✅ Reactor (Mono/Flux) + Coroutines bridge
- ✅ External libraries
- ✅ Framework-specific annotations

**Purpose**: Handle all infrastructure concerns and external system integration

**Dependency Management**:
- `spring-boot-starter-webclient` provides WebClient without WebFlux server overhead
- Uses Reactor Netty for non-blocking HTTP client
- `kotlinx-coroutines-reactor` enables Coroutines extensions (`.awaitSingle()`, etc.)

---

## 수집 전략

collector는 세 가지 수집 시나리오를 지원하며, 모두 동일한 페이지네이션 로직과 중복 제거 메커니즘을 사용합니다.

### 1. 초기 수집 (Application Startup)

**트리거**: Spring Boot 애플리케이션 시작 시 자동 실행

**동작**:
```kotlin
@EventListener(ApplicationReadyEvent::class)
suspend fun initialCollection() {
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    collectArticlesForDate(today)
}
```

**목적**:
- 애플리케이션 시작 시점의 오늘 날짜 모든 기사 수집
- 시스템 재시작 시 누락 데이터 보완

**수집 범위**: KST 기준 오늘 날짜 (CRT_DT 기준)

### 2. 정기 수집 (Scheduled Collection)

**트리거**: `@Scheduled` - 10분 주기 (`0 */10 * * * *`)

**동작**:
```kotlin
@Scheduled(cron = "0 */10 * * * *")
suspend fun scheduledCollection() {
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    collectArticlesForDate(today)
}
```

**목적**:
- 10분마다 오늘 날짜 새로운 기사 발견 및 수집
- 실시간성 유지 (늦게 등록되는 기사 대응)

**수집 범위**: KST 기준 오늘 날짜 (CRT_DT 기준)

**중복 처리**:
- 이미 수집된 기사는 `ArticleRepository.filterNonExisting()`으로 자동 필터링
- 새로운 기사만 DB에 저장

### 3. Backfill 수집 (Manual API Request)

**트리거**: HTTP REST API 호출

**API 명세**:
```http
POST /api/collector/backfill
Content-Type: application/json

{
  "startDate": "2026-01-10"
}
```

**동작**:
```kotlin
@PostMapping("/api/collector/backfill")
suspend fun backfill(@RequestBody request: BackfillRequest) {
    val targetDate = LocalDate.parse(request.startDate)
    collectArticlesForDate(targetDate)  // 해당 날짜의 모든 페이지 수집
}
```

**목적**:
- 특정 날짜의 과거 데이터 소급 수집
- 시스템 장애 복구 시 누락 데이터 보완
- 늦게 등록된 기사 재수집

**수집 범위**:
- `startDate` 날짜에 대해 전체 페이지네이션 수행
- API가 반환하는 모든 페이지를 순회하여 완료

**종료 조건**:
- `pageNo > totalPages` 도달 시 종료
- 페이지네이션을 끝까지 완료해야 해당 날짜 수집 완료

**주의사항**:
- 하루치 데이터가 수백 페이지일 수 있음 (처리 시간 고려)
- 여러 날짜 backfill은 반복 호출 필요

### 공통 수집 로직

모든 수집 시나리오는 다음 함수형 스타일 로직을 사용합니다:

```kotlin
suspend fun collectArticlesForDate(date: LocalDate) {
    val inqDt = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    // Phase 1: Fetch first page to determine total pages
    val firstPage = fetchPageWithRetry(inqDt, pageNo = 1, numOfRows = 1000)
    val totalPages = calculateTotalPages(firstPage.totalCount, pageSize = 1000)

    // Phase 2: Process first page
    val firstPageResult = runCatching { processAndSave(firstPage.body) }

    // Phase 3: Fetch and process remaining pages (functional style)
    val remainingResults = (2..totalPages)
        .map { pageNo -> fetchAndProcess(inqDt, pageNo) }
        .partition { it.isSuccess }

    // Phase 4: Collect all results
    val allResults = listOf(firstPageResult) + remainingResults.first + remainingResults.second
    val failedPages = allResults
        .withIndex()
        .filter { it.value.isFailure }
        .map { it.index + 1 }

    // Phase 5: Retry failed pages (if any)
    if (failedPages.isNotEmpty()) {
        logger.warn("Retrying ${failedPages.size} failed pages for $inqDt")
        val retryResults = retryPages(inqDt, failedPages)

        val stillFailed = retryResults
            .withIndex()
            .filter { it.value.isFailure }
            .map { failedPages[it.index] }

        if (stillFailed.isNotEmpty()) {
            throw CollectionException(
                "Failed to collect pages $stillFailed for $inqDt after retry"
            )
        }
    }
}

// Pure function: calculate total pages
private fun calculateTotalPages(totalCount: Int, pageSize: Int): Int =
    (totalCount + pageSize - 1) / pageSize

// Suspend function: fetch and process single page
private suspend fun fetchAndProcess(inqDt: String, pageNo: Int): Result<Unit> =
    runCatching {
        val response = fetchPageWithRetry(inqDt, pageNo, numOfRows = 1000)
        processAndSave(response.body)
    }.onFailure { e ->
        logger.error("Failed to fetch page $pageNo for $inqDt", e)
    }

// Suspend function: retry multiple pages
private suspend fun retryPages(inqDt: String, pageNumbers: List<Int>): List<Result<Unit>> =
    pageNumbers.map { pageNo ->
        runCatching {
            val response = fetchPageWithRetry(inqDt, pageNo, numOfRows = 1000)
            processAndSave(response.body)
        }.onSuccess {
            logger.info("Successfully retried page $pageNo")
        }.onFailure { e ->
            logger.error("Retry failed for page $pageNo", e)
        }
    }

// Suspend function: fetch with exponential backoff retry
private suspend fun fetchPageWithRetry(
    inqDt: String,
    pageNo: Int,
    numOfRows: Int,
    maxRetries: Int = 3
): SafetyDataApiResponse =
    (0 until maxRetries).fold(null as SafetyDataApiResponse?) { _, attempt ->
        runCatching {
            apiClient.fetchArticles(inqDt, pageNo, numOfRows)
        }.getOrElse { error ->
            if (attempt == maxRetries - 1) {
                throw CollectionException("Failed after $maxRetries retries", error)
            }

            val backoffDelay = calculateBackoffDelay(attempt)
            logger.warn("Retry attempt ${attempt + 1} after ${backoffDelay}ms")
            delay(backoffDelay)
            null
        }
    } ?: error("Unreachable: retry logic guarantees a result or exception")

// Pure function: exponential backoff calculation
private fun calculateBackoffDelay(attempt: Int): Long =
    2.0.pow(attempt).toLong() * 1000

// Suspend function: process and save articles (pure transformation + side effect)
private suspend fun processAndSave(items: List<YonhapnewsArticle>) {
    val newArticles = items
        .asSequence()
        .map { normalizeApiResponse(it) }
        .mapNotNull { validateArticle(it).getOrNull() }
        .toList()
        .let { articles ->
            val articleIds = articles.map { it.articleId }
            val nonExistingIds = articleRepository.filterNonExisting(articleIds).toSet()
            articles.filter { it.articleId in nonExistingIds }
        }

    if (newArticles.isNotEmpty()) {
        articleRepository.saveAll(newArticles)
    }
}
```

**재시도 전략**:
1. **페이지별 독립 재시도**:
   - 실패한 페이지만 추적 (`failedPages` Set)
   - 나머지 페이지는 정상 처리 (부분 실패 허용)

2. **Exponential Backoff**:
   - 1차 재시도: 2초 대기
   - 2차 재시도: 4초 대기
   - 3차 재시도: 8초 대기

3. **2단계 재시도**:
   - Phase 1: 전체 페이지 순회 (실패 시 다음 페이지 진행)
   - Phase 2: 실패한 페이지만 재시도

4. **최종 실패 처리**:
   - 재시도 후에도 실패하면 `CollectionException` throw
   - 실패한 페이지 번호 로그 기록
   - 모니터링/알림으로 수동 개입 유도

**파라미터 고정값**:
- `numOfRows`: 1000 (페이지당 최대)
- `timeout`: 500ms (API 응답 평균 300ms 기준)
- `maxRetries`: 3회 (페이지당)

**성능 특성**:
- 평균 응답 시간: ~300ms/request
- 1000건/페이지로 API 호출 최소화
- 중복 체크는 DB 쿼리 1회로 배치 처리
- 페이지 실패 시 전체 수집 중단하지 않음

---

## 주요 설계 결정

### 1. 정규화 (Normalization)

**API 필드 → Article 매핑**:
```kotlin
fun normalizeApiResponse(response: YonhapnewsArticle): Article {
    return Article(
        articleId = response.YNA_NO.toString(),  // YNA_NO as articleId
        originId = response.YNA_NO.toString(),   // same as articleId
        sourceId = "yonhapnews",
        writtenAt = parseYnaYmd(response.YNA_YMD),  // "yyyy-MM-dd HH:mm:ss" → Instant
        modifiedAt = parseYnaYmd(response.YNA_YMD), // initially same as writtenAt
        title = response.YNA_TTL,
        content = response.YNA_CN,
        sourceUrl = null  // API doesn't provide
    )
}

private fun parseYnaYmd(ynaYmd: String): Instant {
    // "2026-01-12 09:09:54" → Instant (assume KST timezone)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    val localDateTime = LocalDateTime.parse(ynaYmd, formatter)
    val zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.of("Asia/Seoul"))
    return zonedDateTime.toInstant()
}
```

**정규화 규칙**:
- `YNA_NO` → `articleId` and `originId` (String representation)
- `YNA_YMD` → `Instant` (parse as KST, convert to UTC)
- `YNA_WRTR_NM`, `CRT_DT` discarded (not needed)
- `title`, `content` must not be blank (validation)

### 2. 중복 제거 (Deduplication)

**Strategy**: Use `ArticleRepository.filterNonExisting()` from shared module

**Mechanism**:
```kotlin
// 1. Extract articleIds from normalized articles
val articleIds = articles.map { it.articleId }

// 2. Query which IDs don't exist in DB
val nonExistingIds = articleRepository.filterNonExisting(articleIds)

// 3. Filter articles to only non-existing ones
val newArticles = articles.filter { it.articleId in nonExistingIds }

// 4. Save only new articles
articleRepository.saveAll(newArticles)
```

**ArticleRepository.filterNonExisting()**:
```kotlin
// Input: Collection<String> (articleIds)
// Output: List<String> (articleIds that DON'T exist in DB)
fun filterNonExisting(articleIds: Collection<String>): List<String>
```

**Deduplication Logic**:
- `articleId` uniqueness (same as `originId` = YNA_NO)
- Batch query optimization (single DB query with IN clause)
- Only new articles are saved, existing ones skipped

**Modified Article Handling**:
- Current: Skip duplicates (no update)
- Future: Compare `modifiedAt` to detect and update modified articles

### 3. 에러 처리

**API 호출 실패 - 3단계 재시도 전략**:

1. **즉시 재시도 (동일 페이지)**:
   ```kotlin
   // TimeoutException: 500ms 초과
   // IOException: 네트워크 오류
   // 최대 3회 재시도 (exponential backoff: 2s, 4s, 8s)
   ```

2. **실패 페이지 추적 및 지연 재시도**:
   ```kotlin
   // 전체 페이지 순회 완료 후
   // 실패한 페이지만 모아서 재시도
   // 다른 페이지 수집에 영향 없음
   ```

3. **최종 실패 처리**:
   ```kotlin
   // 재시도 후에도 실패 시 CollectionException
   // 실패 페이지 번호 로그 기록
   // 모니터링 알림으로 수동 개입
   ```

**부분 실패 허용 정책**:
- **검증 실패**: 해당 Article만 스킵, 로그 기록, 다음 기사 처리
- **페이지 실패**: 실패한 페이지 추적, 나머지 페이지 계속 수집
- **DB 저장 실패**: 페이지 단위 트랜잭션 롤백, 재시도 큐에 추가

**타임아웃 및 재시도 설정**:
- **API 호출 timeout**: 500ms (평균 300ms + 여유 200ms)
- **재시도 간격**: exponential backoff
  - 1차 실패: 2초 대기
  - 2차 실패: 4초 대기
  - 3차 실패: 8초 대기
- **최대 재시도 횟수**: 3회/페이지

**에러 복구 흐름**:
```
API 호출
  ↓ (실패)
즉시 재시도 (3회, exponential backoff)
  ↓ (여전히 실패)
failedPages 추적, 다음 페이지 진행
  ↓ (전체 순회 완료)
실패한 페이지만 재시도
  ↓ (성공)
수집 완료
  ↓ (재시도 후에도 실패)
CollectionException throw → 모니터링 알림
```

### 4. 스케줄링 전략

**초기 수집**: `@EventListener(ApplicationReadyEvent::class)`
- 애플리케이션 시작 시 1회 실행
- KST 기준 오늘 날짜 전체 수집

**정기 수집**: `@Scheduled(cron = "0 */10 * * * *")`
- 10분 주기 실행
- 오늘 날짜 새 기사 발견 및 수집
- 중복 필터링으로 불필요한 DB 쓰기 방지

**Backfill**: REST API 수동 호출
- 과거 데이터 소급 수집
- startDate ~ today 범위 일별 수집
- 장기간 backfill은 비동기 처리 권장

### 5. 시간대 처리 (Timezone)

**KST 기준 사용**:
```kotlin
val koreaZone = ZoneId.of("Asia/Seoul")
val today = LocalDate.now(koreaZone)
```

**이유**:
- 연합뉴스 기사는 한국 시간 기준
- 사용자도 한국 시간 기준 검색 기대
- UTC 변환은 필요 시 searcher에서 처리

### 6. 페이지네이션 최적화

**numOfRows=1000 선택 이유**:
- API 호출 횟수 최소화 (비용/성능)
- 평균 응답 시간 300ms로 안정적
- 대부분의 날짜별 데이터가 1-2페이지로 커버

**빠른 순회 전략**:
```kotlin
// 첫 페이지에서 totalPages 계산
val totalPages = (response.totalCount + 999) / 1000

// 모든 페이지 빠르게 순회 (데이터 변경 최소화)
(1..totalPages).forEach { pageNo ->
    val response = fetchArticles(inqDt, pageNo)
    processAndSave(response.body)
}
```

**데이터 변동 대응**:
- 페이지네이션 도중 totalCount 변경 무시
- 첫 페이지 기준으로 고정하여 일관성 유지
- 새 데이터는 다음 수집 주기에서 처리

---

## 모델 정의

### Domain Model (shared module)

**Article** - Core business entity

**Source**: `com.vonkernel.lit.entity.Article`

```kotlin
data class Article(
    val articleId: String,
    val originId: String,
    val sourceId: String,
    val writtenAt: Instant,
    val modifiedAt: Instant,
    val title: String,
    val content: String,
    val sourceUrl: String? = null
)
```

**Field Descriptions**:
- `articleId`: Internal unique ID (YNA_NO as String)
- `originId`: Yonhapnews article ID (same as articleId)
- `sourceId`: Source identifier (always "yonhapnews")
- `writtenAt`: Article publication time (Instant, UTC)
- `modifiedAt`: Article modification time (Instant, UTC)
- `title`: Article title
- `content`: Article body
- `sourceUrl`: Source URL (optional, API doesn't provide)

---

### Adapter Layer Models (collector-specific)

**SafetyDataApiResponse** - API response wrapper

```kotlin
data class SafetyDataApiResponse(
    val header: Header,
    val numOfRows: Int,
    val pageNo: Int,
    val totalCount: Int,
    val body: List<YonhapnewsArticle>
)

data class Header(
    val resultCode: String,  // "00" = success
    val resultMsg: String,   // "NORMAL SERVICE"
    val errorMsg: String?
)
```

**YonhapnewsArticle** - External API article structure

```kotlin
data class YonhapnewsArticle(
    val YNA_NO: Int,           // Yonhapnews article ID
    val YNA_TTL: String,       // Article title
    val YNA_CN: String,        // Article content
    val YNA_YMD: String,       // Published at "yyyy-MM-dd HH:mm:ss"
    val YNA_WRTR_NM: String,   // Writer (연합뉴스)
    val CRT_DT: String         // System registered at "yyyy/MM/dd HH:mm:ss.SSSSSSSSS"
)
```

**Field Mapping** (API → Domain):

| API Field | Article Field | Transformation |
|-----------|---------------|----------------|
| YNA_NO | articleId | `toString()` |
| YNA_NO | originId | `toString()` |
| YNA_TTL | title | as-is |
| YNA_CN | content | as-is |
| YNA_YMD | writtenAt | `parseYnaYmd()` → Instant |
| YNA_YMD | modifiedAt | `parseYnaYmd()` → Instant |
| - | sourceId | "yonhapnews" (constant) |
| - | sourceUrl | null (not provided) |

---

## 품질 기준

### 코드 품질
- 순수 함수는 100% 단위 테스트 커버리지
- Integration 테스트로 Adapter 동작 검증
- Linting: ktlint, detekt 통과

### 성능
- **HTTP Client**: WebClient (reactive, non-blocking)
- **API 호출 타임아웃**: 500ms (평균 300ms 기준)
- **페이지당 데이터**: 1000건 (numOfRows 고정값)
- **DB 저장**: 페이지 단위 배치 처리
- **메모리 사용**: 페이지 단위 스트림 처리 (최대 1000건/메모리)
- **중복 체크**: IN 절 배치 쿼리 (1000개 articleIds)
- **예상 처리량**:
  - 평균 300ms/페이지 * 재시도 포함 ≈ 400ms/페이지
  - 1일 평균 800건 → 1페이지 → ~400ms
  - 1일 최대 10,000건 → 10페이지 → ~4초
- **Reactive Benefits**: Non-blocking I/O, efficient resource utilization

### 신뢰성
- API 장애시 재시도 로직 (최대 3회)
- DB 연결 실패시 connection pool 재연결
- 예외 로깅 및 모니터링 알림

---

---

**Tech Stack**: Kotlin 2.21 | Spring Boot 4.0 | Coroutines | PostgreSQL 18 | WebClient

**Module Dependencies**:
- `shared` (domain models + repositories)
- `spring-boot-starter-webclient` (WebClient without WebFlux server)
