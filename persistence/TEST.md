# Persistence Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | Line 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 (Mapper) | 10 | 100 | 87.3% |
| 통합 테스트 (Adapter) | 5 | 100 | (포함) |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../mapper/` | Mapper 순수 함수 테스트 |
| 통합 테스트 | `src/test/kotlin/.../adapter/` | @DataJpaTest 기반 DB 통합 |

## 테스트 실행

```bash
# 전체 테스트
./gradlew persistence:test

# Mapper 단위 테스트만
./gradlew persistence:test --tests "*MapperTest"

# Adapter 통합 테스트만
./gradlew persistence:test --tests "*AdapterTest"
```

## 단위 테스트 체크리스트

### ArticleMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환
- [x] ZonedDateTime ↔ Instant 변환 정확성
- [x] sourceUrl null 처리

### AnalysisResultMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환
- [x] 복합 관계 (urgency, incidentTypes, locations) 매핑
- [x] null 필드 필터링

### LocationMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환
- [x] RegionType enum 매핑
- [x] 좌표 null 처리

### CoordinateMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환

### UrgencyMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환

### IncidentTypeMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환

### KeywordMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환

### RefinedArticleMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환
- [x] ZonedDateTime 변환

### TopicMapper

- [x] toDomainModel 변환
- [x] toPersistenceModel 변환

### AnalysisResultOutboxMapper

- [x] toOutboxEntity 변환
- [x] JSON 직렬화 검증

## 통합 테스트 체크리스트

### ArticleRepositoryAdapter

- [x] save - 단일 기사 저장
- [x] saveAll - 배치 저장
- [x] filterNonExisting - 존재하지 않는 ID 필터링
- [x] 중복 저장 시 업데이트

### AnalysisResultRepositoryAdapter

- [x] save - 분석 결과 저장
- [x] save - Outbox 엔티티 동시 저장
- [x] save - 기존 결과 삭제 후 재저장
- [x] findArticleUpdatedAtByArticleId - 타임스탬프 조회
- [x] 트랜잭션 원자성 검증
- [x] 복합 관계 (urgency, incidentTypes, addresses) 저장
- [x] 키워드, 주제, 정제된 기사 저장

### IncidentTypeRepositoryAdapter

- [x] findAll - 전체 사건 유형 조회
- [x] 마스터 데이터 정합성

### UrgencyRepositoryAdapter

- [x] findAll - 전체 긴급도 조회
- [x] 마스터 데이터 정합성

### AddressCacheRepositoryAdapter

- [x] findByAddressName - 주소명으로 조회
- [x] 캐시 미스 시 null 반환

## 테스트 환경

### 단위 테스트 (Mapper)
- 순수 함수 테스트
- 외부 의존성 없음
- JUnit 5 + AssertJ

### 통합 테스트 (Adapter)
- `@DataJpaTest` 사용
- H2 인메모리 데이터베이스
- Flyway 마이그레이션 자동 적용
- `TestFixtures.kt`로 테스트 데이터 생성

## 테스트 지원 클래스

| 클래스 | 역할 |
|--------|------|
| `PersistenceTestApplication` | @DataJpaTest용 컨텍스트 |
| `TestFixtures` | 테스트 데이터 빌더 |
