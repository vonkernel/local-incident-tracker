# Analyzer Module Architecture

## Overview

The analyzer module consumes Article CDC events from Kafka, performs a two-phase LLM-based analysis pipeline using the ai-core module, and persists aggregated results via the persistence module. The stored `AnalysisResult` acts as a transactional outbox entry, triggering downstream CDC events for the indexer.

```
Kafka (article-events)
    ↓
[Event Listener] ─ Inbound Adapter
    ↓
[ArticleAnalysisService] ─ Application Layer
    │
    ├─ Phase 1: Article Refinement (sequential)
    │   └─ RefineArticleAnalyzer → RefinedArticle (title, content, summary, writtenAt)
    │
    └─ Phase 2: Parallel Analysis (5 concurrent, using refined article)
        ├─ Incident type classification (title + content) → Set<IncidentType>
        ├─ Urgency assessment (title + content) → Urgency
        ├─ Keyword extraction (summary) → List<Keyword>
        ├─ Topic extraction (summary) → Topic
        └─ LocationAnalysisService (title + content)
            ├─ Location extraction (LLM) → List<ExtractedLocation>
            ├─ Location validation (LLM) → List<ExtractedLocation> (filtered/normalized/refined)
            └─ Geocoding (External API) → List<Location>
    ↓
[AnalysisResult assembly]
    ↓
[AnalysisResultRepository.save()] ─ Outbound Adapter
    ↓
PostgreSQL (analysis result + outbox record, single transaction)
    ↓
Debezium CDC → Kafka (analysis-events)
```

---

## Hexagonal Architecture

### Inbound Adapters (Driving Side)

| Adapter | Responsibility | Trigger |
|---------|---------------|---------|
| `ArticleEventListener` | Kafka consumer that receives Debezium CDC envelopes, deserializes them into `Article` domain objects, and delegates to the application service | `article-events` Kafka topic |

The listener is responsible for:
- Deserializing Debezium CDC envelope (`DebeziumEnvelope`) via Jackson `ObjectMapper`
- Filtering to only `op: "c"` (create) events — articles are never updated, only inserted
- Converting `ArticlePayload` to `Article` domain model via `toArticle()` extension function
- Bridging Kafka's blocking consumer to suspend functions via `runBlocking`
- Delegating to `ArticleAnalysisService` for actual analysis orchestration
- Error handling at the message consumption boundary (e.g., deserialization failures, poison messages)

**CDC Event Model** (`DebeziumArticleEvent.kt`):
- `DebeziumEnvelope`: Contains `before`, `after` (ArticlePayload), `op` (c/u/d/r), `source`
- `ArticlePayload`: Maps snake_case CDC columns via `@JsonProperty` to Kotlin fields
- `toArticle()`: Extension function converting payload timestamps (ISO strings) to `Instant`

### Domain Core

| Component | Responsibility |
|-----------|---------------|
| `ArticleAnalysisService` | Top-level orchestration: receives an `Article`, runs 2-phase pipeline (refine → parallel analysis), assembles the final `AnalysisResult`, and delegates persistence. Wraps failures in `ArticleAnalysisException` with `articleId` for traceability |
| `RefineArticleAnalyzer` | Phase 1: Refines raw article by removing noise (journalist info, newlines, "끝" markers), restructuring content as individual fact sentences, and generating a 3-sentence summary. Returns `RefinedArticle` |
| `IncidentTypeAnalyzer` | Classifies the refined article into one or more incident types (e.g., "forest_fire", "typhoon") using LLM prompt execution. Returns `Set<IncidentType>` |
| `UrgencyAnalyzer` | Assesses the urgency/severity level of the incident from the refined article using LLM. Returns `Urgency` with name and numeric level |
| `KeywordAnalyzer` | Extracts up to 3 prioritized keywords from the refined article summary using LLM. Returns `List<Keyword>` with keyword text and priority ranking |
| `TopicAnalyzer` | Extracts a single topic sentence from the refined article summary using LLM. Returns `Topic` with a complete sentence (not keyword combination) |
| `LocationAnalysisService` | Orchestrates the 3-step location pipeline: extraction → validation → geocoding. Delegates to `LocationAnalyzer`, `LocationValidator`, and `GeocodingPort`. Returns `List<Location>` |
| `LocationAnalyzer` | Extracts geographic location mentions from the refined article text using LLM. Each extracted location is classified as `ADDRESS` (행정구역 단위만으로 구성된 주소 — 시설명·건물명·도로명 등 비행정 요소 제외), `LANDMARK` (건물명·시설명 등 고유명사 장소 — 행정구역 접두어 유지, 수식어 제거), or `UNRESOLVABLE`. Returns `List<ExtractedLocation>` |
| `LocationValidator` | Validates and refines extracted locations via LLM before geocoding: (1) filters out locations not directly related to the incident (reporter attribution, background mentions, institution names), (2) normalizes abbreviated region names to full official names (e.g., 충남→충청남도), (3) cleans up modifier suffixes (인근, 부근, 일대 etc.). Returns `List<ExtractedLocation>` |

