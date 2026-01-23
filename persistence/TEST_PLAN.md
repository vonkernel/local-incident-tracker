# Persistence 모듈 테스트 계획

## 📋 개요

- **Mapper 테스트**: 단위 테스트 (Unit Test) - 도메인 ↔ 엔티티 매핑 검증
- **Adapter 테스트**: JPA 통합 테스트 - 데이터베이스 상호작용 검증

### 도메인 모델 구조 (실제 코드 기반)

#### 도메인 모델 (shared 모듈, immutable data class)
```
Article(
    articleId: String,          ← @Id (PK)
    originId: String,
    sourceId: String,
    writtenAt: Instant,         ← from ZonedDateTime (시스템 기본 시간대)
    modifiedAt: Instant,        ← from ZonedDateTime (시스템 기본 시간대)
    title: String,              ← TEXT column
    content: String,            ← TEXT column
    sourceUrl: String?          ← nullable
)

AnalysisResult(
    articleId: String,
    incidentTypes: Set<IncidentType>,   ← 다중 (via IncidentTypeMappingEntity)
    urgency: Urgency,                   ← 단일 (via UrgencyMappingEntity)
    keywords: List<Keyword>,            ← 다중 (via ArticleKeywordEntity)
    locations: List<Location>           ← 다중 (via AddressMappingEntity → AddressEntity)
)

Location(
    coordinate: Coordinate,             ← 1:1 (via AddressCoordinateEntity @MapsId)
    address: Address
)

Address(
    regionType: RegionType,             ← enum (B/H/U)
    code: String,                       ← Location API로부터
    addressName: String,
    depth1Name: String?,                ← nullable (시/도)
    depth2Name: String?,                ← nullable (시/군/구)
    depth3Name: String?                 ← nullable (읍/면/동)
)

Coordinate(lat: Double, lon: Double)
Urgency(name: String, level: Int)
IncidentType(code: String, name: String)
Keyword(keyword: String, priority: Int)
RegionType(code: String) - enum: BJDONG("B"), HADONG("H"), UNKNOWN("U")
```

#### 엔티티 구조 (persistence 모듈, mutable var)
```
ArticleEntity(
    articleId: String @Id,
    originId, sourceId: String,
    writtenAt, modifiedAt: ZonedDateTime,
    title, content: String (TEXT),
    sourceUrl: String?,
    createdAt, updatedAt: ZonedDateTime (audit)
)

AnalysisResultEntity(
    id: Long @GeneratedValue,
    article: ArticleEntity? @ManyToOne(LAZY),    ← unique=true
    urgencyMapping: UrgencyMappingEntity? @OneToOne(LAZY, mappedBy)
    incidentTypeMappings: MutableSet @OneToMany(LAZY)
    addressMappings: MutableSet @OneToMany(LAZY)
    keywords: MutableSet @OneToMany(LAZY)
    createdAt: ZonedDateTime
)

AddressEntity(
    id: Long @GeneratedValue,
    regionType: String (1 char),           ← UK: region_type + code
    code: String,
    addressName: String (500 chars),
    depth1~3Name: String?,
    coordinate: AddressCoordinateEntity? @OneToOne(LAZY, mappedBy)
)

AddressCoordinateEntity(
    id: Long @MapsId (addressId),
    address: AddressEntity @OneToOne,
    latitude, longitude: Double
)

UrgencyMappingEntity(
    id: Long @GeneratedValue,
    analysisResult: AnalysisResultEntity @OneToOne(LAZY),      ← unique=true
    urgencyType: UrgencyTypeEntity @ManyToOne(LAZY),
    setupAnalysisResult(analysisResult): bidirectional setter
)

IncidentTypeMappingEntity(
    id: Long @GeneratedValue,
    analysisResult: AnalysisResultEntity @ManyToOne(LAZY),     ← UK with incident_type_id
    incidentType: IncidentTypeEntity @ManyToOne(LAZY),
    setupAnalysisResult(analysisResult): bidirectional setter
)

AddressMappingEntity(
    id: Long @GeneratedValue,
    analysisResult: AnalysisResultEntity @ManyToOne(LAZY),     ← UK with address_id
    address: AddressEntity @ManyToOne(LAZY),
    setupAnalysisResult(analysisResult): bidirectional setter
)

ArticleKeywordEntity(
    id: Long @GeneratedValue,
    analysisResult: AnalysisResultEntity @ManyToOne(LAZY),
    keyword: String (500 chars),
    priority: Int,
    setupAnalysisResult(analysisResult): bidirectional setter
)

UrgencyTypeEntity(
    id: Long @GeneratedValue,
    name: String @Unique,                   ← DB 로드 전제
    level: Int @Unique
)

IncidentTypeEntity(
    id: Long @GeneratedValue,
    code: String @Unique,                   ← DB 로드 전제
    name: String
)

AnalysisResultOutboxEntity(
    id: Long @GeneratedValue,
    articleId: String @Unique,
    payload: String (JSONB),                ← JSON 직렬화된 AnalysisResult
    createdAt: ZonedDateTime
)
```

---

## 🧪 MAPPER 테스트 (단위 테스트)

### 1. ArticleMapper 테스트
**목적**: Article 도메인 모델과 ArticleEntity 간의 양방향 매핑 검증

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ 기본 필드 매핑 (articleId, originId, sourceId, title, content, sourceUrl)
  ├─ 시간 필드 변환 (ZonedDateTime → Instant)
  │  ├─ entity.writtenAt.toInstant() 호출 확인
  │  └─ entity.modifiedAt.toInstant() 호출 확인
  └─ null sourceUrl 처리

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ 기본 필드 매핑
  ├─ 시간 필드 변환 (Instant → ZonedDateTime)
  │  ├─ ZonedDateTime.ofInstant(domain.writtenAt, ZoneId.systemDefault())
  │  ├─ ZonedDateTime.ofInstant(domain.modifiedAt, ZoneId.systemDefault())
  │  └─ 시스템 기본 시간대 적용 확인
  └─ createdAt, updatedAt 자동 설정 (엔티티 기본값 사용)

✓ 양방향 변환 검증 (Round-trip)
  ├─ Entity → Domain → Entity 검증
  │  ├─ Instant 변환 후 ZonedDateTime으로 재변환 시 값 동일
  │  └─ 모든 필드 값 일치 확인
  ├─ Domain → Entity → Domain 검증
  │  └─ 모든 필드 값 일치 확인
  └─ 시간대 정확성 (milliseconds 단위)
     ├─ 다양한 ZoneId에서 정확한 Instant 변환
     └─ UTC 기준 일관성 검증
