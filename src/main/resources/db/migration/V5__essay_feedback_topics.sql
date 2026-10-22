
ALTER TABLE questions ADD COLUMN topic VARCHAR(80);

ALTER TABLE quiz_sets ADD COLUMN use_cache BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE llm_calls ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'GENERATION'
    CHECK (purpose IN ('GENERATION', 'ESSAY_FEEDBACK'));

CREATE TABLE essay_feedbacks
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    question_id    UUID         NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    answer         TEXT         NOT NULL CHECK (char_length(answer) BETWEEN 1 AND 3000),
    score          NUMERIC(3, 1) NOT NULL CHECK (score BETWEEN 0 AND 10),
    rubric_results JSONB        NOT NULL,
    feedback       JSONB        NOT NULL,
    model          VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_essay_feedbacks_question_user ON essay_feedbacks (question_id, user_id, created_at DESC);

CREATE INDEX idx_essay_feedbacks_user_created ON essay_feedbacks (user_id, created_at);