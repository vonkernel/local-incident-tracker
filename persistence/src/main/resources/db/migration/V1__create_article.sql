-- Create article table
CREATE TABLE article (
    article_id VARCHAR(255) PRIMARY KEY,
    origin_id VARCHAR(255) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    written_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for article table
CREATE INDEX idx_article_written_at ON article(written_at);

-- Add comments
COMMENT ON TABLE article IS '원본 뉴스 기사 정보를 저장하는 테이블';
COMMENT ON COLUMN article.article_id IS '기사의 고유 ID (수집 시스템에서 할당)';
COMMENT ON COLUMN article.origin_id IS '뉴스 소스에서의 원본 ID';
COMMENT ON COLUMN article.source_id IS '뉴스 소스 식별자 (예: yonhapnews)';
COMMENT ON COLUMN article.written_at IS '기사 작성 시간 (UTC)';
COMMENT ON COLUMN article.modified_at IS '기사 수정 시간 (UTC)';
COMMENT ON COLUMN article.title IS '기사 제목';
COMMENT ON COLUMN article.content IS '기사 본문 내용';
COMMENT ON COLUMN article.source_url IS '기사 원문 URL (선택사항)';