```

**데이터 시나리오**:
- **표준 기사**: 모든 필드 채워짐, sourceUrl 포함
- **sourceUrl이 null**: sourceUrl 미포함
- **오래된 기사**: 2020-01-01T00:00:00Z (시간대 변환 정확성)
- **최근 기사**: 현재 시간 (ZonedDateTime.now())

---

### 2. AnalysisResultMapper 테스트
**목적**: 복잡한 관계형 데이터 매핑 검증 (Lazy 로딩 + null 필터링)

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ articleId 추출 (nested entity.article!!.articleId)
  │  ├─ entity.article이 null이 아님 보장
  │  └─ articleId 정확히 추출
  ├─ incidentTypeMappings 변환 (.mapNotNull { it.incidentType })
  │  ├─ 빈 Set<IncidentTypeMappingEntity> (빈 Set 반환)
  │  ├─ 1개 incident type
  │  ├─ 5개 incident types
  │  └─ null incidentType인 매핑은 필터링
  ├─ urgencyMapping 변환 (entity.urgencyMapping!!.urgencyType!!)
  │  ├─ urgencyMapping이 null이 아님 보장
  │  ├─ urgencyType이 null이 아님 보장
  │  └─ UrgencyMapper.toDomainModel() 호출
  ├─ keywords 변환 (entity.keywords.map { ... })
  │  ├─ 빈 MutableSet (빈 List 반환)
  │  ├─ 1개 keyword
  │  ├─ 10개 keywords
  │  └─ KeywordMapper.toDomainModel() 호출
  └─ addressMappings 변환 (.mapNotNull { it.address }.map { LocationMapper... })
     ├─ 빈 MutableSet (빈 List 반환)
     ├─ 1개 location
     ├─ 5개 locations
     └─ null address인 매핑은 필터링

✓ null 체크 엣지 케이스
  ├─ entity.article == null (NPE 발생!)
  ├─ entity.urgencyMapping == null (NPE 발생!)
  ├─ entity.urgencyMapping!!.urgencyType == null (NPE 발생!)
  └─ 각각 !! operator로 인한 NPE 처리 (테스트에서 mocking으로 non-null 보장)

✓ 컬렉션 크기 검증
  ├─ incidentTypes.size == incidentTypeMappings.size
  ├─ keywords.size == entity.keywords.size
  └─ locations.size == addressMappings.size (null 필터링 후)
```

**데이터 시나리오**:
- **최소**: urgency + 1 incident type + 1 keyword + 1 location
- **중간**: urgency + 3 types + 3 keywords + 3 locations
- **모두 비어있음**: urgency만 있음 (incidentTypes.isEmpty(), keywords.isEmpty(), locations.isEmpty())

---

### 3. LocationMapper 테스트
**목적**: Location(주소 + 좌표) 매핑 + 양방향 관계 설정 검증

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ coordinate 매핑 (CoordinateMapper.toDomainModel(entity.coordinate!!))
  │  ├─ entity.coordinate가 null이 아님 보장
  │  └─ Coordinate(lat=latitude, lon=longitude) 생성
  ├─ regionType 매핑 (RegionType.entries.find { it.code == regionType })
  │  ├─ regionType = "B" → RegionType.BJDONG
  │  ├─ regionType = "H" → RegionType.HADONG
  │  ├─ regionType = "U" → RegionType.UNKNOWN
  │  └─ 미지의 코드 → RegionType.UNKNOWN (defaulting)
  └─ 주소 필드 (code, addressName, depth1~3Name)
     ├─ nullable depth 필드들 그대로 복사

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ CoordinateMapper.toPersistenceModel(domain.coordinate) → AddressCoordinateEntity 생성
  ├─ AddressEntity 생성 (regionType = domain.address.regionType.code)
  ├─ 양방향 관계 설정 (apply 블록)
  │  ├─ this.coordinate = coordinateEntity
  │  └─ coordinateEntity.address = this (@MapsId 검증)
  └─ 모든 주소 필드 저장

✓ 양방향 변환 (Round-trip)
  ├─ Entity → Domain → Entity
  │  ├─ RegionType enum 변환 후 다시 code로 변환 일치
  │  ├─ coordinate 정확도 유지
  │  └─ depth 필드 null/non-null 유지
  └─ Domain → Entity → Domain
     ├─ 모든 필드 값 일치
     └─ coordinate 정확도 유지
```

**데이터 시나리오**:
- **BJDONG**: regionType="B", depth 필드 채움
- **HADONG**: regionType="H", depth 일부 null
- **UNKNOWN**: regionType="U", depth 모두 null
- **미지 코드**: regionType="X" (존재하지 않는 코드, UNKNOWN으로 폴백)

---

### 4. CoordinateMapper 테스트
**목적**: 좌표(위도/경도) 매핑 + 정확도 검증

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ entity.latitude → coordinate.lat
  ├─ entity.longitude → coordinate.lon
  └─ Double 타입 정확도 유지 (IEEE 754)

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ domain.lat → entity.latitude
  ├─ domain.lon → entity.longitude
  └─ Double 정확도 유지

✓ 양방향 변환 (Round-trip)
  ├─ Entity → Domain → Entity 정확도 유지 (epsilon 비교)
  └─ Domain → Entity → Domain 정확도 유지 (epsilon 비교)

✓ 극단값 테스트
  ├─ 최대 위도: 90.0 (북극)
  ├─ 최소 위도: -90.0 (남극)
  ├─ 최대 경도: 180.0 (국제변경선 동쪽)
  ├─ 최소 경도: -180.0 (국제변경선 서쪽)
  ├─ 0.0 (적도, 자오선)
  └─ 고정밀: 37.49791234567890, 126.92701234567890
```

**데이터 시나리오**:
- 표준 좌표: 37.4979, 126.9270 (서울)
- 극단 좌표: 90.0, 180.0 / -90.0, -180.0
- 고정밀: 37.49791234567890, 126.92701234567890

---

### 5. UrgencyMapper 테스트
**목적**: 긴급도 매핑 검증 (단순 필드 복사)

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ entity.name → domain.name
  └─ entity.level → domain.level

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ domain.name → entity.name
  ├─ domain.level → entity.level
  └─ createdAt, updatedAt 자동 설정 (엔티티 기본값)

