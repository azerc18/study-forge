CREATE TABLE documents
(
    id                UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    file_type         VARCHAR(10)  NOT NULL CHECK (file_type IN ('PDF', 'PPTX')),
    file_size_bytes   BIGINT       NOT NULL CHECK (file_size_bytes > 0),
    file_hash         VARCHAR(64)  NOT NULL,
    storage_key       VARCHAR(255),
    page_count        INT,
    word_count        INT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
        CHECK (status IN ('UPLOADED', 'EXTRACTING', 'READY', 'FAILED')),
    error_message     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_documents_user_created ON documents (user_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_hash ON documents (file_hash);

CREATE TABLE document_pages
(
    id          BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number INT  NOT NULL CHECK (page_number > 0),
    content     TEXT NOT NULL,
    word_count  INT  NOT NULL CHECK (word_count >= 0),
    UNIQUE (document_id, page_number)
);