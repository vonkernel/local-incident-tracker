# Searcher Service

사용자의 사건사고 검색 요청을 처리하는 REST API 서비스입니다. OpenSearch에 색인된 `ArticleIndexDocument`를 대상으로 전문 검색, 지리 검색, 카테고리 필터링, 시맨틱 검색 등 다층적 검색 기능을 제공합니다.

---

## 목차

- [역할 및 책임](#역할-및-책임)
- [데이터 흐름](#데이터-흐름)
- [검색 기능](#검색-기능)
- [아키텍처](#아키텍처)
- [주요 컴포넌트](#주요-컴포넌트)
- [프로젝트 구조](#프로젝트-구조)
- [환경 설정](#환경-설정)
- [애플리케이션 실행](#애플리케이션-실행)
- [테스트](#테스트)
- [주요 설계 결정](#주요-설계-결정)
- [의존성](#의존성)

---

## 역할 및 책임

searcher는 사용자 대면 검색 서비스로, OpenSearch에 색인된 사건사고 데이터를 검색하고 결과를 반환하는 **읽기 전용** 서비스입니다.

### 핵심 책임
- REST API를 통해 사용자의 검색 요청 수신
- 검색 조건(텍스트, 위치, 카테고리, 날짜, 시맨틱)을 OpenSearch 쿼리로 변환
- OpenSearch에서 검색 실행 및 결과 매핑
- 3가지 정렬 기준 제공 (관련성, 날짜, 거리)
- 시맨틱 검색을 위한 쿼리 텍스트 임베딩 변환

### 설계 제약
- **읽기 전용**: 어떤 영속 저장소에도 쓰기 작업을 수행하지 않음
- **OpenSearch 전용**: PostgreSQL에 직접 접근하지 않음 (CQRS Read Model만 사용)
- **무상태**: 세션이나 캐시 없이 매 요청을 독립적으로 처리

---

## 데이터 흐름

```
사용자
  ↓ HTTP 요청
REST Controller (검색 요청 수신)
  ↓ SearchCriteria
ArticleSearchService (검색 오케스트레이션)
  ├─ 시맨틱 검색 시 → Embedder (쿼리 텍스트 임베딩 변환)
  ↓ SearchCriteria + ByteArray?
ArticleSearcher (검색 실행)
  ├─ SearchQueryBuilder → OpenSearch Query DSL
  ├─ OpenSearch 실행
  └─ SearchResultMapper → SearchResult
  ↓ SearchResult
REST Controller → HTTP 응답
```

---

## 검색 기능

### 검색 조건 요약

| 기능 | 검색 대상 | 쿼리 방식 |
|------|----------|----------|
| 전문 검색 | title, content, keywords | `multi_match` (Nori 분석기, AND, BestFields) |
| 시맨틱 검색 | contentEmbedding (128차원) | `hybrid` (BM25 + kNN), min_score 0.8 |
| 법정구역 코드 | jurisdictionCodes | `prefix` (계층적 필터) |
| 주소 텍스트 | addresses (nested) | `match` (addressName) + `term` (depth1~3Name) |
| 행정구역명 조합 | addresses (nested) | `bool.must` → `term` (depth1~3Name) |
| 거리 기반 | geoPoints.location | `geo_distance` (nested) |
| 카테고리 | incidentTypes.code | `terms` (nested) |
| 긴급도 | urgency.level | `range` (gte) |
| 날짜 범위 | incidentDate | `range` |

### 하이브리드 검색 (BM25 + kNN)

시맨틱 검색 활성화 시 OpenSearch `hybrid` 쿼리를 사용합니다:

- BM25(multiMatch)와 kNN 벡터 검색을 결합
- `normalization-processor` search pipeline으로 점수 정규화 (min_max → arithmetic_mean, BM25 30% : kNN 70%)
- 필터 조건은 각 sub-query 내부에 **pre-filter**로 적용

### 정렬

| SortType | 정렬 기준 | 전제 조건 |
|----------|----------|----------|
| `RELEVANCE` | BM25 점수순 (+ 시맨틱 시 하이브리드 점수) | `query` 필수 |
| `DATE` | `incidentDate` 내림차순 | 없음 |
| `DISTANCE` | 좌표 기준 거리 오름차순 | `proximity` 필수 |

---

## 아키텍처

searcher는 **Hexagonal Architecture (Ports & Adapters)** 패턴을 따르며, 프로젝트 전체의 **OOP + FP Hybrid** 전략을 사용합니다.

```
┌─────────────────────────────────────────────────┐
│ Adapter Layer (OOP - Spring Beans)              │
│ Inbound:                                        │
│   ArticleSearchController (REST)                │
│   DTO (요청/응답 모델)                             │
│ Outbound:                                       │
│   OpenSearchArticleSearcher (OpenSearch Client)  │
│   ├─ SearchQueryBuilder (쿼리 구성)               │
│   └─ SearchResultMapper (응답 매핑)               │
│   EmbeddingAdapter (OpenAI Embedding)            │
└───────────────────┬─────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Port Interfaces (Contracts)                     │
│   ArticleSearcher, Embedder                     │
└───────────────────┬─────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Domain Service (FP - Orchestration)             │
│   ArticleSearchService                          │
└───────────────────┬─────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│ Domain Models (Immutable)                       │
│   SearchCriteria, SearchResult,                 │
│   ProximityFilter, RegionFilter, SortType       │
└─────────────────────────────────────────────────┘
```

---

## 주요 컴포넌트

### Port Interfaces

| 인터페이스 | 역할 |
|-----------|------|
| `ArticleSearcher` | 검색 실행 계약. `SearchCriteria` → `SearchResult` |
| `Embedder` | 텍스트 임베딩 생성 계약. indexer와 동일한 인터페이스 |

### Domain Service

| 컴포넌트 | 역할 |
|---------|------|
| `ArticleSearchService` | 검색 파이프라인 오케스트레이터. 조건부 임베딩 → 검색 실행. 임베딩 실패 시 전문 검색으로 fallback |

### Adapter Layer

| 컴포넌트 | 역할 |
|---------|------|
| `ArticleSearchController` | REST Controller. 검색 요청 파라미터 → `SearchCriteria` 변환 |
| `OpenSearchArticleSearcher` | `ArticleSearcher` 구현체. `SearchQueryBuilder`와 `SearchResultMapper`를 내부적으로 사용 |
| `SearchQueryBuilder` | `SearchCriteria` + 쿼리 임베딩 → OpenSearch Query DSL 변환 |
| `SearchResultMapper` | OpenSearch `SearchResponse` → `SearchResult` 변환 |
| `EmbeddingAdapter` | ai-core의 `EmbeddingExecutor`에 위임. OpenAI `text-embedding-3-small` (128차원) |

---

## 프로젝트 구조

```
searcher/src/main/kotlin/com/vonkernel/lit/searcher/
├── SearcherApplication.kt
├── adapter/
│   ├── inbound/
│   │   └── http/
│   │       ├── ArticleSearchController.kt
│   │       └── dto/
│   │           ├── SearchRequest.kt
│   │           └── SearchResponse.kt
│   └── outbound/
│       ├── embedding/
│       │   └── EmbeddingAdapter.kt
│       └── opensearch/
│           ├── OpenSearchArticleSearcher.kt
│           ├── SearchQueryBuilder.kt
│           ├── SearchResultMapper.kt
│           └── config/
│               └── OpenSearchClientConfig.kt
├── domain/
│   ├── model/
│   │   ├── SearchCriteria.kt
│   │   └── SearchResult.kt
│   ├── port/
│   │   ├── ArticleSearcher.kt
│   │   └── Embedder.kt
│   ├── service/
│   │   └── ArticleSearchService.kt
│   └── exception/
│       └── ArticleSearchException.kt
```

---

## 환경 설정

### 필수 환경변수

```bash
# OpenAI API Key (시맨틱 검색용 쿼리 임베딩)
SPRING_AI_OPENAI_API_KEY=sk-your-api-key-here

# OpenSearch 설정
OPENSEARCH_HOST=localhost
OPENSEARCH_PORT=9200
OPENSEARCH_INDEX_NAME=articles
```

### 설정 확인

| 환경변수 | 기본값 | 필수 여부 | 용도 |
|----------|--------|-----------|------|
| `SPRING_AI_OPENAI_API_KEY` | (없음) | **예** | 시맨틱 검색 쿼리 임베딩 (OpenAI text-embedding-3-small) |
| `OPENSEARCH_HOST` | `localhost` | 아니오 | OpenSearch 호스트 |
| `OPENSEARCH_PORT` | `9200` | 아니오 | OpenSearch 포트 |
| `OPENSEARCH_INDEX_NAME` | `articles` | 아니오 | OpenSearch 인덱스명 |

---

## 애플리케이션 실행

### 사전 요구사항

1. **OpenSearch 실행**
   ```bash
   cd infrastructure && docker-compose up -d opensearch
   ```

2. **인덱스 및 파이프라인 생성**
   ```bash
   cd infrastructure/opensearch && ./create-index.sh
   ```

3. **환경변수 설정** (위 [환경 설정](#환경-설정) 섹션 참조)

### 실행 방법

#### 방법 A: Gradle을 통한 실행

```bash
# 프로젝트 루트에서
./gradlew searcher:bootRun
```

#### 방법 B: IntelliJ IDEA 사용

1. Run/Debug Configurations → Edit Configurations
2. Environment variables → Load from file
3. `.env.local` 파일 선택
4. 실행

### 실행 확인

애플리케이션이 정상적으로 시작되면 다음 로그를 확인할 수 있습니다:

```
Started SearcherApplication in X.XXX seconds
```

---

## 테스트

### 테스트 종류

#### 단위 테스트

외부 의존성을 MockK로 모킹하여 비즈니스 로직만 검증합니다.

- `SearchQueryBuilderTest`: 검색 조건 → OpenSearch 쿼리 변환 (전문/시맨틱/필터/정렬)
- `SearchResultMapperTest`: OpenSearch 응답 → 도메인 모델 변환
- `ArticleSearchServiceTest`: 검색 파이프라인 오케스트레이션, 임베딩 실패 시 fallback
- `ArticleSearchControllerTest`: REST 엔드포인트, 파라미터 매핑
- `OpenSearchArticleSearcherTest`: OpenSearch 클라이언트 상호작용

#### 통합 테스트

- `@Tag("integration")` 태그로 구분
- 로컬 OpenSearch 인스턴스 및 OpenAI API Key 필요

### 테스트 실행

```bash
# 전체 단위 테스트
./gradlew searcher:test

# 통합 테스트 포함
./gradlew searcher:integrationTest

# 특정 테스트 클래스
./gradlew searcher:test --tests SearchQueryBuilderTest
```

---

## 주요 설계 결정

### 1. 읽기 전용 서비스

CQRS Read Model 전용 서비스로, OpenSearch에서만 읽기 수행. PostgreSQL에 직접 접근하지 않아 서비스 간 결합도를 최소화하고 수평 확장이 용이합니다.

### 2. 시맨틱 검색 Graceful Degradation

쿼리 임베딩 생성 실패 시 시맨틱 검색을 제외하고 전문 검색으로 fallback합니다. OpenAI API 장애 시에도 기본 검색 기능은 유지됩니다.

### 3. 하이브리드 쿼리 Pre-filter

시맨틱 검색 시 필터 조건을 hybrid 쿼리의 각 sub-query 내부에 pre-filter로 적용합니다. BM25에는 `bool.filter`, kNN에는 `knn.filter`를 사용하여 필터 범위 내에서만 검색을 수행합니다.

### 4. 쿼리 구성/응답 매핑을 Adapter 계층에 배치

`SearchQueryBuilder`와 `SearchResultMapper`는 OpenSearch DSL에 직접 종속되는 기술 구현체이므로 domain이 아닌 adapter에 배치합니다. `ArticleSearcher` 포트가 도메인 모델만 주고받으므로, 도메인 서비스는 OpenSearch DSL을 알지 못합니다.

### 5. 3가지 독립 정렬 모드

`function_score` 복합 점수 대신 RELEVANCE / DATE / DISTANCE 독립 정렬을 제공합니다. 사용자의 검색 의도에 따라 최적의 정렬을 선택할 수 있으며, 쿼리 구성과 정렬 기준이 분리되어 구현이 단순합니다.

---

## 의존성

### 모듈 의존성

| 모듈 | 제공 |
|------|------|
| `shared` | 도메인 모델 (`ArticleIndexDocument`, `Address`, `Coordinate`, `IncidentType`, `Urgency` 등) |
| `ai-core` | `EmbeddingExecutor` (OpenAI Embedding API 실행) |

### 외부 API

| API | 용도 | 인증 |
|-----|------|------|
| OpenAI Embedding API | 쿼리 텍스트 벡터 임베딩 (`text-embedding-3-small`, 128차원) | `SPRING_AI_OPENAI_API_KEY` |

### 주요 라이브러리

| 라이브러리 | 용도 |
|----------|------|
| Spring Boot 4.0 | 프레임워크 |
| Spring Boot Starter Web | REST API (Spring MVC) |
| OpenSearch Java Client 3.4 | OpenSearch 검색 실행 |
| Spring AI 2.0 (OpenAI) | OpenAI Embedding API 호출 (ai-core 모듈 통해) |
| Kotlin Coroutines | 비동기 처리 (임베딩 호출) |
| Jackson | JSON 직렬화/역직렬화 |
| MockK | 테스트 모킹 |

---

**Tech Stack**: Kotlin 2.21 | Spring Boot 4.0 | Spring MVC | OpenSearch 3.3 | Spring AI 2.0 | OpenAI text-embedding-3-small