✓ 양방향 변환
  ├─ Entity → Domain → Entity 불변성
  └─ Domain → Entity → Domain 불변성
```

**데이터 시나리오**:
- 낮은 긴급도: Urgency("LOW", 1)
- 중간 긴급도: Urgency("MEDIUM", 2)
- 높은 긴급도: Urgency("HIGH", 3)

---

### 6. IncidentTypeMapper 테스트
**목적**: 사건 유형 매핑 검증 (단순 필드 복사)

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ entity.code → domain.code
  └─ entity.name → domain.name

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ domain.code → entity.code
  ├─ domain.name → entity.name
  └─ createdAt, updatedAt 자동 설정

✓ 양방향 변환
  ├─ Entity → Domain → Entity 불변성
  └─ Domain → Entity → Domain 불변성
```

**데이터 시나리오**:
- 산불: IncidentType("forest_fire", "산불")
- 태풍: IncidentType("typhoon", "태풍")
- 홍수: IncidentType("flood", "홍수")
- 특수문자: IncidentType("special-case_123", "특수케이스")

---

### 7. KeywordMapper 테스트
**목적**: 키워드 매핑 검증 (단순 필드 복사)

#### 테스트 케이스
```
✓ toDomainModel() - 엔티티 → 도메인 변환
  ├─ entity.keyword → domain.keyword
  └─ entity.priority → domain.priority

✓ toPersistenceModel() - 도메인 → 엔티티 변환
  ├─ domain.keyword → entity.keyword
  ├─ domain.priority → entity.priority
  └─ createdAt 자동 설정

✓ 양방향 변환
  ├─ Entity → Domain → Entity 불변성
  └─ Domain → Entity → Domain 불변성
```

**데이터 시나리오**:
- 높은 우선순위: Keyword("화재", 10)
- 중간 우선순위: Keyword("대피", 5)
- 낮은 우선순위: Keyword("예보", 1)
- 특수문자: Keyword("@#$%&", 3)
- 긴 문자열: Keyword("...1000+ chars...", 5)

---

### 8. AnalysisResultOutboxMapper 테스트
**목적**: ObjectMapper 기반 JSON 직렬화 검증

#### 테스트 케이스
```
✓ toPersistenceModel() - 도메인 → 아웃박스 엔티티
  ├─ articleId 저장
  ├─ objectMapper.writeValueAsString(analysisResult) 호출 확인
  ├─ payload 필드에 JSON 저장
  └─ createdAt 자동 설정 (엔티티 기본값)

✓ JSON 직렬화 정확성
  ├─ AnalysisResult.articleId 포함됨
  ├─ Set<IncidentType> 배열로 직렬화
  ├─ Urgency(name, level) 객체로 직렬화
  ├─ List<Keyword> 배열로 직렬화
  ├─ List<Location> → List<Address> + List<Coordinate> 중첩 직렬화
  └─ enum RegionType (e.g., "BJDONG") 문자열로 직렬화

✓ 역직렬화 가능성
  ├─ payload 문자열이 유효한 JSON
  ├─ ObjectMapper.readValue(payload, AnalysisResult::class.java) 성공
  └─ 역직렬화된 데이터 == 원본 데이터

✓ 특수 문자 처리
  ├─ 한글 키워드: "화재", "태풍", "홍수"
  ├─ 특수문자: "@#$%&*()"
  ├─ 줄바꿈: "\\n" 이스케이핑
  ├─ 따옴표: "\\"" 이스케이핑
  └─ 이모지: "🔥" (UTF-8)

✓ 크기 검증
  ├─ 최소 크기: urgency만 있는 경우
  ├─ 최대 크기: 모든 필드 채워진 경우 (1KB 이상)
  └─ 매우 큰 분석: 1000+ 키워드, 100+ 위치
```

**데이터 시나리오**:
- **최소 분석**: urgency만 있음 (incidentTypes, keywords, locations 모두 empty)
- **표준 분석**: urgency + 3 types + 5 keywords + 3 locations
- **최대 분석**: 모든 컬렉션 100개씩
- **특수 분석**: 한글, 특수문자, 이모지 포함

---

## 🗄️ ADAPTER 테스트 (JPA 통합 테스트)

### 테스트 환경 설정
```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = EMBEDDED)  // H2 in-memory DB 사용
@Import(ObjectMapperConfig::class)              // ObjectMapper 주입
class ArticleRepositoryAdapterTest {
    @Autowired lateinit var jpaRepository: JpaArticleRepository
    @Autowired lateinit var adapter: ArticleRepositoryAdapter
    @Autowired lateinit var entityManager: TestEntityManager
}
```

### 테스트 데이터 빌더 (모든 테스트 클래스에서 사용)
```kotlin
object TestFixtures {
    fun createArticle(
        articleId: String = "test-article-1",
        originId: String = "news-123",
        sourceId: String = "yonhapnews",
        writtenAt: Instant = Instant.now(),
        modifiedAt: Instant = Instant.now(),
        title: String = "테스트 기사",
        content: String = "테스트 내용",
        sourceUrl: String? = null
    ) = Article(articleId, originId, sourceId, writtenAt, modifiedAt, title, content, sourceUrl)

    fun createAnalysisResult(
        articleId: String = "test-article-1",
        incidentTypes: Set<IncidentType> = setOf(IncidentType("fire", "산불")),
        urgency: Urgency = Urgency("HIGH", 3),
        keywords: List<Keyword> = listOf(Keyword("화재", 10)),
        locations: List<Location> = listOf(createLocation())
    ) = AnalysisResult(articleId, incidentTypes, urgency, keywords, locations)

    fun createLocation() = Location(
        coordinate = Coordinate(37.4979, 126.9270),
        address = createAddress()
    )

    fun createAddress() = Address(
        regionType = RegionType.BJDONG,
        code = "11110",
        addressName = "서울특별시 강남구 테헤란로",
        depth1Name = "서울특별시",
        depth2Name = "강남구",
        depth3Name = "강남동"
    )

    // ... 기타 builders
}
```

---

### 1. ArticleRepositoryAdapter 테스트
**목적**: Article 저장/조회 및 필터링 기능 검증

