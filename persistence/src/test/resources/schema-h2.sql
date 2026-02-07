-- ============================================
-- 초기화: 기존 테이블 삭제 (외래키 제약 조건 고려 CASCADE 사용)
-- ============================================
DROP TABLE IF EXISTS article_keywords CASCADE;
DROP TABLE IF EXISTS address_mapping CASCADE;
DROP TABLE IF EXISTS incident_type_mapping CASCADE;
DROP TABLE IF EXISTS urgency_mapping CASCADE;
DROP TABLE IF EXISTS refined_article CASCADE;
DROP TABLE IF EXISTS topic_analysis CASCADE;
DROP TABLE IF EXISTS analysis_result_outbox CASCADE;
DROP TABLE IF EXISTS analysis_result CASCADE;
DROP TABLE IF EXISTS address_coordinate CASCADE;
DROP TABLE IF EXISTS address CASCADE;
DROP TABLE IF EXISTS incident_type CASCADE;
DROP TABLE IF EXISTS urgency_type CASCADE;
DROP TABLE IF EXISTS article CASCADE;

-- ============================================
-- Article Table
-- ============================================
CREATE TABLE article (
    article_id VARCHAR(255) PRIMARY KEY,
    origin_id VARCHAR(255) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    written_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source_url VARCHAR(2048),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_article_written_at ON article(written_at);

-- ============================================
-- Master Tables for Management
-- ============================================
CREATE TABLE urgency_type (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    level INT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_type (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE address (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_type VARCHAR(1) NOT NULL,
    code VARCHAR(100) NOT NULL,
    address_name VARCHAR(500) NOT NULL,
    depth1_name VARCHAR(255),
    depth2_name VARCHAR(255),
    depth3_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(region_type, code)
);

CREATE TABLE address_coordinate (
    address_id BIGINT PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (address_id) REFERENCES address(id) ON DELETE CASCADE
);

-- ============================================
-- Analysis Result Mapping Tables
-- ============================================
CREATE TABLE analysis_result (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    article_updated_at TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE TABLE urgency_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    urgency_type_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (urgency_type_id) REFERENCES urgency_type(id) ON DELETE RESTRICT
);

CREATE TABLE incident_type_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL,
    incident_type_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (incident_type_id) REFERENCES incident_type(id) ON DELETE RESTRICT,
    UNIQUE(analysis_result_id, incident_type_id)
);

CREATE TABLE address_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES address(id) ON DELETE RESTRICT,
UNIQUE(analysis_result_id, address_id)
);

CREATE TABLE article_keywords (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL,
    keyword VARCHAR(500) NOT NULL,
    priority INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- ============================================
-- Refined Article Table
-- ============================================
CREATE TABLE refined_article (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT NOT NULL,
    written_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- ============================================
-- Topic Analysis Table
-- ============================================
CREATE TABLE topic_analysis (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    topic VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- ============================================
-- Transactional Outbox Table for CDC
-- ============================================
CREATE TABLE analysis_result_outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE INDEX idx_address_name ON address(address_name);

CREATE INDEX idx_outbox_article_id ON analysis_result_outbox(article_id);
CREATE INDEX idx_outbox_created_at ON analysis_result_outbox(created_at);

-- ============================================
-- Master Data (V4)
-- ============================================
INSERT INTO urgency_type (name, level) VALUES
    ('정보', 1),
    ('주의', 3),
    ('경계', 5),
    ('심각', 7),
    ('긴급', 9);

INSERT INTO incident_type (code, name) VALUES
    ('AVIAN_INFLUENZA', '조류독감'),
    ('DROUGHT', '가뭄'),
    ('LIVESTOCK_DISEASE', '가축질병'),
    ('STRONG_WIND', '강풍'),
    ('DRY_WEATHER', '건조'),
    ('TRAFFIC', '교통'),
    ('TRAFFIC_ACCIDENT', '교통사고'),
    ('TRAFFIC_CONTROL', '교통통제'),
    ('FINANCE', '금융'),
    ('OTHER', '기타'),
    ('HEAVY_SNOW', '대설'),
    ('FINE_DUST', '미세먼지'),
    ('CIVIL_DEFENSE', '민방공'),
    ('COLLAPSE', '붕괴'),
    ('FOREST_FIRE', '산불'),
    ('LANDSLIDE', '산사태'),
    ('WATER_SUPPLY', '수도'),
    ('FOG', '안개'),
    ('ENERGY', '에너지'),
    ('EPIDEMIC', '전염병'),
    ('POWER_OUTAGE', '정전'),
    ('EARTHQUAKE', '지진'),
    ('TSUNAMI', '지진해일'),
    ('TYPHOON', '태풍'),
    ('TERRORISM', '테러'),
    ('COMMUNICATION', '통신'),
    ('EXPLOSION', '폭발'),
    ('HEAT_WAVE', '폭염'),
    ('HIGH_SEAS', '풍랑'),
    ('COLD_WAVE', '한파'),
    ('HEAVY_RAIN', '호우'),
    ('FLOOD', '홍수'),
    ('FIRE', '화재'),
    ('ENVIRONMENTAL_POLLUTION', '환경오염사고'),
    ('YELLOW_DUST', '황사'),
    ('MARITIME_ACCIDENT', '해양선박사고'),
    ('DEATH', '사망');