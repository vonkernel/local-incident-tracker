# Kakao Local API 참고 문서

Analyzer 모듈 지오코딩에 사용하는 Kakao Local API 레퍼런스.

- 공식 문서: https://developers.kakao.com/docs/latest/ko/local/dev-guide

---

## 인증

모든 요청에 REST API 키를 `Authorization` 헤더로 전달해야 한다.

```
Authorization: KakaoAK ${REST_API_KEY}
```

---

## 1. 주소로 좌표 변환

주소 문자열을 입력받아 좌표(위도/경도)와 상세 주소 정보를 반환한다.

### 요청

```
GET https://dapi.kakao.com/v2/local/search/address.json
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `query` | String | O | 검색할 주소 |
| `analyze_type` | String | X | `similar` (유사 매칭, 기본값) 또는 `exact` (정확 매칭) |
| `page` | Integer | X | 결과 페이지 번호 (1~45, 기본값: 1) |
| `size` | Integer | X | 페이지당 문서 수 (1~30, 기본값: 10) |

### 응답

#### `meta`

| 필드 | 타입 | 설명 |
|------|------|------|
| `total_count` | Integer | 검색어에 매칭된 전체 문서 수 |
| `pageable_count` | Integer | 노출 가능한 문서 수 |
| `is_end` | Boolean | 현재 페이지가 마지막 페이지인지 여부 |

#### `documents[]`

| 필드 | 타입 | 설명 |
|------|------|------|
| `address_name` | String | 전체 주소 문자열 |
| `address_type` | String | `REGION` \| `ROAD` \| `REGION_ADDR` \| `ROAD_ADDR` |
| `x` | String | 경도 (longitude) |
| `y` | String | 위도 (latitude) |
| `address` | Object | 지번 주소 상세 정보 (아래 참조) |
| `road_address` | Object | 도로명 주소 상세 정보 (아래 참조) |

#### `address` 객체 (지번 주소)

| 필드 | 타입 | 설명 |
|------|------|------|
| `address_name` | String | 전체 지번 주소 |
| `region_1depth_name` | String | 시/도 |
| `region_2depth_name` | String | 시/군/구 |
| `region_3depth_name` | String | 법정동/리 |
| `region_3depth_h_name` | String | 행정동 |
| `h_code` | String | 행정동 코드 |
| `b_code` | String | 법정동 코드 |
| `mountain_yn` | String | 산 여부 (`Y` / `N`) |
| `main_address_no` | String | 본번 |
| `sub_address_no` | String | 부번 |
| `x` | String | 경도 |
| `y` | String | 위도 |

#### `road_address` 객체 (도로명 주소)

| 필드 | 타입 | 설명 |
|------|------|------|
| `address_name` | String | 전체 도로명 주소 |
| `region_1depth_name` | String | 시/도 |
| `region_2depth_name` | String | 시/군/구 |
| `region_3depth_name` | String | 읍/면/동 |
| `road_name` | String | 도로명 |
| `underground_yn` | String | 지하 여부 (`Y` / `N`) |
| `main_building_no` | String | 건물 본번 |
| `sub_building_no` | String | 건물 부번 |
| `building_name` | String | 건물 이름 |
| `zone_no` | String | 우편번호 (5자리) |
| `x` | String | 경도 |
| `y` | String | 위도 |

### 요청 예시

```bash
curl -G "https://dapi.kakao.com/v2/local/search/address.json" \
  -H "Authorization: KakaoAK ${REST_API_KEY}" \
  --data-urlencode "query=전북 삼성동 100"