#### 테스트 케이스
```
✓ save() - 단일 Article 저장
  ├─ ArticleMapper.toPersistenceModel() 호출
  ├─ jpaRepository.save(entity) 호출
  ├─ ArticleMapper.toDomainModel() 호출
  ├─ 저장 후 반환값이 원본과 동일
  ├─ DB에 articleId @Id로 Insert됨
  └─ 재조회 시 동일한 데이터 검증

✓ saveAll() - 다중 Article 저장
  ├─ 빈 컬렉션 처리 (List.empty() → List.empty() 반환)
  ├─ 단일 항목 리스트 (1개)
  ├─ 다중 항목 리스트 (5개, 10개, 100개)
  ├─ 모든 항목이 정확히 저장됨 (INSERT 개수 확인)
  ├─ 각 항목의 articleId가 고유함
  └─ 각 항목의 매핑 정확성 검증

✓ filterNonExisting() - 존재하지 않는 기사 ID 필터링
  ├─ JpaArticleRepository.findExistingIds(articleIds) 호출
  ├─ 모두 존재하지 않음 (모든 ID 반환)
  ├─ 모두 존재함 (빈 List 반환)
  ├─ 혼합 (일부는 존재, 일부는 미존재)
  │  ├─ 3개 중 1개 존재 → 2개 반환
  │  └─ 10개 중 7개 존재 → 3개 반환
  ├─ 빈 컬렉션 (빈 List 반환)
  └─ 대량 ID (1000개) 성능 검증

✓ 트랜잭션 처리
  ├─ @Transactional 없음 (adapter는 non-transactional)
  ├─ 각 save()는 자동 commit
  ├─ 여러 save()는 각각 독립적인 트랜잭션
  └─ 장 조회도 새로운 트랜잭션에서 실행
```

**데이터 시나리오**:
```
1. 표준 기사: 모든 필드 채움
2. sourceUrl이 null: sourceUrl 제외
3. 오래된 기사: writtenAt = 2020-01-01T00:00:00Z
4. 최근 기사: writtenAt = 현재 시간
5. 긴 제목: title = "...500+ 글자..."
6. 긴 내용: content = "...10000+ 글자..."
```

**DB 상태 검증**:
```kotlin
val saved = adapter.save(article)
val fromDb = jpaRepository.findById(saved.articleId).orElse(null)
assertThat(fromDb).isNotNull()
assertThat(fromDb!!.title).isEqualTo(article.title)
assertThat(fromDb.writtenAt).isEqualTo(article.writtenAt)
```

---

### 2. AnalysisResultRepositoryAdapter 테스트
**목적**: 복잡한 트랜잭션 + Outbox 패턴 + 양방향 관계 검증

#### 테스트 케이스

##### Part 1: 기본 save() 흐름
```
✓ save() - AnalysisResult 저장 (트랜잭션)
  ├─ buildAnalysisResultEntity(analysisResult) 호출
  ├─ jpaAnalysisResultRepository.save(entity) 호출
  ├─ jpaAnalysisResultOutboxRepository.save(outboxEntity) 호출 (같은 트랜잭션)
  ├─ AnalysisResultMapper.toDomainModel(savedEntity) 호출
  ├─ 반환값이 원본 analysisResult와 동일 (모든 필드)
  └─ @Transactional 보증: 둘 다 저장되거나 모두 롤백
```

##### Part 2: buildAnalysisResultEntity() - Urgency 로드
```
✓ loadUrgency() - 긴급도 로드
  ├─ jpaUrgencyTypeRepository.findByName(urgency.name) 호출
  ├─ 존재하는 urgency ("HIGH", 3)
  │  ├─ UrgencyTypeEntity 반환됨
  │  └─ createUrgencyMapping()으로 매핑 엔티티 생성
  ├─ 존재하지 않는 urgency ("NONEXISTENT")
  │  └─ IllegalArgumentException 발생 → 전체 롤백
  └─ 여러 테스트 케이스:
     ├─ "LOW" (level 1)
     ├─ "MEDIUM" (level 2)
     └─ "HIGH" (level 3)
```

##### Part 3: buildAnalysisResultEntity() - IncidentTypes 로드
```
✓ loadIncidentTypes() - 사건 유형들 로드
  ├─ jpaIncidentTypeRepository.findByCodes(codes) 호출
  ├─ 0개 incident types (빈 Set)
  │  └─ 빈 List 반환 → incidentTypeMappings 비어있음
  ├─ 1개 incident type
  │  ├─ findByCodes(["fire"]) → [IncidentTypeEntity("fire", "산불")]
  │  └─ createIncidentTypeMappings() 생성
  ├─ 5개 incident types
  │  ├─ ["fire", "typhoon", "flood", "earthquake", "landslide"]
  │  └─ 모두 로드됨
  ├─ 부분 로드 (일부 존재하지 않음)
  │  ├─ ["fire", "nonexistent", "typhoon"]
  │  ├─ findByCodes() → [fire, typhoon] 반환 (2개만)
  │  └─ 실제 분석 결과에는 3개 필요하지만 2개만 존재 (처리 방식 확인 필요)
  └─ 순서: 로드된 순서로 MutableSet에 추가됨
```

##### Part 4: buildAnalysisResultEntity() - Addresses 로드 또는 생성
```
✓ loadOrCreateAddresses() - 주소 로드 또는 생성
  ├─ 기존 address 재사용
  │  ├─ jpaAddressRepository.findByRegionTypeAndCode("B", "11110")
  │  ├─ 존재함 → 재사용
  │  └─ unique constraint (region_type + code) 검증
  ├─ 새로운 address 생성
  │  ├─ 존재하지 않음 → jpaAddressRepository.save(addressEntity)
  │  ├─ AddressEntity 저장
  │  ├─ AddressCoordinateEntity @MapsId로 생성
  │  └─ 반환된 addressEntity 사용
  ├─ 혼합 (일부 기존, 일부 신규)
  │  ├─ 5개 locations 중 2개는 기존, 3개는 신규
  │  └─ 각각 올바르게 처리됨
  └─ 0개 locations (빈 List)
     └─ 빈 List 반환 → addressMappings 비어있음
```

