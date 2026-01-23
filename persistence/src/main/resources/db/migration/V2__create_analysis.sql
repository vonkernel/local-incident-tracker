-- ============================================
-- 관리용 마스터 테이블
-- ============================================

-- 긴급도/중요도 타입 관리 (Admin이 추가/삭제)
CREATE TABLE urgency_type (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL UNIQUE,
    level INT NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 사건 유형 관리 (Admin이 추가/삭제)
CREATE TABLE incident_type (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 주소 데이터 (동일 행정구역은 테이블에 한 번만 등록)
CREATE TABLE address (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    region_type VARCHAR(1) NOT NULL,
    code VARCHAR(100) NOT NULL,
    address_name VARCHAR(500) NOT NULL,
    depth1_name VARCHAR(255),
    depth2_name VARCHAR(255),
    depth3_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(region_type, code)
);

-- 지리 좌표 정보 (Address의 하위, 1:1 관계)
CREATE TABLE address_coordinate (
    address_id BIGINT PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (address_id) REFERENCES address(id) ON DELETE CASCADE
);

-- ============================================
-- 분석 결과 마킹 테이블
-- ============================================

-- 분석 결과 마킹용 테이블 (실제 Outbox 테이블은 별도로 정의될 예정)
CREATE TABLE analysis_result (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

-- ============================================
-- AnalysisResult와의 매핑 테이블들
-- ============================================

-- AnalysisResult과 Urgency의 N:1 관계
CREATE TABLE urgency_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    urgency_type_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (urgency_type_id) REFERENCES urgency_type(id) ON DELETE RESTRICT
);

-- AnalysisResult과 IncidentType의 M:N 관계
CREATE TABLE incident_type_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    incident_type_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (incident_type_id) REFERENCES incident_type(id) ON DELETE RESTRICT,
    UNIQUE(analysis_result_id, incident_type_id)
);

-- AnalysisResult과 Address의 M:N 관계 (Address하위의 GeoPoint는 자동으로 포함)
CREATE TABLE address_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES address(id) ON DELETE RESTRICT,
    UNIQUE(analysis_result_id, address_id)
);

-- AnalysisResult의 키워드
CREATE TABLE article_keywords (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    keyword VARCHAR(500) NOT NULL,
    priority INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- ============================================
-- 테이블 주석
-- ============================================

COMMENT ON TABLE urgency_type IS '긴급도/중요도 타입을 정의하는 테이블 (Admin이 관리)';
COMMENT ON COLUMN urgency_type.name IS '긴급도 이름 (예: 긴급, 중요, 정보)';
COMMENT ON COLUMN urgency_type.level IS '우선도 레벨 (숫자가 클수록 높은 우선도)';

COMMENT ON TABLE incident_type IS '사건 유형을 정의하는 테이블 (Admin이 관리)';
COMMENT ON COLUMN incident_type.code IS '사건 유형 코드 (예: forest_fire, typhoon)';
COMMENT ON COLUMN incident_type.name IS '사건 유형 이름 (예: 산불, 태풍)';

COMMENT ON TABLE address IS '분석된 주소 데이터 (중복 제거, region_type+code+address_name으로 유일성 보장)';
COMMENT ON COLUMN address.region_type IS '지역 유형: B(법정동), H(행정동), U(구별 불가)';
COMMENT ON COLUMN address.code IS '지역 코드 (Kakao API, 식별 불가시 00000)';
COMMENT ON COLUMN address.address_name IS '전체 주소명 (항상 존재)';
COMMENT ON COLUMN address.depth1_name IS '시/도 (깊이 1, nullable)';
COMMENT ON COLUMN address.depth2_name IS '시/군/구 (깊이 2)';
COMMENT ON COLUMN address.depth3_name IS '읍/면/동 (깊이 3)';

COMMENT ON TABLE address_coordinate IS '지리 좌표 정보 (Address의 하위, 1:1 관계로 각 Address마다 하나의 AddressGeoPoint)';
COMMENT ON COLUMN address_coordinate.address_id IS 'Address FK (1:1 관계)';
COMMENT ON COLUMN address_coordinate.latitude IS '위도 (Latitude)';
COMMENT ON COLUMN address_coordinate.longitude IS '경도 (Longitude)';

COMMENT ON TABLE analysis_result IS '분석 결과 마킹 테이블 (실제 Outbox는 별도 테이블로 정의될 예정)';
COMMENT ON COLUMN analysis_result.article_id IS '기사 ID (외래키)';
COMMENT ON COLUMN analysis_result.created_at IS '분석 결과 생성 시간';

COMMENT ON TABLE urgency_mapping IS 'AnalysisResult과 UrgencyType의 N:1 관계 (하나의 분석 결과는 하나의 긴급도를 가짐)';
COMMENT ON TABLE incident_type_mapping IS 'AnalysisResult과 IncidentType의 M:N 관계 (하나의 분석 결과는 여러 사건 유형을 가질 수 있음)';
COMMENT ON TABLE address_mapping IS 'AnalysisResult과 Address의 M:N 관계 (Address하위의 AddressCoordinate는 자동으로 포함)';
COMMENT ON TABLE article_keywords IS 'AnalysisResult의 추출된 키워드';
COMMENT ON COLUMN article_keywords.keyword IS '추출된 키워드';
COMMENT ON COLUMN article_keywords.priority IS '키워드 우선도 (높을수록 중요)';

-- ============================================
-- 인덱스
-- ============================================

CREATE INDEX idx_address_name ON address(address_name);