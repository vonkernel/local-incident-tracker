-- refined_article 테이블
CREATE TABLE refined_article (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    summary TEXT NOT NULL,
    written_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_refined_article_analysis_result
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);

-- topic_analysis 테이블
CREATE TABLE topic_analysis (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    analysis_result_id BIGINT NOT NULL UNIQUE,
    topic VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_topic_analysis_analysis_result
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id) ON DELETE CASCADE
);
