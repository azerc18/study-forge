-- ========== QUIZ SETS ==========
CREATE TABLE quiz_sets
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id      UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    question_type    VARCHAR(10)  NOT NULL CHECK (question_type IN ('MCQ', 'ESSAY', 'MIXED')),
    requested_count  INT          NOT NULL CHECK (requested_count BETWEEN 1 AND 50),
    language         VARCHAR(2)   NOT NULL CHECK (language IN ('EN', 'VI')),
    status           VARCHAR(20)  NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'GENERATING', 'READY', 'PARTIAL', 'FAILED')),
    generated_count  INT          NOT NULL DEFAULT 0,
    total_chunks     INT          NOT NULL DEFAULT 0,
    completed_chunks INT          NOT NULL DEFAULT 0,
    cache_key        VARCHAR(64)  NOT NULL,   -- sha256(file_hash|type|count|lang|prompt_version)
    prompt_version   VARCHAR(20)  NOT NULL,
    error_message    VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

CREATE INDEX idx_quiz_sets_user_created ON quiz_sets (user_id, created_at DESC);
CREATE INDEX idx_quiz_sets_cache ON quiz_sets (cache_key) WHERE status = 'READY';

-- ========== JOB QUEUE ==========
CREATE TABLE generation_jobs
(
    id           BIGSERIAL PRIMARY KEY,
    quiz_set_id  UUID        NOT NULL UNIQUE REFERENCES quiz_sets (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    attempts     INT         NOT NULL DEFAULT 0,
    max_attempts INT         NOT NULL DEFAULT 3,
    next_run_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_by    VARCHAR(100),
    locked_at    TIMESTAMPTZ,
    last_error   VARCHAR(1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_pick ON generation_jobs (next_run_at) WHERE status = 'QUEUED';
CREATE INDEX idx_jobs_stuck ON generation_jobs (locked_at) WHERE status = 'RUNNING';

CREATE TABLE generation_chunks
(
    id           BIGSERIAL PRIMARY KEY,
    quiz_set_id  UUID        NOT NULL REFERENCES quiz_sets (id) ON DELETE CASCADE,
    chunk_index  INT         NOT NULL,
    page_from    INT         NOT NULL,
    page_to      INT         NOT NULL,
    word_count   INT         NOT NULL,
    mcq_target   INT         NOT NULL DEFAULT 0,
    essay_target INT         NOT NULL DEFAULT 0,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts     INT         NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000),
    UNIQUE (quiz_set_id, chunk_index)
);

-- ========== QUESTIONS ==========
CREATE TABLE questions
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    quiz_set_id     UUID        NOT NULL REFERENCES quiz_sets (id) ON DELETE CASCADE,
    chunk_id        BIGINT      REFERENCES generation_chunks (id) ON DELETE SET NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('MCQ', 'ESSAY')),
    order_index     INT         NOT NULL,
    content         TEXT        NOT NULL,
    explanation     TEXT,                  -- MCQ: giải thích đáp án
    model_answer    TEXT,                  -- ESSAY: đáp án mẫu
    rubric          JSONB,                 -- ESSAY: ["ý 1", "ý 2", ...]
    source_pages    INT[]       NOT NULL DEFAULT '{}',
    normalized_hash VARCHAR(64) NOT NULL,  -- dùng để loại câu trùng
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (quiz_set_id, normalized_hash)
);
CREATE INDEX idx_questions_set ON questions (quiz_set_id, order_index);

CREATE TABLE question_options
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    label       VARCHAR(1) NOT NULL CHECK (label IN ('A', 'B', 'C', 'D')),
    content     TEXT       NOT NULL,
    is_correct  BOOLEAN    NOT NULL DEFAULT FALSE,
    UNIQUE (question_id, label)
);
-- Mỗi câu trắc nghiệm có đúng 1 đáp án đúng; DB chặn luôn nếu code có bug
CREATE UNIQUE INDEX uq_one_correct_option ON question_options (question_id) WHERE is_correct;

-- ========== LLM CALL LOG ==========
CREATE TABLE llm_calls
(
    id                BIGSERIAL PRIMARY KEY,
    quiz_set_id       UUID REFERENCES quiz_sets (id) ON DELETE SET NULL,  -- giữ log để tính chi phí kể cả khi bộ đề bị xoá
    chunk_id          BIGINT REFERENCES generation_chunks (id) ON DELETE SET NULL,
    provider          VARCHAR(30)  NOT NULL,
    model             VARCHAR(100) NOT NULL,
    prompt_tokens     INT,
    completion_tokens INT,
    latency_ms        INT          NOT NULL,
    success           BOOLEAN      NOT NULL,
    http_status       INT,
    error_message     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_calls_created ON llm_calls (created_at);