##### Part 5: buildAnalysisResultEntity() - Keywords 생성
```
✓ createKeywords() - 키워드 생성
  ├─ KeywordMapper.toPersistenceModel() 호출
  ├─ 0개 keywords (빈 List)
  │  └─ 빈 List 반환
  ├─ 1개 keyword
  │  ├─ ArticleKeywordEntity 생성
  │  └─ keyword="화재", priority=10
  ├─ 10개 keywords
  │  ├─ 모두 ArticleKeywordEntity 생성
  │  └─ 우선순위 순서대로
  └─ 특수문자 키워드
     ├─ "@#$%&", "\\n", "\\"" 등 이스케이핑 필요한 문자
     └─ 정확히 저장됨
```

##### Part 6: 양방향 관계 설정
```
✓ setupAnalysisResult() - 모든 매핑 엔티티의 양방향 관계 설정
  ├─ UrgencyMappingEntity.setupAnalysisResult(analysisResult)
  │  ├─ this.analysisResult = analysisResult
  │  └─ analysisResult.urgencyMapping = this (양방향)
  ├─ IncidentTypeMappingEntity.setupAnalysisResult(analysisResult) (다중)
  │  ├─ this.analysisResult = analysisResult
  │  └─ analysisResult.incidentTypeMappings.add(this)
  ├─ AddressMappingEntity.setupAnalysisResult(analysisResult) (다중)
  │  ├─ this.analysisResult = analysisResult
  │  └─ analysisResult.addressMappings.add(this)
  └─ ArticleKeywordEntity.setupAnalysisResult(analysisResult) (다중)
     ├─ this.analysisResult = analysisResult
     └─ analysisResult.keywords.add(this)
```

##### Part 7: Transactional 트랜잭션 보증
```
✓ @Transactional 보증
  ├─ 모든 INSERT가 한 트랜잭션으로 실행
  ├─ 성공 시나리오: 모든 엔티티 저장됨
  │  ├─ AnalysisResultEntity INSERT
  │  ├─ UrgencyMappingEntity INSERT
  │  ├─ IncidentTypeMappingEntity INSERT (N개)
  │  ├─ AddressMappingEntity INSERT (N개)
  │  ├─ ArticleKeywordEntity INSERT (N개)
  │  ├─ AnalysisResultOutboxEntity INSERT
  │  └─ 모두 COMMIT
  ├─ 실패 시나리오: 전체 ROLLBACK
  │  ├─ Urgency 존재 안 함 → IllegalArgumentException
  │  │  └─ 모든 INSERT 롤백
  │  ├─ IncidentType 일부 미존재 (현재 처리 방식 확인 필요)
  │  └─ Outbox INSERT 실패 → 전체 롤백
  └─ 격리 수준 (READ_COMMITTED)
     ├─ Dirty read 없음
     ├─ Non-repeatable read 가능 (일반적)
     └─ Phantom read 가능 (일반적)
```

##### Part 8: 데이터 무결성
```
✓ Foreign Key 제약 조건
  ├─ analysis_result.article_id → article(article_id) FK
  │  └─ 유효한 article 참조
  ├─ urgency_mapping.analysis_result_id → analysis_result(id) FK
  │  └─ 유효한 analysis_result 참조
  ├─ urgency_mapping.urgency_type_id → urgency_type(id) FK
  │  └─ 유효한 urgency_type 참조
  ├─ incident_type_mapping.analysis_result_id FK
  ├─ incident_type_mapping.incident_type_id FK
  ├─ address_mapping.analysis_result_id FK
  ├─ address_mapping.address_id FK
  └─ article_keywords.analysis_result_id FK

✓ Unique 제약 조건
  ├─ analysis_result.article_id (unique=true, 1:1 관계)
  │  └─ 같은 articleId로 2개 분석 결과 저장 불가
  ├─ incident_type_mapping(analysis_result_id, incident_type_id) UK
  │  └─ 같은 분석에 같은 유형 2번 매핑 불가
  ├─ address_mapping(analysis_result_id, address_id) UK
  │  └─ 같은 분석에 같은 주소 2번 매핑 불가
  ├─ address(region_type, code) UK
  │  └─ 같은 지역타입+코드 중복 불가
  └─ urgency_mapping(analysis_result_id) unique=true (implicit)
```

##### Part 9: Outbox 패턴 검증
```
✓ AnalysisResultOutboxEntity 저장 (CDC 준비)
  ├─ AnalysisResultOutboxMapper.toPersistenceModel(analysisResult)
  │  ├─ objectMapper.writeValueAsString(analysisResult)
  │  └─ AnalysisResultOutboxEntity 생성
  ├─ articleId 저장 (검색용)
  ├─ payload JSON 저장 (직렬화됨)
  │  ├─ {"articleId":"...", "incidentTypes":[...], "urgency":{...}, ...}
  │  └─ 유효한 JSON (역직렬화 가능)
  ├─ createdAt 자동 설정 (감사용)
  └─ Debezium CDC 트리거 준비
     ├─ insert 이벤트 감지
     └─ analysis-events Kafka 토픽으로 발행
```

##### Part 10: 엣지 케이스
```
✓ 최소 분석 결과
  ├─ urgency: 1개
  ├─ incidentTypes: 0개 (빈 Set)
  ├─ keywords: 0개 (빈 List)
  └─ locations: 0개 (빈 List)

✓ 최대 분석 결과
  ├─ urgency: 1개
  ├─ incidentTypes: 100개 (매핑 100개 생성)
  ├─ keywords: 100개 (엔티티 100개 생성)
  └─ locations: 100개 (매핑 + Address 100개)

✓ 혼합 주소
  ├─ 기존 address 2개 (DB에서 로드)
  ├─ 신규 address 3개 (새로 생성)
  └─ 총 5개 AddressMappingEntity 생성

✓ 에러 시나리오
  ├─ 존재하지 않는 urgency name
  │  └─ IllegalArgumentException("Urgency not found: ...")
  ├─ 부분 미존재 incident types
  │  └─ findByCodes()는 존재하는 것만 반환 (처리 방식 확인 필요)
  └─ DB 제약 조건 위반
     ├─ 중복된 (analysis_result_id, incident_type_id)
     ├─ 중복된 article_id (unique 제약)
     └─ DataIntegrityViolationException
```

