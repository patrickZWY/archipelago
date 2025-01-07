-- 1. Create Users table

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(50) NOT NULL,
    creation_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    verification_token VARCHAR(255),
    verified BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL,
    password_reset_token VARCHAR(255),
    password_reset_token_expire_time TIMESTAMP,
    failed_login_attempts INT,
    lockout_time TIMESTAMP,
    deleted BOOLEAN NOT NULL,
    role VARCHAR(50) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

-- 2. Create Movies table
CREATE TABLE IF NOT EXISTS movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    release_year INT NOT NULL,
    director VARCHAR(255) NOT NULL,
    picture_url VARCHAR(255),
    external_id VARCHAR(255),
    CONSTRAINT uk_movies_title UNIQUE (title)
);

-- 3. Create Connections table
CREATE TABLE IF NOT EXISTS connections (
    id BIGSERIAL PRIMARY KEY,
    from_movie_id BIGINT NOT NULL,
    to_movie_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    category VARCHAR(255),
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_connections_from_movie FOREIGN KEY (from_movie_id)
                                       REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_connections_to_movie FOREIGN KEY (to_movie_id)
                                       REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_connections_user FOREIGN KEY (user_id)
                                       REFERENCES users(id) ON DELETE CASCADE
);






















