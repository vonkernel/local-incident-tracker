# Collector Service

연합뉴스 안전행정부 재난 API로부터 사건사고 데이터를 수집하여 정규화하고 PostgreSQL에 저장하는 데이터 수집 파이프라인입니다.

---

## 목차

- [역할 및 책임](#역할-및-책임)
- [데이터 흐름](#데이터-흐름)
- [Safety Data API 특성](#safety-data-api-특성)
- [아키텍처](#아키텍처)
- [주요 컴포넌트](#주요-컴포넌트)
- [수집 전략](#수집-전략)
- [환경 설정](#환경-설정)
- [애플리케이션 실행](#애플리케이션-실행)
- [테스트](#테스트)

---

## 역할 및 책임

collector는 연합뉴스 재난 API로부터 원본 데이터를 수집하여 내부 표준 형식으로 정규화하고, 중복을 제거한 후 PostgreSQL에 저장하는 첫 번째 파이프라인 단계입니다.

### 핵심 책임
- 외부 API(연합뉴스 안전행정부 재난 API)로부터 원본 데이터 수집
- 외부 데이터 형식을 내부 표준 형식(`Article`)으로 정규화
- 데이터 유효성 검증 및 중복 제거
- PostgreSQL에 `Article` 저장 (CDC를 통해 analyzer로 자동 전달)

---

## 데이터 흐름

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

---

## Safety Data API 특성

### API 엔드포인트
```
https://www.safetydata.go.kr/V2/api/DSSP-IF-00051
```

### 주요 파라미터
| 파라미터 | 설명 | 비고 |
|---------|------|------|
| `inqDt` | 조회 날짜 (YYYYMMDD) | 시스템 등록일(CRT_DT) 기준 |
| `pageNo` | 페이지 번호 | 1부터 시작 |
| `numOfRows` | 페이지당 결과 수 | 최대 1000 |
| `serviceKey` | API 인증 키 | 필수 |
| `returnType` | 응답 형식 | json 고정 |

### API 응답 필드
| 필드 | 설명 | 매핑 |
|-----|------|------|
| `YNA_NO` | 연합뉴스 기사 고유 ID | `originId` |
| `YNA_YMD` | 기사 작성/발행 일시 | `writtenAt` |
| `CRT_DT` | 시스템 등록 일시 | `modifiedAt` |
| `YNA_TTL` | 기사 제목 | `title` |
| `YNA_CN` | 기사 본문 | `content` |
| `YNA_WRTR_NM` | 작성자 | 사용 안함 |

### API 동작 특성

**중요한 관찰 사항**:
1. **`inqDt` 파라미터의 불명확한 필터링**
   - 서로 다른 `inqDt`로 조회해도 동일한 기사가 반환될 수 있음
   - `inqDt=20260112` 조회 결과에 1월 12일~15일 작성 기사가 모두 포함될 수 있음

2. **정렬 순서 불명확**
   - `CRT_DT`, `YNA_YMD`, `YNA_NO` 모두 일관된 정렬 순서를 보이지 않음

**대응 전략**:
- `YNA_NO` 기반 중복 제거 필수 구현
- 정기적 재수집으로 늦게 등록된 기사 보완
- 빠른 페이지네이션으로 데이터 변경 최소화

---

## 아키텍처

collector는 **Hexagonal Architecture (Ports & Adapters)** 패턴을 따르며, 프로젝트 전체의 **OOP + FP Hybrid** 전략을 사용합니다.

### 계층 구조

```
┌───────────────────────────────────────────┐
│ Adapter Layer (OOP - Spring Beans)        │
│ - SafetyDataApiAdapter (HTTP Client)      │
│ - ArticleRepositoryImpl (PostgreSQL)      │
│ - CollectorScheduler (periodic)           │
│ - CollectorController (REST API)          │
│ - External API Models:                    │
│   └─ SafetyDataApiResponse                │
│   └─ YonhapnewsArticle                    │
│   └─ YonhapnewsArticleMapper: normalize   │
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
│ - ArticleCollectionService: orchestration│
│ - ArticleValidator: validation           │
└──────────────┬───────────────────────────┘
               ↓
┌──────────────────────────────────────────┐
│ Domain Models (Immutable)                │
│ - Article (shared module)                │
│ - ArticlePage (collector-specific)       │
└──────────────────────────────────────────┘
```

**Layer Responsibilities**:
- **Adapter Layer**: 외부 시스템 연동, 프레임워크 종속 코드, API 응답 모델
- **Port Interfaces**: 의존성 계약 (shared 또는 collector에서 정의)
- **Domain Core**: 순수 비즈니스 로직, 사이드 이펙트 없음
- **Domain Models**: 핵심 비즈니스 엔티티 (shared에서 정의)

---

## 주요 컴포넌트

### 1. Domain Core (순수 함수 및 서비스)

#### `ArticleCollectionService` (인터페이스)
```kotlin
interface ArticleCollectionService {
    suspend fun collectArticlesForDate(date: LocalDate, pageSize: Int)
}
```

#### `ArticleCollectionServiceImpl` (구현)
**책임**: 특정 날짜의 모든 기사를 수집하는 핵심 오케스트레이션 로직

**주요 메서드**:
- `collectArticlesForDate()`: 날짜별 전체 페이지 수집 워크플로우
- `processPages()`: 페이지 범위 처리 및 실패 추적
- `retryBatchFailures()`: 실패한 페이지 일괄 재시도
- `fetchPageWithRetry()`: 지수 백오프 재시도 (최대 3회)
- `saveArticles()`: 검증 + 중복 제거 + DB 저장

**특징**:
- 페이지별 실패 추적 및 선택적 재시도
- 지수 백오프 전략으로 안정적 재시도
- 검증 실패/중복 기사 자동 필터링

#### `ArticleValidator`
**책임**: Article 비즈니스 규칙 검증

```kotlin
fun Article.validate(): Result<Article>
```

**검증 규칙**:
- `title` 비어있지 않음
- `content` 비어있지 않음
- `originId` 비어있지 않음
- `sourceId` 비어있지 않음

### 2. Port Interfaces

#### `NewsApiPort`
**정의**: collector 모듈

```kotlin
interface NewsApiPort {
    suspend fun fetchArticles(
        inqDt: String,
        pageNo: Int,
        numOfRows: Int
    ): ArticlePage
}
```

#### `ArticleRepository`
**정의**: shared 모듈

```kotlin
interface ArticleRepository {
    fun save(article: Article): Article
    fun saveAll(articles: Collection<Article>): List<Article>
    fun filterNonExisting(articleIds: Collection<String>): List<String>
}
```

### 3. Adapter Layer

#### `SafetyDataApiAdapter`
**책임**: Safety Data API HTTP 클라이언트

**구현 기술**:
- Spring WebClient (비동기 논블로킹)
- Kotlin Coroutines (`awaitSingle()`)

**설정**:
```yaml
safety-data:
  api:
    url: https://www.safetydata.go.kr
    key: ${SAFETY_DATA_API_KEY:}
```

#### `YonhapnewsArticleMapper`
**책임**: 외부 API 응답을 내부 `Article` 모델로 정규화

```kotlin
fun YonhapnewsArticle.toArticle(): Article
```

**매핑 규칙**:

| API 필드 | Article 필드 | 변환 |
|---------|-------------|------|
| `YNA_NO` | `articleId` | `"YYYY-MM-DD-{articleNo}"` 형식 |
| `YNA_NO` | `originId` | `toString()` |
| - | `sourceId` | `"yonhapnews"` (상수) |
| `YNA_YMD` | `writtenAt` | KST → UTC Instant |
| `CRT_DT` | `modifiedAt` | KST → UTC Instant |
| `YNA_TTL` | `title` | `trim()` |
| `YNA_CN` | `content` | `trim()` |
| - | `sourceUrl` | `null` (API 미제공) |

#### `CollectorScheduler`
**책임**: 정기 수집 스케줄링

**동작**:
```kotlin
@Scheduled(fixedRate = 600000) // 10분마다
fun collectTodayArticles()
```

- KST 기준 오늘 날짜 수집
- 실패 시 로그 기록 후 다음 주기 대기

#### `CollectorController`
**책임**: 수동 Backfill REST API 제공

**엔드포인트**:
```http
POST /api/collector/backfill
Content-Type: application/json

{
  "startDate": "2026-01-10"
}
```

**응답**: 204 No Content (성공 시)

---

## 수집 전략

collector는 세 가지 수집 시나리오를 지원합니다.

### 1. 초기 수집 (Application Startup)

**트리거**: Spring Boot 애플리케이션 시작 시 자동 실행 (현재는 스케줄러만 동작)

**동작**: 애플리케이션 시작 후 10분 이내 첫 스케줄 실행

**목적**: 시스템 재시작 시점의 오늘 날짜 기사 수집

### 2. 정기 수집 (Scheduled Collection)

**트리거**: `@Scheduled(fixedRate = 600000)` - 10분 주기

**동작**:
```kotlin
@Scheduled(fixedRate = 600000)
fun collectTodayArticles() {
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    articleCollectionService.collectArticlesForDate(today, 1000)
}
```

**목적**:
- 10분마다 오늘 날짜 새로운 기사 발견 및 수집
- 늦게 등록되는 기사 대응

**중복 처리**: `ArticleRepository.filterNonExisting()`으로 자동 필터링

### 3. Backfill 수집 (Manual API Request)

**트리거**: HTTP REST API 호출

**API**:
```http
POST /api/collector/backfill
Content-Type: application/json

{
  "startDate": "2026-01-10"
}
```

**목적**:
- 특정 날짜의 과거 데이터 소급 수집
- 시스템 장애 복구 시 누락 데이터 보완

**종료 조건**: 해당 날짜의 모든 페이지 수집 완료 시

### 공통 수집 로직

#### Phase 1: 첫 페이지 조회 및 메타데이터 확보
```kotlin
val firstPage = fetchPageWithRetry(apiDate, 1, pageSize)
val totalPages = calculateTotalPages(firstPage.totalCount, pageSize)
saveArticles(firstPage.articles)
```

#### Phase 2: 나머지 페이지 병렬 수집
```kotlin
val failedPages = processPages(apiDate, 2..totalPages, pageSize)
```

- 각 페이지 독립적으로 처리
- 실패한 페이지 번호만 추적

#### Phase 3: 실패 페이지 일괄 재시도
```kotlin
if (failedPages.isNotEmpty()) {
    retryBatchFailures(apiDate, failedPages, pageSize)
}
```

#### Phase 4: 검증 및 저장
```kotlin
private suspend fun saveArticles(rawArticles: List<Article>) {
    rawArticles
        .mapNotNull { it.validate().getOrNull() }  // 검증
        .let { validArticles ->
            val existingIds = articleRepository.filterNonExisting(
                validArticles.map { it.articleId }
            ).toSet()
            validArticles.filter { it.articleId in existingIds }  // 중복 제거
        }
        .takeIf { it.isNotEmpty() }
        ?.let { articleRepository.saveAll(it) }  // 저장
}
```

### 재시도 전략

**Micro-Retry (페이지별)**:
- 최대 3회 재시도
- 지수 백오프: 2초, 4초, 8초
- `shared` 모듈의 `executeWithRetry()` 사용

**Batch-Retry (전체 수집)**:
- Phase 1: 전체 페이지 순회 (실패 시 다음 페이지 진행)
- Phase 2: 실패한 페이지만 모아서 일괄 재시도
- 재시도 후에도 실패 시 `CollectionException` 발생

### 파라미터
| 파라미터 | 값 | 비고 |
|---------|---|------|
| `numOfRows` | 1000 | 페이지당 최대 |
| `maxRetries` | 3 | 페이지당 재시도 횟수 |
| `fixedRate` | 600000ms | 10분 주기 |

---

## 환경 설정

### 1. 로컬 환경 파일 생성

템플릿 파일을 복사하여 로컬 설정 파일을 만듭니다:

```bash
cp .env.local.example .env.local
```

### 2. 필수 환경변수 설정

`.env.local` 파일을 열어 실제 값으로 수정합니다:

```bash
# 데이터베이스 설정
DB_URL=jdbc:postgresql://localhost:5432/lit_maindb
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Safety Data API 설정 (필수)
SAFETY_DATA_API_KEY=실제-발급받은-API-키

# 서버 설정
SERVER_PORT=8081
```

### 3. 설정 확인

환경변수가 설정되지 않은 경우 다음 기본값이 사용됩니다:

| 환경변수 | 기본값 | 필수 여부 |
|----------|-----------|-----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/lit_maindb` | 아니오 |
| `DB_USERNAME` | `postgres` | 아니오 |
| `DB_PASSWORD` | `postgres` | 아니오 |
| `SAFETY_DATA_API_KEY` | (없음) | **예** |
| `SERVER_PORT` | `8081` | 아니오 |

**주의**: `SAFETY_DATA_API_KEY`는 Safety Data API에서 기사를 수집하기 위해 반드시 설정되어야 합니다.

### 보안 주의사항

- `.env.local` 파일은 gitignore에 포함되어 있으며 **절대 커밋하면 안 됩니다**
- 팀원들에게는 `.env.local.example` 파일을 템플릿으로 공유하세요
- 프로덕션 환경에서는 적절한 비밀 관리 도구를 사용하세요 (AWS Secrets Manager, Vault 등)

---

## 애플리케이션 실행

### 사전 요구사항

1. **PostgreSQL 실행**
   ```bash
   # Docker를 사용하는 경우
   cd infrastructure && docker-compose up -d postgres
   ```

2. **환경변수 설정** (위 [환경 설정](#환경-설정) 섹션 참조)

### 실행 방법

#### 방법 A: Gradle을 통한 실행

```bash
# 프로젝트 루트에서
./gradlew collector:bootRun
```

#### 방법 B: 환경변수 로드 후 실행

```bash
# collector 디렉토리에서
set -a && source .env.local && set +a
./gradlew bootRun
```

#### 방법 C: IntelliJ IDEA 사용

1. Run/Debug Configurations → Edit Configurations
2. Environment variables → Load from file
3. `.env.local` 파일 선택
4. 실행

#### 방법 D: JAR 빌드 후 실행

```bash
# 빌드
./gradlew collector:bootJar

# 실행
java -jar collector/build/libs/collector-0.0.1-SNAPSHOT.jar
```

### 실행 확인

애플리케이션이 정상적으로 시작되면 다음 로그를 확인할 수 있습니다:

```
Started CollectorApplication in X.XXX seconds
Starting collection for date: 2026-01-27
Successfully collected articles for 2026-01-27
```

### 수동 Backfill 실행

특정 날짜의 데이터를 수집하려면:

```bash
curl -X POST http://localhost:8081/api/collector/backfill \
  -H "Content-Type: application/json" \
  -d '{"startDate": "2026-01-10"}'
```

---

## 테스트

### 테스트 구조

```
collector/src/test/kotlin/
└── com/vonkernel/lit/collector/
    ├── domain/service/
    │   ├── ArticleCollectionServiceImplTest.kt           # 단위 테스트
    │   └── ArticleCollectionServiceImplIntegrationTest.kt  # 통합 테스트
    └── adapter/outbound/
        └── SafetyDataApiAdapterIntegrationTest.kt         # API 통합 테스트
```

### 테스트 종류

#### 1. 단위 테스트 (`ArticleCollectionServiceImplTest`)

**대상**: `ArticleCollectionServiceImpl` 비즈니스 로직

**테스트 케이스**:
- 단일 페이지 수집 성공
- 다중 페이지 수집 성공
- 검증 실패한 article 필터링
- 이미 존재하는 article 필터링
- 페이지 수집 실패 후 재시도 성공
- 재시도 후에도 실패 시 예외 발생
- API 호출 실패 시 지수 백오프로 재시도
- 최대 재시도 초과 시 예외 발생
- 빈 페이지 응답 처리

**기술**: MockK를 사용한 의존성 모킹

#### 2. 통합 테스트 (`ArticleCollectionServiceImplIntegrationTest`)

**대상**: 실제 PostgreSQL 연동 검증

**환경**:
- `@SpringBootTest`
- 실제 DB 연결 필요
- Testcontainers 또는 로컬 PostgreSQL 사용

#### 3. API 통합 테스트 (`SafetyDataApiAdapterIntegrationTest`)

**대상**: Safety Data API 실제 호출 검증

**주의**:
- 실제 API 키 필요 (`SAFETY_DATA_API_KEY` 환경변수)
- 네트워크 연결 필요
- API 할당량 고려

### 테스트 실행

#### 전체 테스트 실행
```bash
./gradlew collector:test
```

#### 특정 테스트 클래스 실행
```bash
./gradlew collector:test --tests ArticleCollectionServiceImplTest
```

#### 단위 테스트만 실행
```bash
./gradlew collector:test --tests '*Test'
```

#### 통합 테스트만 실행
```bash
./gradlew collector:test --tests '*IntegrationTest'
```

### 테스트 리포트 확인

```bash
open collector/build/reports/tests/test/index.html
```

---

## 주요 설계 결정

### 1. Article ID 생성 전략

**현재 전략**: `{YYYY-MM-DD}-{YNA_NO}` 형식의 결정적(deterministic) ID

**이유**:
- 동일한 API 응답은 항상 동일한 Article ID 생성
- 중복 제거 로직의 일관성 보장
- 날짜 기반 partitioning 지원 가능

### 2. 중복 제거 전략

**방식**: `ArticleRepository.filterNonExisting()` 사용

**메커니즘**:
```kotlin
val existingIds = articleRepository.filterNonExisting(articleIds).toSet()
val newArticles = articles.filter { it.articleId in existingIds }
```

**장점**:
- 단일 DB 쿼리로 배치 중복 체크
- 1000개 article 기준 1회 IN 절 쿼리
- 메모리 효율적

### 3. 시간대 처리

**KST 기준 사용**:
```kotlin
val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
```

**이유**:
- 연합뉴스 기사는 한국 시간 기준
- 사용자도 한국 시간 기준 검색 기대
- 내부 저장은 UTC Instant (DB 일관성)

### 4. 페이지 크기 최적화

**numOfRows=1000 선택**:
- API 호출 횟수 최소화
- 평균 응답 시간 안정적
- 대부분의 날짜별 데이터 1-2페이지로 커버

---

## 의존성

### 주요 라이브러리
| 라이브러리 | 용도 |
|----------|------|
| Spring Boot 4.0 | 프레임워크 |
| Spring WebFlux (WebClient) | 비동기 HTTP 클라이언트 |
| Kotlin Coroutines | 비동기 처리 |
| Spring Data JPA | 영속성 계층 (persistence 모듈 통해) |
| PostgreSQL Driver | DB 연결 |
| MockK | 테스트 모킹 |

### 모듈 의존성
- `shared`: Article, ArticleRepository
- `persistence`: ArticleRepository 구현체

---

**Tech Stack**: Kotlin 2.21 | Spring Boot 4.0 | Coroutines | PostgreSQL 18 | WebClient