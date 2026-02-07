# Searcher Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 7 | 49 | - |
| 통합 테스트 | - | - | - |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../domain/service/` | 서비스 로직 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/outbound/opensearch/` | 쿼리 빌더, 결과 매퍼 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/inbound/http/` | Controller, 예외 핸들러 테스트 |

## 테스트 실행

```bash
# 전체 테스트
./gradlew searcher:test

# 쿼리 빌더 테스트
./gradlew searcher:test --tests SearchQueryBuilderTest

# 서비스 테스트
./gradlew searcher:test --tests ArticleSearchServiceTest
```

## 단위 테스트 체크리스트

### SearchQueryBuilder

검색 조건 → OpenSearch Query DSL 변환 로직 검증.

**기본 쿼리**:
- [x] 빈 검색 조건은 match_all 쿼리를 생성한다
- [x] 텍스트만 있는 RELEVANCE 정렬은 must에 multiMatch를 생성한다
- [x] 텍스트가 있는 DATE 정렬은 multiMatch를 직접 생성한다
- [x] 텍스트가 있는 DISTANCE 정렬은 multiMatch와 geo_distance 정렬을 생성한다

**시맨틱 검색**:
- [x] 시맨틱 검색은 knn과 multiMatch 하위 쿼리로 hybrid 쿼리를 사용한다
- [x] 필터가 있는 시맨틱 검색은 hybrid 하위 쿼리에 사전 필터를 적용한다

**필터**:
- [x] 관할 코드 필터는 prefix 쿼리를 사용한다
- [x] 주소 쿼리 필터는 nested bool should를 사용한다
- [x] 지역 필터는 term 쿼리로 nested bool must를 사용한다
- [x] depth1Name만 있는 지역 필터는 단일 term 쿼리를 생성한다
- [x] 빈 지역 필터는 무시된다
- [x] 근접 필터는 nested geo_distance 쿼리를 사용한다
- [x] 사건 유형 필터는 nested terms 쿼리를 사용한다
- [x] 긴급도 필터는 gte가 포함된 range 쿼리를 사용한다
- [x] 날짜 범위 필터는 range 쿼리를 사용한다
- [x] dateFrom만 있는 날짜 범위 필터

**텍스트 + 필터 결합**:
- [x] 필터가 있는 텍스트 쿼리는 bool must + filter로 감싼다

**페이지네이션 & 하이라이트**:
- [x] 페이지네이션이 올바르게 적용된다
- [x] 하이라이트 필드가 설정된다

### SearchResultMapper

OpenSearch 응답 → 도메인 모델 변환 로직 검증.

- [x] 빈 응답을 빈 결과로 매핑한다
- [x] 검색 결과 문서를 도메인 모델로 매핑한다
- [x] 페이지네이션 메타데이터를 올바르게 매핑한다
- [x] null인 선택 필드를 정상 처리한다

### ArticleSearchService

검색 파이프라인 오케스트레이션 검증.

- [x] 전문 검색은 임베딩 없이 실행한다
- [x] 시맨틱 검색은 임베딩을 생성하여 검색기에 전달한다
- [x] 시맨틱 검색은 임베딩 실패 시 전문 검색으로 폴백한다
- [x] RELEVANCE 정렬에 쿼리 없으면 예외 발생
- [x] DISTANCE 정렬에 proximity 없으면 예외 발생
- [x] 검색 실패 시 ArticleSearchException으로 전파
- [x] DATE 정렬에 쿼리 없으면 임베딩 건너뜀
- [x] 시맨틱 검색에 빈 쿼리면 임베더 호출하지 않음

### ArticleSearchController

REST 엔드포인트 및 요청 매핑 검증.

- [x] search는 결과와 함께 200을 반환한다
- [x] search는 파라미터를 올바르게 매핑한다
- [x] 불완전한 proximity 필드는 InvalidSearchRequestException을 발생시킨다
- [x] 음수 distanceKm은 InvalidSearchRequestException을 발생시킨다
- [x] 음수 page는 InvalidSearchRequestException을 발생시킨다
- [x] 100 초과 size는 InvalidSearchRequestException을 발생시킨다
- [x] SearchRequest의 toCriteria가 올바르게 변환한다

### GlobalExceptionHandler

예외 처리 검증.

- [x] GlobalExceptionHandler는 InvalidSearchRequestException을 400으로 처리한다
- [x] GlobalExceptionHandler는 ArticleSearchException을 500으로 처리한다
- [x] GlobalExceptionHandler는 예상치 못한 Exception을 500으로 처리한다
- [x] (추가 4개 테스트)

### OpenSearchArticleSearcher

OpenSearch 클라이언트 상호작용 검증.

- [x] 검색 요청 실행
- [x] 에러 핸들링

### EmbeddingAdapter

임베딩 생성 위임 검증.

- [x] ai-core EmbeddingExecutor 위임
- [x] 임베딩 결과 반환

## 테스트 환경

### 단위 테스트
- MockK를 사용한 의존성 모킹
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- Spring Context 없이 실행
- OpenSearch 클라이언트 응답 모킹