**데이터 시나리오 예제**:
```kotlin
// 1. 최소 분석
createAnalysisResult(
    articleId = "art-1",
    incidentTypes = emptySet(),      // 0개
    urgency = Urgency("HIGH", 3),    // 필수
    keywords = emptyList(),          // 0개
    locations = emptyList()          // 0개
)

// 2. 표준 분석
createAnalysisResult(
    articleId = "art-2",
    incidentTypes = setOf(
        IncidentType("fire", "산불"),
        IncidentType("typhoon", "태풍")
    ),
    urgency = Urgency("HIGH", 3),
    keywords = listOf(
        Keyword("화재", 10),
        Keyword("대피", 8),
        Keyword("경고", 5)
    ),
    locations = listOf(
        Location(...coordinate..., Address("BJDONG", "11110", ...)),
        Location(...coordinate..., Address("HADONG", "11120", ...))
    )
)

// 3. 기존 Address 재사용
// 사전에 DB에 주소 등록 후
createAnalysisResult(
    articleId = "art-3",
    locations = listOf(
        Location(...coordinate..., Address("BJDONG", "11110", ...))  // DB에서 로드
    )
)

// 4. 특수문자 포함
createAnalysisResult(
    articleId = "art-4",
    keywords = listOf(
        Keyword("@#$%&", 5),
        Keyword("한글테스트", 8),
        Keyword("🔥emoji", 3)
    )
)
```

---

## 📊 테스트 실행 순서

### Phase 1: Mapper 단위 테스트
```bash
# 1. 기본 Mapper (의존성 없음)
./gradlew persistence:test --tests "*CoordinateMapperTest"
./gradlew persistence:test --tests "*UrgencyMapperTest"
./gradlew persistence:test --tests "*IncidentTypeMapperTest"
./gradlew persistence:test --tests "*KeywordMapperTest"

# 2. 단순 Mapper (다른 Mapper 위임)
./gradlew persistence:test --tests "*ArticleMapperTest"
./gradlew persistence:test --tests "*LocationMapperTest"

# 3. 복잡 Mapper (여러 Mapper 위임)
./gradlew persistence:test --tests "*AnalysisResultMapperTest"
./gradlew persistence:test --tests "*AnalysisResultOutboxMapperTest"
```

### Phase 2: Adapter JPA 통합 테스트
```bash
# 1. 단순 Adapter
./gradlew persistence:test --tests "*ArticleRepositoryAdapterTest"

# 2. 복잡 Adapter
./gradlew persistence:test --tests "*AnalysisResultRepositoryAdapterTest"
```

### Phase 3: 전체 통합 테스트
```bash
./gradlew persistence:test
```

---

## 🛠️ 테스트 유틸리티

### Test Fixture Builders (테스트 데이터 생성)

위치: `persistence/src/test/kotlin/com/vonkernel/lit/persistence/TestFixtures.kt`

```kotlin
object TestFixtures {
    // Article 빌더
    fun createArticle(
        articleId: String = "test-article-${System.nanoTime()}",
        originId: String = "news-${Random.nextInt()}",
        sourceId: String = "yonhapnews",
        writtenAt: Instant = Instant.now(),
        modifiedAt: Instant = Instant.now(),
        title: String = "테스트 기사 제목",
        content: String = "테스트 기사 본문",
        sourceUrl: String? = "https://example.com/article"
    ) = Article(articleId, originId, sourceId, writtenAt, modifiedAt, title, content, sourceUrl)

    fun createArticleEntity(
        articleId: String = "test-article-${System.nanoTime()}",
        title: String = "테스트 기사",
        content: String = "테스트 내용",
        sourceUrl: String? = null
    ) = ArticleEntity(
        articleId = articleId,
        originId = "news-123",
        sourceId = "yonhapnews",
        writtenAt = ZonedDateTime.now(),
        modifiedAt = ZonedDateTime.now(),
        title = title,
        content = content,
        sourceUrl = sourceUrl
    )

    // AnalysisResult 빌더
    fun createAnalysisResult(
        articleId: String = "test-article-1",
        incidentTypes: Set<IncidentType> = setOf(
            IncidentType("fire", "산불"),
            IncidentType("typhoon", "태풍")
        ),
        urgency: Urgency = Urgency("HIGH", 3),
        keywords: List<Keyword> = listOf(
            Keyword("화재", 10),
            Keyword("대피", 8)
        ),
        locations: List<Location> = listOf(createLocation())
    ) = AnalysisResult(articleId, incidentTypes, urgency, keywords, locations)

    // Coordinate 빌더
    fun createCoordinate(
        lat: Double = 37.4979,
        lon: Double = 126.9270
    ) = Coordinate(lat, lon)

    fun createCoordinateEntity(
        latitude: Double = 37.4979,
        longitude: Double = 126.9270
    ) = AddressCoordinateEntity(
        latitude = latitude,
        longitude = longitude
    )

    // Address 빌더
    fun createAddress(
        regionType: RegionType = RegionType.BJDONG,
        code: String = "11110",
        addressName: String = "서울특별시 강남구",
        depth1Name: String? = "서울특별시",
        depth2Name: String? = "강남구",
        depth3Name: String? = "강남동"
    ) = Address(regionType, code, addressName, depth1Name, depth2Name, depth3Name)

    fun createAddressEntity(
        regionType: String = "B",
        code: String = "11110",
        addressName: String = "서울특별시 강남구"
    ) = AddressEntity(
        regionType = regionType,
        code = code,
        addressName = addressName,
        depth1Name = "서울특별시",
        depth2Name = "강남구"
    )

    // Location 빌더
    fun createLocation(
        coordinate: Coordinate = createCoordinate(),
        address: Address = createAddress()
    ) = Location(coordinate, address)

    // Urgency 빌더
    fun createUrgency(name: String = "HIGH", level: Int = 3) = Urgency(name, level)

    fun createUrgencyEntity(name: String = "HIGH", level: Int = 3) = UrgencyTypeEntity(
        name = name,
        level = level
    )

    // IncidentType 빌더
    fun createIncidentType(code: String = "fire", name: String = "산불") = IncidentType(code, name)

    fun createIncidentTypeEntity(code: String = "fire", name: String = "산불") = IncidentTypeEntity(
        code = code,
        name = name
    )

    // Keyword 빌더
    fun createKeyword(keyword: String = "화재", priority: Int = 10) = Keyword(keyword, priority)

    fun createKeywordEntity(keyword: String = "화재", priority: Int = 10) = ArticleKeywordEntity(
        keyword = keyword,
        priority = priority
    )

    // 대량 데이터 생성
    fun createIncidentTypes(count: Int) = (1..count).map { i ->
        IncidentType("type_$i", "타입_$i")
    }.toSet()

    fun createKeywords(count: Int) = (1..count).map { i ->
        Keyword("keyword_$i", count - i)
    }

    fun createLocations(count: Int) = (1..count).map { i ->
        Location(
            coordinate = Coordinate(37.4979 + i * 0.001, 126.9270 + i * 0.001),
            address = Address(
                regionType = if (i % 2 == 0) RegionType.BJDONG else RegionType.HADONG,
                code = "1111${i}",
                addressName = "주소_$i"
            )
        )
    }
}
```

