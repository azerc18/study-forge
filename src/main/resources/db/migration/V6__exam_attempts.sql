CREATE TABLE exam_attempts
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    quiz_set_id        UUID          NOT NULL REFERENCES quiz_sets (id) ON DELETE CASCADE,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status             VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS'
        CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    time_limit_seconds INT CHECK (time_limit_seconds BETWEEN 60 AND 10800),
    total              INT           NOT NULL CHECK (total > 0),
    correct_count      INT,
    score              NUMERIC(3, 1) CHECK (score BETWEEN 0 AND 10),
    started_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    submitted_at       TIMESTAMPTZ,
    CHECK (correct_count BETWEEN 0 AND total),
    CHECK ((status = 'SUBMITTED') = (submitted_at IS NOT NULL AND correct_count IS NOT NULL AND score IS NOT NULL))
);

CREATE UNIQUE INDEX uq_exam_attempts_open ON exam_attempts (user_id, quiz_set_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_exam_attempts_user_set ON exam_attempts (user_id, quiz_set_id, started_at DESC);

CREATE TABLE exam_answers
(
    id             BIGSERIAL PRIMARY KEY,
    attempt_id     UUID       NOT NULL REFERENCES exam_attempts (id) ON DELETE CASCADE,
    question_id    UUID       NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    order_index    INT        NOT NULL CHECK (order_index >= 1),
    selected_label VARCHAR(1) CHECK (selected_label IN ('A', 'B', 'C', 'D')),
    correct        BOOLEAN,
    UNIQUE (attempt_id, question_id),
    UNIQUE (attempt_id, order_index)
);