```

### 응답 예시

```json
{
  "meta": {
    "total_count": 4,
    "pageable_count": 4,
    "is_end": true
  },
  "documents": [
    {
      "address_name": "전북 익산시 부송동 100",
      "y": "35.97664845766847",
      "x": "126.99597295767953",
      "address_type": "REGION_ADDR",
      "address": {
        "address_name": "전북 익산시 부송동 100",
        "region_1depth_name": "전북",
        "region_2depth_name": "익산시",
        "region_3depth_name": "부송동",
        "region_3depth_h_name": "삼성동",
        "h_code": "4514069000",
        "b_code": "4514013400",
        "mountain_yn": "N",
        "main_address_no": "100",
        "sub_address_no": "",
        "x": "126.99597295767953",
        "y": "35.97664845766847"
      },
      "road_address": {
        "address_name": "전북 익산시 망산길 11-17",
        "region_1depth_name": "전북",
        "region_2depth_name": "익산시",
        "region_3depth_name": "부송동",
        "road_name": "망산길",
        "underground_yn": "N",
        "main_building_no": "11",
        "sub_building_no": "17",
        "building_name": "",
        "zone_no": "54547",
        "y": "35.976749396987046",
        "x": "126.99599512792346"
      }
    }
  ]
}
```

---

## 2. 키워드로 장소 검색

키워드로 장소를 검색하여 좌표, 주소, 장소 정보를 반환한다.

### 요청

```
GET https://dapi.kakao.com/v2/local/search/keyword.json
```

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `query` | String | O | 검색 키워드 |
| `category_group_code` | String | X | 카테고리 필터 (예: `MT1`, `CS2`, `FD6`) |
| `x` | String | X | 중심 좌표 경도 (거리 기반 검색 시) |
| `y` | String | X | 중심 좌표 위도 (거리 기반 검색 시) |
| `radius` | Integer | X | 검색 반경 (미터 단위, 0~20000) |
| `rect` | String | X | 사각 범위 (`left_x,left_y,right_x,right_y`) |
| `page` | Integer | X | 결과 페이지 번호 (1~45, 기본값: 1) |
| `size` | Integer | X | 페이지당 문서 수 (1~15, 기본값: 15) |
| `sort` | String | X | 정렬 기준 — `accuracy` (정확도, 기본값) 또는 `distance` (거리) |

### 응답

#### `meta`

| 필드 | 타입 | 설명 |
|------|------|------|
| `total_count` | Integer | 검색어에 매칭된 전체 문서 수 |
| `pageable_count` | Integer | 노출 가능한 문서 수 (최대 45) |
| `is_end` | Boolean | 현재 페이지가 마지막 페이지인지 여부 |
| `same_name` | Object | 질의어 분석 결과 (아래 참조) |

#### `same_name` 객체

| 필드 | 타입 | 설명 |
|------|------|------|
| `region` | String[] | 질의어에서 인식된 지역 리스트 |
| `keyword` | String | 질의어에서 지역 정보를 제외한 키워드 |
| `selected_region` | String | 현재 검색에 사용된 지역 정보 |

#### `documents[]`

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | String | 장소 ID |
| `place_name` | String | 장소명 |
| `category_name` | String | 카테고리 전체 경로 (예: `가정,생활 > 편의점 > CU`) |
| `category_group_code` | String | 카테고리 그룹 코드 |
| `category_group_name` | String | 카테고리 그룹명 |
| `phone` | String | 전화번호 |
| `address_name` | String | 전체 지번 주소 |
| `road_address_name` | String | 전체 도로명 주소 |
| `x` | String | 경도 |
| `y` | String | 위도 |
| `place_url` | String | 카카오맵 장소 상세 페이지 URL |
| `distance` | String | 중심 좌표까지의 거리 (미터, `x`/`y` 파라미터 제공 시에만) |

### 요청 예시

```bash
curl -G "https://dapi.kakao.com/v2/local/search/keyword.json" \
  -H "Authorization: KakaoAK ${REST_API_KEY}" \
  --data-urlencode "query=카카오프렌즈"