### Outbound Ports (Driven Side)

| Port | Implementation | Purpose |
|------|---------------|---------|
| `AnalysisResultRepository` | persistence module adapter | Atomically persists AnalysisResult + outbox record in a single transaction |
| `PromptOrchestrator` | ai-core module service | Executes LLM prompts with template variable substitution and JSON response deserialization |
| `GeocodingPort` | `KakaoGeocodingAdapter` | Converts place name strings to `Location` domain objects with coordinates and addresses via Kakao Local API. Includes DB-level address caching via `JpaAddressRepository.findByAddressName()` |

---

## Component Hierarchy

```
┌─────────────────────────────────────────────────────────┐
│ ADAPTER LAYER (Inbound)                                  │
│                                                          │
│  ArticleEventListener                                    │
│  - @KafkaListener consuming article-events topic         │
│  - Debezium CDC envelope deserialization                 │
│  - Article domain model construction                     │
│  - Delegates to ArticleAnalysisService                   │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ DOMAIN SERVICE LAYER                                     │
│                                                          │
│  ArticleAnalysisService (@Service)                       │
│  - Entry point for analysis orchestration                │
│  - Phase 1: RefineArticleAnalyzer (sequential)           │
│  - Phase 2: 5 parallel analyzers using refined article   │
│  - Awaits all results and assembles AnalysisResult       │
│  - Calls AnalysisResultRepository.save()                 │
│  - Wraps failures in ArticleAnalysisException            │
│                                                          │
│  LocationAnalysisService (@Service)                      │
│  - Orchestrates location extraction → validation →       │
│    geocoding pipeline                                    │
│  - Delegates to LocationAnalyzer, LocationValidator,     │
│    and GeocodingPort                                     │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ DOMAIN LAYER                                             │
│                                                          │
│  Individual Analyzers (interface + @Service impl)        │
│  ├─ RefineArticleAnalyzer (Phase 1 - sequential)         │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "refine-article"                       │
│  │      input: article title + content                   │
│  │      output: RefinedArticle                           │
│  │                                                       │
│  ├─ IncidentTypeAnalyzer (Phase 2 - parallel)            │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "incident-type-classification"         │
│  │      input: refined title + content                   │
│  │      output: Set<IncidentType>                        │
│  │                                                       │
│  ├─ UrgencyAnalyzer (Phase 2 - parallel)                 │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "urgency-assessment"                   │
│  │      input: refined title + content                   │
│  │      output: Urgency                                  │
│  │                                                       │
│  ├─ KeywordAnalyzer (Phase 2 - parallel)                 │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "keyword-extraction"                   │
│  │      input: refined summary                           │
│  │      output: List<Keyword> (max 3)                    │
│  │                                                       │
│  ├─ TopicAnalyzer (Phase 2 - parallel)                   │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "topic-extraction"                     │
│  │      input: refined summary                           │
│  │      output: Topic                                    │
│  │                                                       │
│  ├─ LocationAnalyzer (Phase 2 - parallel)                │
│  │   └─ Uses PromptOrchestrator.execute()                │
│  │      promptId: "location-extraction"                  │
│  │      input: refined title + content                   │
│  │      output: List<ExtractedLocation>                  │
│  │        ExtractedLocation: { name, type }              │
│  │        type: ADDRESS | LANDMARK | UNRESOLVABLE        │
│  │                                                       │
│  └─ LocationValidator (post-extraction validation)       │
│      └─ Uses PromptOrchestrator.execute()                │
│         promptId: "location-validation"                  │
│         input: title + content + extracted locations     │
│         output: List<ExtractedLocation> (filtered,       │
│                 normalized, refined)                      │
│                                                          │
│  Port Interfaces                                         │
│  └─ GeocodingPort                                        │
│      ├─ geocodeByAddress(query) → Location?              │
│      └─ geocodeByKeyword(query) → Location?              │
└─────────────────────────┬───────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ ADAPTER LAYER (Outbound)                                 │
│                                                          │
│  PromptOrchestrator (ai-core module)                     │
│  - LLM prompt execution with template variables          │
│  - JSON response deserialization to typed outputs         │
│                                                          │
│  KakaoGeocodingAdapter (@Component)                      │
│  - Spring WebClient-based REST API calls to Kakao        │
│  - DB cache lookup via JpaAddressRepository              │
│  - Response mapping to Location domain model             │
│                                                          │
│  AnalysisResultRepository (persistence module)           │
│  - Transactional save of AnalysisResult + outbox         │
│  - IncidentType/Urgency resolution from DB               │
│  - Address entity creation/reuse                         │
└─────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
com.vonkernel.lit.analyzer
├── adapter
│   ├── inbound
│   │   ├── ArticleEventListener.kt               # Kafka consumer for article CDC events
│   │   └── model
│   │       └── DebeziumArticleEvent.kt            # CDC envelope, payload, Article conversion
│   └── outbound
│       ├── KakaoGeocodingAdapter.kt               # GeocodingPort implementation (Kakao REST API)
│       ├── config
│       │   └── KakaoWebClientConfig.kt            # WebClient bean with Kakao API auth
│       └── model
│           ├── KakaoAddressResponse.kt            # 주소 검색 API 응답 모델
│           ├── KakaoKeywordResponse.kt            # 키워드 검색 API 응답 모델
│           └── KakaoMeta.kt                       # 공통 meta 객체
│
├── domain
│   ├── service
│   │   ├── ArticleAnalysisService.kt              # Analysis orchestration service
│   │   └── LocationAnalysisService.kt             # Location extraction → validation → geocoding pipeline
│   ├── analyzer
│   │   ├── RefineArticleAnalyzer.kt               # Phase 1: Article refinement (LLM)
│   │   ├── IncidentTypeAnalyzer.kt                # Phase 2: Incident type classification (LLM)
│   │   ├── UrgencyAnalyzer.kt                     # Phase 2: Urgency assessment (LLM)
│   │   ├── KeywordAnalyzer.kt                     # Phase 2: Keyword extraction (LLM, summary-based)
│   │   ├── TopicAnalyzer.kt                       # Phase 2: Topic extraction (LLM, summary-based)
│   │   ├── LocationAnalyzer.kt                    # Location extraction from article text (LLM)
│   │   └── LocationValidator.kt                   # Location validation/normalization/refinement (LLM)
│   ├── exception
│   │   └── ArticleAnalysisException.kt            # Custom exception with articleId for traceability
│   │
│   └── port
│       └── GeocodingPort.kt                       # Outbound port for geocoding external API
│
└── AnalyzerApplication.kt                         # Spring Boot entry point
```

