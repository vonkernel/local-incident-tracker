# Collector External APIs

이 모듈이 호출하는 외부 API 레퍼런스.

## 목차

- [API 1: Safety Data API (연합뉴스 재난 API)](#api-1-safety-data-api)

---

## API 1: Safety Data API

### 개요

| 항목 | 값 |
|------|------|
| 공식 문서 | [공공데이터포털 - 연합뉴스 안전행정부 재난 API](https://www.data.go.kr/data/3038936/openapi.do) |
| Base URL | `https://www.safetydata.go.kr` |
| 인증 방식 | Query Parameter (serviceKey) |
| 환경변수 | `SAFETY_DATA_API_KEY` |

### 인증

```
GET /V2/api/DSSP-IF-00051?serviceKey={API_KEY}&...
```

API 키를 `serviceKey` 쿼리 파라미터로 전달.

### 엔드포인트

#### 재난 기사 목록 조회

**요청**

```
GET /V2/api/DSSP-IF-00051
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|:----:|------|
| `serviceKey` | string | O | API 인증 키 |
| `inqDt` | string | O | 조회 날짜 (YYYYMMDD). 시스템 등록일 기준 |
| `pageNo` | integer | O | 페이지 번호 (1부터 시작) |
| `numOfRows` | integer | O | 페이지당 결과 수 (최대 1000) |
| `returnType` | string | O | 응답 형식 (`json` 고정) |

**응답**

```json
{
  "header": {
    "resultCode": "00",
    "resultMsg": "OK",
    "errorMsg": null
  },
  "numOfRows": 1000,
  "pageNo": 1,
  "totalCount": 150,
  "body": [
    {
      "YNA_NO": 12345,
      "YNA_TTL": "기사 제목",
      "YNA_CN": "기사 본문 내용...",
      "YNA_YMD": "2026-01-15 14:30:00",
      "YNA_WRTR_NM": "홍길동",
      "CRT_DT": "2026/01/15 14:30:00.000000000"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `header.resultCode` | string | 결과 코드 ("00" = 성공) |
| `header.resultMsg` | string | 결과 메시지 |
| `numOfRows` | integer | 요청한 페이지당 결과 수 |
| `pageNo` | integer | 현재 페이지 번호 |
| `totalCount` | integer | 전체 결과 수 |
| `body` | array | 기사 목록 |

#### body 배열 항목 (기사)

| 필드 | 타입 | 설명 | 매핑 |
|------|------|------|------|
| `YNA_NO` | integer | 연합뉴스 기사 고유 ID | `originId`, `articleId` 일부 |
| `YNA_TTL` | string | 기사 제목 | `title` |
| `YNA_CN` | string | 기사 본문 | `content` |
| `YNA_YMD` | string | 기사 작성/발행 일시 (`yyyy-MM-dd HH:mm:ss`, KST) | `writtenAt` |
| `YNA_WRTR_NM` | string | 작성자 이름 | (사용 안함) |
| `CRT_DT` | string | 시스템 등록 일시 (`yyyy/MM/dd HH:mm:ss.SSSSSSSSS`, KST) | `modifiedAt` |

**요청 예시**

```bash
curl -X GET "https://www.safetydata.go.kr/V2/api/DSSP-IF-00051" \
  -G \
  --data-urlencode "serviceKey=YOUR_API_KEY" \
  --data-urlencode "inqDt=20260115" \
  --data-urlencode "pageNo=1" \
  --data-urlencode "numOfRows=1000" \
  --data-urlencode "returnType=json"
```

**응답 예시**

```json
{
  "header": {
    "resultCode": "00",
    "resultMsg": "OK",
    "errorMsg": null
  },
  "numOfRows": 1000,
  "pageNo": 1,
  "totalCount": 25,
  "body": [
    {
      "YNA_NO": 54321,
      "YNA_TTL": "서울 강남구 건물 화재 발생",
      "YNA_CN": "15일 오후 2시경 서울 강남구 역삼동의 5층 건물에서 화재가 발생했다...",
      "YNA_YMD": "2026-01-15 14:30:00",
      "YNA_WRTR_NM": "김기자",
      "CRT_DT": "2026/01/15 14:35:00.123456789"
    }
  ]
}
```

### API 동작 특성

#### inqDt 파라미터의 불명확한 필터링

`inqDt` 파라미터는 시스템 등록일(`CRT_DT`) 기준으로 필터링하는 것으로 예상되나, 실제 동작은 불명확하다.

**관찰된 현상**:
- 서로 다른 `inqDt`로 조회해도 동일한 기사가 반환될 수 있음
- `inqDt=20260112` 조회 결과에 1월 12일~15일 작성 기사가 모두 포함될 수 있음

#### 정렬 순서 불명확

`CRT_DT`, `YNA_YMD`, `YNA_NO` 모두 일관된 정렬 순서를 보이지 않음.

**대응 전략**:
- `YNA_NO` 기반 중복 제거 필수 구현 (결정적 ID 생성)
- 정기적 재수집으로 늦게 등록된 기사 보완
- 빠른 페이지네이션으로 데이터 변경 최소화

### 에러 처리

| 상태 코드 | 원인 | 대응 |
|:---------:|------|------|
| 200 + `resultCode != "00"` | API 레벨 에러 | 에러 메시지 로깅, 재시도 |
| 400 | 잘못된 요청 파라미터 | 파라미터 확인 |
| 401 | 잘못된 API 키 | API 키 확인 |
| 500 | 서버 에러 | 재시도 (지수 백오프) |
| Timeout | 네트워크 지연 | 재시도 (지수 백오프) |

### 모듈 내 사용

| 컴포넌트 | 호출 엔드포인트 | 용도 |
|---------|---------------|------|
| `SafetyDataApiAdapter` | `/V2/api/DSSP-IF-00051` | 재난 기사 목록 조회 (페이지네이션) |

### 재시도 전략

| 항목 | 값 |
|------|------|
| 최대 재시도 횟수 | 3회 |
| 백오프 전략 | 지수 백오프 (2초, 4초, 8초) |
| 재시도 대상 | 네트워크 에러, 5xx 에러, Timeout |

### 데이터 매핑

API 응답 → `Article` 도메인 모델 변환 (`YonhapnewsArticleMapper`):

| API 필드 | Article 필드 | 변환 로직 |
|---------|-------------|----------|
| `YNA_NO` | `articleId` | `"{YYYY-MM-DD}-{YNA_NO}"` 형식으로 결정적 ID 생성 |
| `YNA_NO` | `originId` | `toString()` |
| (상수) | `sourceId` | `"yonhapnews"` |
| `YNA_YMD` | `writtenAt` | KST → UTC `Instant` 변환 |
| `CRT_DT` | `modifiedAt` | KST → UTC `Instant` 변환 |
| `YNA_TTL` | `title` | `trim()` |
| `YNA_CN` | `content` | `trim()` |
| (없음) | `sourceUrl` | `null` (API 미제공) |