### Database 상태 검증 Helper

위치: `persistence/src/test/kotlin/com/vonkernel/lit/persistence/DbAssertions.kt`

```kotlin
object DbAssertions {
    fun assertArticleInDatabase(
        jpaRepository: JpaArticleRepository,
        article: Article
    ) {
        val fromDb = jpaRepository.findById(article.articleId).orElse(null)
        assertThat(fromDb).isNotNull
        assertThat(fromDb!!.title).isEqualTo(article.title)
        assertThat(fromDb.content).isEqualTo(article.content)
        assertThat(fromDb.originId).isEqualTo(article.originId)
        assertThat(fromDb.sourceId).isEqualTo(article.sourceId)
        assertThat(fromDb.sourceUrl).isEqualTo(article.sourceUrl)
        // 시간 필드는 millisecond 단위로 비교 (ZoneId 변환으로 인한 미세 오차)
    }

    fun assertAnalysisResultInDatabase(
        jpaRepository: JpaAnalysisResultRepository,
        analysisResult: AnalysisResult
    ) {
        val fromDb = jpaRepository.findAll()
            .find { it.article?.articleId == analysisResult.articleId }
        assertThat(fromDb).isNotNull()
        assertThat(fromDb!!.article?.articleId).isEqualTo(analysisResult.articleId)
        assertThat(fromDb.urgencyMapping).isNotNull()
        assertThat(fromDb.incidentTypeMappings.size).isEqualTo(analysisResult.incidentTypes.size)
        assertThat(fromDb.keywords.size).isEqualTo(analysisResult.keywords.size)
        assertThat(fromDb.addressMappings.size).isEqualTo(analysisResult.locations.size)
    }

    fun assertOutboxCreated(
        jpaRepository: JpaAnalysisResultOutboxRepository,
        articleId: String
    ) {
        val outbox = jpaRepository.findAll()
            .find { it.articleId == articleId }
        assertThat(outbox).isNotNull()
        assertThat(outbox!!.payload).isNotBlank()
        // JSON 유효성 검증 (ObjectMapper 사용 가능)
    }
}
```

---

## ✅ 완료 기준

### Mapper 테스트 (8개 매퍼, ~40-50개 테스트)
- [ ] CoordinateMapper (6개 테스트)
  - toDomainModel() 기본 + 극단값
  - toPersistenceModel() 기본 + 극단값
  - Round-trip 정확도 검증
- [ ] UrgencyMapper (6개)
- [ ] IncidentTypeMapper (6개)
- [ ] KeywordMapper (6개)
- [ ] ArticleMapper (8개)
  - ZonedDateTime ↔ Instant 변환 검증
- [ ] LocationMapper (10개)
  - RegionType enum 매핑 검증
  - 양방향 관계 설정 검증
- [ ] AnalysisResultMapper (10개)
  - null 체크 (!! operator)
  - mapNotNull() 필터링
  - 컬렉션 크기 검증
- [ ] AnalysisResultOutboxMapper (8개)
  - JSON 직렬화/역직렬화
  - 특수문자 처리

**통과 조건**:
- 모든 테스트 PASS
- 코드 커버리지 ≥ 95% (매퍼는 단순하므로 높은 커버리지 가능)

### Adapter 테스트 (2개 어댑터, ~60-70개 테스트)

#### ArticleRepositoryAdapter (15-20개 테스트)
- [ ] save() - 3개
  - 기본 저장 + DB 검증
  - sourceUrl null 처리
  - 시간대 변환 정확성
- [ ] saveAll() - 4개
  - 빈 List, 단일 항목, 다중 항목 (5, 100개)
  - 개수 검증
- [ ] filterNonExisting() - 5개
  - 모두 미존재, 모두 존재, 혼합
  - 빈 List
  - 대량 ID (1000개)
- [ ] 트랜잭션 - 2개
  - 자동 commit 검증
  - 각 save() 독립성

#### AnalysisResultRepositoryAdapter (40-50개 테스트)
- [ ] save() 통합 흐름 - 5개
  - AnalysisResultEntity + OutboxEntity 동시 저장
  - @Transactional 보증
  - 전체 COMMIT/ROLLBACK
- [ ] loadUrgency() - 5개
  - 존재하는 urgency 3가지 (LOW, MEDIUM, HIGH)
  - 존재하지 않는 urgency → IllegalArgumentException
  - null 처리
- [ ] loadIncidentTypes() - 8개
  - 0개, 1개, 5개, 100개
  - 부분 미존재 (findByCodes 동작 확인)
  - 순서 검증
- [ ] loadOrCreateAddresses() - 8개
  - 기존 address 재사용 (UK constraint 검증)
  - 신규 address 생성
  - 혼합 (기존 2개 + 신규 3개)
  - 0개 locations
- [ ] createKeywords() - 4개
  - 0개, 1개, 10개 keywords
  - 특수문자, 한글, 이모지
- [ ] setupAnalysisResult() - 6개
  - UrgencyMappingEntity 양방향 설정
  - IncidentTypeMappingEntity 양방향 설정
  - AddressMappingEntity 양방향 설정
  - ArticleKeywordEntity 양방향 설정
- [ ] 데이터 무결성 - 8개
  - FK 제약 조건 검증 (5개)
  - UK 제약 조건 검증 (3개)
- [ ] 아웃박스 패턴 - 5개
  - JSON 직렬화 정확성
  - articleId 저장
  - 역직렬화 가능성
  - CDC 준비 상태

**통과 조건**:
- 모든 테스트 PASS
- 코드 커버리지 ≥ 90%
- 통합 테스트이므로 복잡한 로직 전부 커버

---

## 📊 테스트 실행 계획