```

### 응답 예시

```json
{
  "meta": {
    "same_name": {
      "region": [],
      "keyword": "카카오프렌즈",
      "selected_region": ""
    },
    "pageable_count": 14,
    "total_count": 14,
    "is_end": true
  },
  "documents": [
    {
      "place_name": "카카오프렌즈 코엑스점",
      "distance": "418",
      "place_url": "http://place.map.kakao.com/26338954",
      "category_name": "가정,생활 > 문구,사무용품 > 디자인문구 > 카카오프렌즈",
      "address_name": "서울 강남구 삼성동 159",
      "road_address_name": "서울 강남구 영동대로 513",
      "id": "26338954",
      "phone": "02-6002-1880",
      "category_group_code": "",
      "category_group_name": "",
      "x": "127.05902969025047",
      "y": "37.51207412593136"
    }
  ]
}
```

---

## Analyzer에서의 활용

### LLM 위치 추출 결과 분류

LLM은 뉴스 기사에서 위치 표현을 추출할 때 각 표현의 **유형**을 함께 분류한다.

| 유형 | 설명 | 예시 | API 호출 |
|------|------|------|----------|
| `ADDRESS` | 행정구역 기반 주소 표현 | "하남시 선동", "전북", "서울특별시 강남구" | 주소 검색 API |
| `LANDMARK` | 랜드마크, 건물명, 시설명 등 키워드 | "코엑스", "판교JC", "서울역" | 키워드 장소 검색 API → 주소 검색 API |
| `UNRESOLVABLE` | 특정 좌표로 변환할 수 없는 광역/추상 표현 | "전국", "수도권", "남부지방" | API 호출 없음 |

### 유형별 처리 흐름

#### `ADDRESS` 유형: 주소 검색 API 직접 호출

```
LLM 추출: "하남시 선동" (type: ADDRESS)
    ↓
주소 검색 API: query="하남시 선동"
    ↓
응답 documents[0].address 매핑 → Location 도메인 모델
    ↓ (응답 없을 경우 fallback)
키워드 장소 검색 API: query="하남시 선동" → 주소 획득 → 주소 검색 API 재호출
```

#### `LANDMARK` 유형: 키워드 → 주소 → 좌표 2단계 호출

```
LLM 추출: "코엑스" (type: LANDMARK)
    ↓
키워드 장소 검색 API: query="코엑스"
    ↓
응답 documents[0].address_name → "서울 강남구 삼성동 159"
    ↓
주소 검색 API: query="서울 강남구 삼성동 159"
    ↓
응답 documents[0].address 매핑 → Location 도메인 모델 (체계적 주소 + 좌표)
```

#### `UNRESOLVABLE` 유형: API 호출 없이 저장

```
LLM 추출: "전국" (type: UNRESOLVABLE)
    ↓
API 호출 없음
    ↓
