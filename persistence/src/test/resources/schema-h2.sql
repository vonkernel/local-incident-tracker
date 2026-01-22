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
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL UNIQUE,
    level INT NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_type (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE address (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
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
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE TABLE urgency_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    urgency_type_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (urgency_type_id) REFERENCES urgency_type(id) ON DELETE RESTRICT
);

CREATE TABLE incident_type_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    incident_type_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (incident_type_id) REFERENCES incident_type(id) ON DELETE RESTRICT,
    UNIQUE(analysis_result_id, incident_type_id)
);

CREATE TABLE address_mapping (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    address_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE,
    FOREIGN KEY (address_id) REFERENCES address(id) ON DELETE RESTRICT,
    UNIQUE(analysis_result_id, address_id)
);

CREATE TABLE article_keywords (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    analysis_result_id BIGINT NOT NULL,
    keyword VARCHAR(500) NOT NULL,
    priority INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- ============================================
-- Transactional Outbox Table for CDC
-- ============================================

CREATE TABLE analysis_result_outbox (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE INDEX idx_outbox_article_id ON analysis_result_outbox(article_id);
CREATE INDEX idx_outbox_created_at ON analysis_result_outbox(created_at);
