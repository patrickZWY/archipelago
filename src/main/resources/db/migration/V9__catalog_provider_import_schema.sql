CREATE TABLE IF NOT EXISTS catalog_import_runs (
    id VARCHAR(36) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    source VARCHAR(255) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    total_processed INT NOT NULL DEFAULT 0,
    error_kind VARCHAR(64),
    error_message VARCHAR(500),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_catalog_import_runs_provider_source
    ON catalog_import_runs (provider_id, source);

CREATE INDEX IF NOT EXISTS idx_catalog_import_runs_started_at
    ON catalog_import_runs (started_at);

CREATE TABLE IF NOT EXISTS movie_external_ids (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    provider_id VARCHAR(64) NOT NULL,
    source VARCHAR(255) NOT NULL,
    external_id_type VARCHAR(64) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_movie_external_ids_provider_external
    ON movie_external_ids (provider_id, external_id_type, external_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_movie_external_ids_movie_source_type
    ON movie_external_ids (movie_id, provider_id, source, external_id_type);

CREATE INDEX IF NOT EXISTS idx_movie_external_ids_movie
    ON movie_external_ids (movie_id);

CREATE TABLE IF NOT EXISTS movie_genres (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    provider_id VARCHAR(64) NOT NULL,
    source VARCHAR(255) NOT NULL,
    genre VARCHAR(128) NOT NULL,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_movie_genres_movie_provider_source
    ON movie_genres (movie_id, provider_id, source, genre);

CREATE INDEX IF NOT EXISTS idx_movie_genres_genre
    ON movie_genres (genre);

CREATE TABLE IF NOT EXISTS movie_people (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    provider_id VARCHAR(64) NOT NULL,
    source VARCHAR(255) NOT NULL,
    person_name VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    billing_order INT NOT NULL DEFAULT 0,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_movie_people_movie_provider_source
    ON movie_people (movie_id, provider_id, source, role, person_name);

CREATE INDEX IF NOT EXISTS idx_movie_people_person
    ON movie_people (person_name);