Location(
    coordinate = null,
    address = Address(
        regionType = UNKNOWN,
        code = "UNKNOWN",
        addressName = "전국",    ← LLM이 추출한 원본 표현 그대로 저장
        depth1Name = null,
        depth2Name = null,
        depth3Name = null
    )
)
```

### 실제 뉴스 기사 예시

| 기사 문장 | LLM 추출 | 유형 | API 호출 | DB 저장 결과 (Location 수) |
|----------|----------|------|----------|--------------------------|
| "하남시 선동에서 어젯밤 정전이 발생했습니다" | "하남시 선동" | `ADDRESS` | 주소 검색 | **2개**: BJDONG(`code`=법정동코드, `depth3Name`="선동") + HADONG(`code`=행정동코드, `depth3Name`="선동") |
| "판교JC에서 13중 추돌이 발생하였습니다" | "판교JC" | `LANDMARK` | 키워드 → 주소 검색 | **2개**: BJDONG + HADONG (depth3에서 분리, 좌표 공유) |
| "전국에 호우주의보가 발령되었습니다" | "전국" | `UNRESOLVABLE` | 없음 | **1개**: `code`="UNKNOWN", `regionType`=UNKNOWN, `coordinate`=null |
| "코엑스에서 싱크홀이 발생하였습니다" | "코엑스" | `LANDMARK` | 키워드 → 주소 검색 | **2개**: BJDONG(`depth3Name`="삼성동") + HADONG(`depth3Name`="삼성1동") |
| "전북 지역엔 오늘 황사가 심하겠습니다" | "전북" | `ADDRESS` | 주소 검색 | **2개**: BJDONG + HADONG (depth3Name 모두 null, depth1="전북특별자치도") |

### 도메인 모델 매핑

주소 검색 API 응답을 shared 모듈의 `Location` 도메인 모델로 변환한다.
하나의 API 응답에서 **법정동(BJDONG)과 행정동(HADONG)이 동시에 존재**할 수 있으며, 이 경우 **2개의 `Location` 객체**가 생성된다.

#### 법정동 / 행정동 분리 규칙

Kakao 주소 API 응답의 `address` 객체에는 `b_code`(법정동 코드)와 `h_code`(행정동 코드)가 함께 포함될 수 있다.
법정동과 행정동은 대체로 `depth3`(읍/면/동) 수준에서 갈리며, `depth1`(시/도)과 `depth2`(시/군/구)는 공유된다.

```
Kakao address 객체 필드               →  분리 기준
──────────────────────────────────────────────────────
b_code (법정동 코드)                   → 존재 시 BJDONG Location 생성
  + region_3depth_name (법정동명)      →   Address.depth3Name
  + region_1depth_name                →   Address.depth1Name (공유)
  + region_2depth_name                →   Address.depth2Name (공유)

h_code (행정동 코드)                   → 존재 시 HADONG Location 생성
  + region_3depth_h_name (행정동명)    →   Address.depth3Name
  + region_1depth_name                →   Address.depth1Name (공유)
  + region_2depth_name                →   Address.depth2Name (공유)

documents[].y (위도)                   →  Coordinate.lat (공유)
documents[].x (경도)                   →  Coordinate.lon (공유)
LLM이 추출한 원본 표현                 →  Address.addressName (공유)
```

#### 매핑 예시

"하남시 선동" 주소 검색 시 API 응답에 `b_code="4145010200"`, `h_code="4145069000"` 가 모두 존재하면:

| # | RegionType | code | depth3Name | addressName |
|---|------------|------|------------|-------------|
| 1 | BJDONG | 4145010200 | 선동 (region_3depth_name) | 하남시 선동 |
| 2 | HADONG | 4145069000 | 선동 (region_3depth_h_name) | 하남시 선동 |

"전북" 주소 검색 시 depth3 자체가 없지만, `b_code`와 `h_code` 모두 존재하면 2개의 Location이 생성된다 (depth3Name은 둘 다 null).

**주의**: `Address.addressName`에는 Kakao API가 반환한 주소가 아니라, **LLM이 뉴스 기사에서 추출한 원본 표현**이 저장된다 (예: "하남시 선동", "판교JC", "코엑스", "전국").

### 호출 전략 요약

```
LLM 위치 추출 (유형 분류 포함)
    ↓
┌─────────────────────────────────────────────────────┐
│ ADDRESS 유형                                         │
│   1차: 주소 검색 API (query=추출된 주소)              │
│   fallback: 키워드 장소 검색 API → 주소 검색 API     │
├─────────────────────────────────────────────────────┤
│ LANDMARK 유형                                        │
│   1차: 키워드 장소 검색 API (query=추출된 키워드)     │
│        → 획득한 address_name으로 주소 검색 API 호출  │
│   fallback: 주소 검색 API 직접 호출                  │
├─────────────────────────────────────────────────────┤
│ UNRESOLVABLE 유형                                    │
│   API 호출 없음                                      │
│   addressName만 저장, coordinate=null                │
└─────────────────────────────────────────────────────┘
    ↓
모든 유형 공통: 최종 실패 시
Location(coordinate=null, Address(addressName=원본표현, code="UNKNOWN", ...))
```
