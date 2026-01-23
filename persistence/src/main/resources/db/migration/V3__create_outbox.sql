-- ============================================
-- Transactional Outbox Table for CDC
-- ============================================

-- Analysis Result Outbox Table
CREATE TABLE analysis_result_outbox (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    article_id VARCHAR(255) NOT NULL UNIQUE,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (article_id) REFERENCES article(article_id) ON DELETE CASCADE
);

CREATE INDEX idx_outbox_article_id ON analysis_result_outbox(article_id);
CREATE INDEX idx_outbox_created_at ON analysis_result_outbox(created_at);

-- ============================================
-- Table Comments
-- ============================================

COMMENT ON TABLE analysis_result_outbox IS 'Transactional Outbox 테이블 - CDC를 통해 Kafka로 전달되는 완전한 분석 결과 (At-least-once delivery 보장)';
COMMENT ON COLUMN analysis_result_outbox.article_id IS '기사 ID (외래키, 유일성 보장)';
COMMENT ON COLUMN analysis_result_outbox.payload IS 'AnalysisResult 도메인 모델의 완전한 데이터를 JSON으로 저장 (incidentTypes, urgency, locations, keywords 포함)';
COMMENT ON COLUMN analysis_result_outbox.created_at IS '분석 결과 생성 시간';