CREATE TABLE learning_courses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_learning_courses_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_learning_courses_user_id ON learning_courses (user_id);

CREATE TABLE learning_units (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES learning_courses (id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(150),
    index_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_learning_units_user_course ON learning_units (user_id, course_id);

ALTER TABLE chat_sessions ALTER COLUMN repository_id DROP NOT NULL;
ALTER TABLE chat_sessions
    ADD COLUMN course_id UUID REFERENCES learning_courses (id) ON DELETE CASCADE,
    ADD COLUMN unit_id UUID REFERENCES learning_units (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_chat_sessions_scope CHECK (
        (repository_id IS NOT NULL AND course_id IS NULL AND unit_id IS NULL)
        OR (repository_id IS NULL AND course_id IS NOT NULL)
    );

CREATE INDEX idx_chat_sessions_user_course ON chat_sessions (user_id, course_id, unit_id);
