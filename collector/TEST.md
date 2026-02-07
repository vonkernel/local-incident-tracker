# Collector Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | Line 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 3 | 21 | 61.2% |
| 통합 테스트 | 2 | - | - |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../domain/service/` | 비즈니스 로직 테스트 |
| 단위 테스트 | `src/test/kotlin/.../adapter/outbound/news/` | Mapper 테스트 |
| 통합 테스트 | `src/test/kotlin/.../domain/service/` | DB 연동 테스트 |
| 통합 테스트 | `src/test/kotlin/.../adapter/outbound/` | API 연동 테스트 |

## 테스트 실행

```bash
# 전체 테스트
./gradlew collector:test

# 단위 테스트만
./gradlew collector:test --tests '*Test'

# 통합 테스트만 (실제 API/DB 호출)
./gradlew collector:test --tests '*IntegrationTest'

# 특정 테스트 클래스
./gradlew collector:test --tests ArticleCollectionServiceImplTest
```

## 단위 테스트 체크리스트

### ArticleCollectionServiceImpl

수집 파이프라인 오케스트레이션 로직 검증.

- [x] 단일 페이지 수집 성공
- [x] 다중 페이지 수집 성공
- [x] 검증 실패한 article은 저장하지 않음
- [x] 이미 존재하는 article은 저장하지 않음
- [x] 모든 article이 이미 존재하면 저장 호출 안함
- [x] 페이지 수집 실패 후 재시도 성공
- [x] 재시도 후에도 실패하면 예외 발생
- [x] API 호출 실패 시 지수 백오프로 재시도
- [x] 최대 재시도 초과 시 예외 발생
- [x] 빈 페이지 응답 시 저장 호출 안함

### ArticleValidator

Article 비즈니스 규칙 검증.

- [x] 유효한 Article은 성공 결과 반환
- [x] 빈 title은 검증 실패
- [x] 빈 content는 검증 실패
- [x] 빈 originId는 검증 실패
- [x] 빈 sourceId는 검증 실패
- [x] 여러 필드가 비어있으면 모든 오류 포함

### YonhapnewsArticleMapper

API 응답 → Article 정규화 로직 검증.

- [x] 기본 필드 매핑 검증 (sourceId, originId, sourceUrl)
- [x] articleId 생성 형식 검증 (`YYYY-MM-DD-{articleNo}`)
- [x] publishedAt 날짜 파싱 검증 (KST → UTC)
- [x] createdAt 날짜 파싱 검증 (KST → UTC)
- [x] title과 content 공백 제거 검증

## 통합 테스트 체크리스트

### SafetyDataApiAdapterIntegrationTest

실제 Safety Data API 호출 검증.

- [ ] 실제 API 호출 및 응답 파싱
- [ ] 페이지네이션 동작 확인
- [ ] 에러 응답 처리

**필요 환경**:
- `SAFETY_DATA_API_KEY` 환경변수 필수
- 네트워크 연결 필요
- API 할당량 주의

### ArticleCollectionServiceImplIntegrationTest

실제 PostgreSQL 연동 검증.

- [ ] 수집된 기사 DB 저장
- [ ] 중복 기사 필터링 동작
- [ ] 트랜잭션 롤백 동작

**필요 환경**:
- PostgreSQL 실행 필요 (Testcontainers 또는 로컬)
- DB 연결 환경변수 설정

## 테스트 환경

### 단위 테스트
- MockK를 사용한 의존성 모킹
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- Spring Context 없이 실행

### 통합 테스트
- `@SpringBootTest` 사용
- 실제 외부 API/DB 호출
- 환경변수 설정 필요 (`SAFETY_DATA_API_KEY`, DB 연결 정보)