---

## Data Flow (Detailed)

### Step 1: Event Consumption

`ArticleEventListener` receives a Debezium CDC message from the `article-events` Kafka topic. The CDC envelope contains the full row state of the inserted `Article` record from PostgreSQL.

The listener:
1. Deserializes the Debezium envelope payload
2. Constructs an `Article` domain object (from shared module)
3. Invokes `ArticleAnalysisService.analyze(article)`

### Step 2: Two-Phase LLM Analysis

`ArticleAnalysisService` implements a two-phase analysis pipeline:

**Phase 1 (Sequential)**: Refine the raw article to remove noise and produce a clean, structured version with a summary.

```kotlin
val refinedArticle = withRetry("refineArticleAnalyzer") {
    refineArticleAnalyzer.analyze(article)
}
```

**Phase 2 (Parallel)**: Five concurrent analysis tasks using the refined article.

```kotlin
coroutineScope {
    val incidentTypes = async { incidentTypeAnalyzer.analyze(refinedArticle.title, refinedArticle.content) }
    val urgency       = async { urgencyAnalyzer.analyze(refinedArticle.title, refinedArticle.content) }
    val keywords      = async { keywordAnalyzer.analyze(refinedArticle.summary) }
    val topic         = async { topicAnalyzer.analyze(refinedArticle.summary) }
    val locations     = async { locationAnalysisService.analyze(articleId, refinedArticle.title, refinedArticle.content) }

    AnalysisResult(
        articleId     = article.articleId,
        refinedArticle = refinedArticle,
        incidentTypes = incidentTypes.await(),
        urgency       = urgency.await(),
        keywords      = keywords.await(),
        topic         = topic.await(),
        locations     = locations.await()
    )
}
```

