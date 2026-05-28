CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verification_token VARCHAR(255),
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    password_reset_token VARCHAR(255),
    password_reset_token_expire_time TIMESTAMP,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    lockout_time TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(50) NOT NULL DEFAULT 'USER'
);

CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    release_year INT NOT NULL,
    director VARCHAR(255) NOT NULL,
    picture_url VARCHAR(255),
    external_id VARCHAR(255),
    CONSTRAINT uk_movies_title_year UNIQUE (title, release_year)
);

CREATE TABLE connections (
    id BIGSERIAL PRIMARY KEY,
    from_movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    to_movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    reason VARCHAR(500) NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    category VARCHAR(255),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_movies_title ON movies (title);
CREATE INDEX idx_connections_user_id ON connections (user_id);
CREATE INDEX idx_connections_user_movie_pair ON connections (user_id, from_movie_id, to_movie_id);