### 1단계: 테스트 클래스 생성 및 구조화
```
persistence/src/test/kotlin/
├── com/vonkernel/lit/persistence/
│   ├── TestFixtures.kt              ← 공통 테스트 데이터
│   ├── DbAssertions.kt              ← 공통 검증 헬퍼
│   ├── mapper/
│   │   ├── ArticleMapperTest.kt
│   │   ├── AnalysisResultMapperTest.kt
│   │   ├── LocationMapperTest.kt
│   │   ├── CoordinateMapperTest.kt
│   │   ├── UrgencyMapperTest.kt
│   │   ├── IncidentTypeMapperTest.kt
│   │   ├── KeywordMapperTest.kt
│   │   └── AnalysisResultOutboxMapperTest.kt
│   └── adapter/
│       ├── ArticleRepositoryAdapterTest.kt
│       └── AnalysisResultRepositoryAdapterTest.kt
└── resources/
    └── application-test.yml         ← H2 DB 설정
```

### 2단계: Phase 1 - Mapper 단위 테스트 작성
```bash
# 기본 매퍼 먼저 (의존성 없음)
./gradlew persistence:test --tests "*CoordinateMapperTest"
./gradlew persistence:test --tests "*UrgencyMapperTest"
./gradlew persistence:test --tests "*IncidentTypeMapperTest"
./gradlew persistence:test --tests "*KeywordMapperTest"

# 단순 매퍼
./gradlew persistence:test --tests "*ArticleMapperTest"
./gradlew persistence:test --tests "*LocationMapperTest"

# 복잡 매퍼
./gradlew persistence:test --tests "*AnalysisResultMapperTest"
./gradlew persistence:test --tests "*AnalysisResultOutboxMapperTest"

# Phase 1 전체
./gradlew persistence:test --tests "*Mapper*Test"
```

### 3단계: Phase 2 - Adapter 통합 테스트 작성
```bash
# 단순 어댑터
./gradlew persistence:test --tests "*ArticleRepositoryAdapterTest"

# 복잡 어댑터 (Outbox 패턴 검증 중요!)
./gradlew persistence:test --tests "*AnalysisResultRepositoryAdapterTest"

# Phase 2 전체
./gradlew persistence:test --tests "*RepositoryAdapterTest"
```

### 4단계: 전체 테스트 및 커버리지
```bash
# 전체 실행
./gradlew persistence:test

# 커버리지 리포트
./gradlew persistence:jacocoTestReport
open persistence/build/reports/jacoco/test/html/index.html
```

---

## 📝 주의사항

### Mapper 테스트
- ✅ 단순 필드 복사 매퍼는 간단 (Urgency, IncidentType, Keyword, Coordinate)
- ⚠️ ArticleMapper: `ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())` 시스템 시간대 의존
  - 테스트 시 시스템 시간대 영향 받을 수 있음
  - `@ExtendWith(MockedStaticExtension::class)` 고려
- ⚠️ LocationMapper: `RegionType.entries.find { it.code == code }` elvis operator로 UNKNOWN 폴백
  - 미지의 코드는 UNKNOWN으로 변환됨
- ⚠️ AnalysisResultMapper: `!! operator` 여러 개 사용
  - null이면 NPE 발생 (테스트에서는 non-null 보장해야 함)

### Adapter 테스트
- ✅ @DataJpaTest로 H2 in-memory DB 자동 생성
- ✅ @Import(ObjectMapperConfig::class) 필수 (JSON 직렬화용)
- ⚠️ AnalysisResultRepositoryAdapter.save()는 @Transactional
  - 트랜잭션 테스트는 별도 설정 필요
- ⚠️ LAZY 로딩: Proxy 엔티티 일부 필드는 접근 불가
  - `entityManager.flush()` 또는 `findById().get()` 후 접근
- ⚠️ 기존 Address 재사용 테스트
  - 부딩에 findByRegionTypeAndCode() 호출 → 기존 주소 반환
  - UK constraint (region_type + code) 검증 필수

---

## 📈 예상 테스트 커버리지

| 모듈 | 클래스 | 메서드 | 예상 커버리지 |
|------|--------|--------|-------------|
| Mapper | CoordinateMapper | 2 | 100% |
| | UrgencyMapper | 2 | 100% |
| | IncidentTypeMapper | 2 | 100% |
| | KeywordMapper | 2 | 100% |
| | ArticleMapper | 2 | 100% |
| | LocationMapper | 2 | 95% (엣지 케이스) |
| | AnalysisResultMapper | 1 | 98% |
| | AnalysisResultOutboxMapper | 1 | 100% |
| **Mapper 합계** | **8개** | **14개** | **>98%** |
| Adapter | ArticleRepositoryAdapter | 3 | 95% |
| | AnalysisResultRepositoryAdapter | 6 | 90% |
| | (private methods) | 5 | 90% |
| **Adapter 합계** | **2개** | **14개** | **>90%** |
| **전체** | **10개** | **28개** | **>95%** |

---

## 🎯 최종 요약

### 작성할 테스트 파일 목록
| 파일 | 테스트 수 | 예상 라인 | 우선순위 |
|------|----------|---------|--------|
| CoordinateMapperTest.kt | 6 | ~100 | 🟢 최고 (의존성 없음) |
| UrgencyMapperTest.kt | 6 | ~100 | 🟢 최고 |
| IncidentTypeMapperTest.kt | 6 | ~100 | 🟢 최고 |
| KeywordMapperTest.kt | 6 | ~100 | 🟢 최고 |
| ArticleMapperTest.kt | 8 | ~150 | 🟢 높음 |
| LocationMapperTest.kt | 10 | ~200 | 🟡 중간 (CoordinateMapper 의존) |
| AnalysisResultMapperTest.kt | 10 | ~250 | 🟡 중간 (여러 Mapper 의존) |
| AnalysisResultOutboxMapperTest.kt | 8 | ~150 | 🟡 중간 |
| ArticleRepositoryAdapterTest.kt | 15 | ~300 | 🟠 낮음 (DB 필요) |
| AnalysisResultRepositoryAdapterTest.kt | 50 | ~1000 | 🔴 매우 낮음 (복잡함) |
| **합계** | **125개** | **~2,500** | - |

### 작성 순서
1. **Phase 1-1**: CoordinateMapper, UrgencyMapper, IncidentTypeMapper, KeywordMapper
2. **Phase 1-2**: ArticleMapper, LocationMapper
3. **Phase 1-3**: AnalysisResultMapper, AnalysisResultOutboxMapper
4. **Phase 2-1**: ArticleRepositoryAdapterTest
5. **Phase 2-2**: AnalysisResultRepositoryAdapterTest

