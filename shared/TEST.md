# Shared Tests

## 테스트 커버리지

| 유형 | 클래스 수 | 테스트 수 | Line 커버리지 |
|------|:--------:|:--------:|:--------:|
| 단위 테스트 | 1 | 6 | 20.7% |

## 테스트 구조

| 유형 | 위치 | 설명 |
|------|------|------|
| 단위 테스트 | `src/test/kotlin/.../util/` | 유틸리티 함수 테스트 |

## 테스트 실행

```bash
# 단위 테스트
./gradlew shared:test
```

## 단위 테스트 체크리스트

### RetryUtil

- [x] 첫 시도에 성공하면 바로 반환
- [x] 재시도 후 성공
- [x] 최대 재시도 초과 시 MaxRetriesExceededException 발생
- [x] onRetry 콜백이 올바른 인자로 호출됨
- [x] maxRetries가 0이면 재시도 없이 즉시 실패
- [x] 원본 예외가 cause로 전달됨

## 테스트 환경

### 단위 테스트
- `kotlinx-coroutines-test`를 사용한 Coroutine 테스트
- 외부 의존성 없음