Any exception during analysis or persistence is caught, logged at ERROR level, and re-thrown as `ArticleAnalysisException(articleId, message, cause)` for upstream handling with article identity preserved.

Each analyzer internally:
1. Constructs a `PromptRequest` with the appropriate `promptId`, input data, and output type
2. Calls `PromptOrchestrator.execute()` which loads the prompt template, substitutes `{{variables}}`, sends to the LLM, and deserializes the JSON response
3. Maps the deserialized response to the corresponding shared domain model

### Step 3: Location Resolution (LocationAnalysisService)

`LocationAnalysisService` orchestrates a three-step pipeline:

1. **Extraction (LLM)**: `LocationAnalyzer` extracts location mentions from article text with type classification.
   Each extracted location includes:
   - `name`: the raw text as it appears in the article (e.g., "하남시 선동", "코엑스", "전국")
   - `type`: one of `ADDRESS`, `LANDMARK`, or `UNRESOLVABLE`

2. **Validation (LLM)**: `LocationValidator` refines extracted locations before geocoding:
   - **Filtering**: Removes locations not directly related to the incident (reporter attribution, background mentions, institution-embedded place names)
   - **Normalization**: Converts abbreviated region names to official full names (e.g., 충남→충청남도, 경북→경상북도, 강원→강원특별자치도)
   - **Refinement**: Re-applies expression cleanup rules — strips non-administrative elements from ADDRESS types and removes modifier suffixes (인근, 부근, 일대, 근처, 주변) from LANDMARK types

3. **Geocoding**: Processes each validated location according to its type:

   **`ADDRESS`** (e.g., "하남시 선동", "전북"):
   ```
   GeocodingPort.geocodeByAddress("하남시 선동")
       → Kakao 주소 검색 API 호출
       → 체계적 주소 + 좌표 획득
       → 결과 없으면 unresolvedLocation 반환
   ```

   **`LANDMARK`** (e.g., "코엑스", "판교JC"):
   ```
   GeocodingPort.geocodeByKeyword("코엑스")
       → Kakao 키워드 장소 검색 API 호출 → address_name 획득
       → 획득한 address_name으로 주소 검색 API 재호출
       → 체계적 주소 + 좌표 획득
       → fallback: geocodeByAddress() 호출
   ```

   **`UNRESOLVABLE`** (e.g., "전국", "수도권"):
   ```
   API 호출 없음
       → Location(
             coordinate = null,
             address = Address(
                 regionType = UNKNOWN,
                 code = "UNKNOWN",
                 addressName = "전국",  ← LLM 추출 원본 그대로
                 depth1Name = null, depth2Name = null, depth3Name = null
             )
         )
   ```

**Important**: `Address.addressName` always stores the LLM-extracted original expression (e.g., "하남시 선동", "판교JC", "코엑스", "전국"), not the Kakao API response address. The structured address fields (`depth1Name`, `depth2Name`, `depth3Name`, `code`) and `Coordinate` are populated from the Kakao API response.

The geocoding calls can be parallelized via coroutines since each location is independent.

### Step 4: Result Assembly and Persistence

After all analyzers complete:

1. `ArticleAnalysisService` assembles the final `AnalysisResult`:
   ```kotlin
   AnalysisResult(
       articleId      = article.articleId,
       refinedArticle = RefinedArticle,      // from RefineArticleAnalyzer (Phase 1)
       incidentTypes  = Set<IncidentType>,   // from IncidentTypeAnalyzer (Phase 2)
       urgency        = Urgency,             // from UrgencyAnalyzer (Phase 2)
       keywords       = List<Keyword>,       // from KeywordAnalyzer (Phase 2)
       topic          = Topic,               // from TopicAnalyzer (Phase 2)
       locations      = List<Location>       // from LocationAnalyzer (Phase 2)
   )
   ```

2. Calls `AnalysisResultRepository.save(analysisResult)`, which within the persistence module:
   - Resolves `IncidentType` codes against the database reference table
   - Resolves `Urgency` name against the database reference table
   - Creates or reuses `Address` entities
   - Inserts `AnalysisResultEntity` with all relationships
   - Inserts `AnalysisResultOutboxEntity` in the same transaction (outbox pattern)
   - Commits atomically — both succeed or both rollback

3. Debezium detects the outbox table INSERT via CDC and publishes an `analysis-events` message to Kafka for the indexer to consume.

---

## External Dependencies

### ai-core Module (LLM Integration)

The analyzer depends on `PromptOrchestrator` as its primary interface to LLM capabilities.

**Key interface**:
```kotlin
// Single prompt execution
suspend fun <I, O> execute(
    promptId: String,
    input: I,
    inputType: Class<I>,
    outputType: Class<O>
): PromptExecutionResult<O>

// Parallel prompt execution (alternative to manual coroutine management)
suspend fun <I, O> executeParallel(
    requests: List<PromptRequest<I, O>>
): List<PromptExecutionResult<O>>
```

**Prompt IDs used by analyzer**:
| Prompt ID | Analyzer | Input | Output |
|-----------|----------|-------|--------|
| `refine-article` | RefineArticleAnalyzer | Article title + content | Refined title, content (fact sentences), summary (3 sentences) |
| `incident-type-classification` | IncidentTypeAnalyzer | Refined title + content | Set of incident type codes and names |
| `urgency-assessment` | UrgencyAnalyzer | Refined title + content | Urgency name and numeric level |
| `keyword-extraction` | KeywordAnalyzer | Refined summary | List of keywords with priority (max 3) |
| `topic-extraction` | TopicAnalyzer | Refined summary | Topic sentence (complete sentence form) |
| `location-extraction` | LocationAnalyzer | Refined title + content | List of `{ name, type }` where type is `ADDRESS` / `LANDMARK` / `UNRESOLVABLE` |
| `location-validation` | LocationValidator | Title + content + extracted locations list | List of `{ name, type }` — filtered, normalized, and refined |

**Error handling**: Each analyzer must handle `LlmExecutionException`, `LlmTimeoutException`, and `LlmRateLimitException` from ai-core. Retry logic via shared `RetryUtil` is applied for transient failures.

### persistence Module (Database)

The analyzer depends on `AnalysisResultRepository` defined in the shared module and implemented by the persistence module.

**Key interface**:
```kotlin
interface AnalysisResultRepository {
    fun save(analysisResult: AnalysisResult): AnalysisResult
    fun existsByArticleId(articleId: String): Boolean
    fun deleteByArticleId(articleId: String)
}
```

**Transactional behavior** (handled inside persistence module):
- Single `@Transactional` boundary wrapping both AnalysisResult and outbox INSERT
- `IncidentType` and `Urgency` are resolved from database reference tables (not created)
- `Address` entities are created or reused based on existing records
- Keywords are persisted with ordering preserved

The analyzer does **not** manage transactions directly — it passes a fully constructed `AnalysisResult` domain object and the persistence layer handles all ORM mapping and transactional guarantees.

### Geocoding API (Kakao Local API)

The geocoding port provides two methods corresponding to the two Kakao Local API endpoints.
See `GEOCODING_API.md` for full API specification, response examples, and domain model mapping.

```kotlin
interface GeocodingPort {
    suspend fun geocodeByAddress(query: String): Location?
    suspend fun geocodeByKeyword(query: String): Location?
}
```

| Method | Kakao API Endpoint | Use Case |
|--------|-------------------|----------|
| `geocodeByAddress` | `GET /v2/local/search/address.json` | ADDRESS type locations (e.g., "하남시 선동", "전북") |
| `geocodeByKeyword` | `GET /v2/local/search/keyword.json` | LANDMARK type locations (e.g., "코엑스", "판교JC") |

**Resolution strategy by location type**:
- **ADDRESS**: `geocodeByAddress()` → fallback `unresolvedLocation()`
- **LANDMARK**: `geocodeByKeyword()` → extract `address_name` → `geocodeByAddress()` → fallback direct coordinate use
- **UNRESOLVABLE**: No API call. Store `addressName` only with `coordinate = null`

The adapter implementation (`KakaoGeocodingAdapter`) uses Spring WebClient (configured via `KakaoWebClientConfig` with `KakaoAK` authorization header) to call the Kakao REST API.

**DB Cache Layer**: Before making API calls, the adapter checks `JpaAddressRepository.findByAddressName(query)` for a previously resolved address. Cache hits skip the API call entirely and return the stored `Location` via `LocationMapper.toDomainModel()`.

**Parallel Geocoding**: `LocationAnalysisService` resolves all validated locations concurrently via `coroutineScope { extractedLocations.map { async { resolveLocation(it) } }.awaitAll() }`. Each location is resolved independently based on its `LocationType`.

`Address.addressName` always stores the LLM-extracted original expression, not the Kakao API response address.

---

## Design Constraints

### No Synchronous Inter-Service Calls
The analyzer communicates with other services exclusively through events. It consumes Article events from Kafka and produces AnalysisResult records that are published via CDC. There are no direct HTTP calls to collector, indexer, or searcher.

### Idempotency
The same Article CDC event may be delivered more than once (at-least-once delivery). The analyzer handles duplicate events by checking for existing analysis results via `AnalysisResultRepository.existsByArticleId()` before processing. If a prior result exists, it is deleted via `deleteByArticleId()` and the article is re-analyzed from scratch. This "delete-then-reinsert" strategy ensures idempotent processing while allowing analysis improvements on redelivery.

### Transactional Atomicity
All analysis results for a single article must be committed atomically. The persistence module ensures that the AnalysisResult entity and the outbox record are written in a single transaction. If any part fails, the entire operation rolls back.

### Pure Functional Domain Logic
Each individual analyzer's internal logic follows functional principles:
- **Immutable inputs/outputs**: All data classes from the shared module are immutable
- **No side effects**: Analyzers do not mutate external state; they return new values
- **Deterministic composition**: Given the same LLM responses, the same AnalysisResult is produced

Side effects (LLM calls, geocoding calls, DB writes) are confined to the adapter layer boundaries.

### Coroutine-Based Concurrency
The analysis pipeline uses a two-phase approach. Phase 1 (article refinement) runs sequentially since its output (refined title, content, summary) is required by all Phase 2 analyzers. Phase 2 runs five analysis tasks (incident type, urgency, keywords, topic, location) concurrently via Kotlin coroutines (`async`/`await`). Within `LocationAnalyzer`, geocoding calls for multiple place names can also run in parallel. This minimizes total analysis latency, which is dominated by LLM response times.

### Error Handling Strategy
- **LLM failures**: Retry with exponential backoff via `RetryUtil` (max 2 retries). After max retries, the exception propagates up.
- **Geocoding failures**: Retry transient errors. For permanent failures (all API fallbacks exhausted), return a `Location` with `coordinate = null` and `Address(addressName = LLM extracted text, code = "UNKNOWN", regionType = UNKNOWN)`. The `UNRESOLVABLE` type locations skip API calls entirely and always produce this form.
- **Persistence failures**: Propagate to the event listener.
- **Error wrapping**: `ArticleAnalysisService.analyze()` catches all exceptions, logs at ERROR level with `articleId`, and wraps them in `ArticleAnalysisException(articleId, message, cause)`. The `ArticleEventListener` extracts `articleId` from this exception for structured error logging at the Kafka consumption boundary.
- **Retry logging**: All `withRetry` calls include `articleId` in warn-level retry logs for traceability.
