-- Backfill provider-owned metadata for the built-in demo catalog so a fresh
-- demo session can showcase enriched details, metadata filters, and suggestions
-- before any optional catalog import is run.

INSERT INTO movie_external_ids (
    movie_id,
    provider_id,
    source,
    external_id_type,
    external_id
)
SELECT
    m.id,
    'curated',
    'curated-spring-2026',
    'imdb',
    m.external_id
FROM movies m
WHERE m.external_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM movie_external_ids existing
      WHERE existing.provider_id = 'curated'
        AND existing.external_id_type = 'imdb'
        AND existing.external_id = m.external_id
  );

INSERT INTO movie_genres (
    movie_id,
    provider_id,
    source,
    genre
)
SELECT
    m.id,
    'curated',
    'curated-spring-2026',
    seeded.genre
FROM movies m
JOIN (
    SELECT 'Inception' AS title, 2010 AS release_year, 'Science Fiction' AS genre
    UNION ALL SELECT 'Inception', 2010, 'Thriller'
    UNION ALL SELECT 'Inception', 2010, 'Heist'
    UNION ALL SELECT 'Interstellar', 2014, 'Science Fiction'
    UNION ALL SELECT 'Interstellar', 2014, 'Drama'
    UNION ALL SELECT 'Interstellar', 2014, 'Adventure'
    UNION ALL SELECT 'Memento', 2000, 'Thriller'
    UNION ALL SELECT 'Memento', 2000, 'Mystery'
    UNION ALL SELECT 'Memento', 2000, 'Neo-Noir'
    UNION ALL SELECT 'The Prestige', 2006, 'Mystery'
    UNION ALL SELECT 'The Prestige', 2006, 'Drama'
    UNION ALL SELECT 'The Prestige', 2006, 'Thriller'
    UNION ALL SELECT 'The Dark Knight', 2008, 'Crime'
    UNION ALL SELECT 'The Dark Knight', 2008, 'Thriller'
    UNION ALL SELECT 'The Dark Knight', 2008, 'Superhero'
    UNION ALL SELECT 'Arrival', 2016, 'Science Fiction'
    UNION ALL SELECT 'Arrival', 2016, 'Drama'
    UNION ALL SELECT 'Arrival', 2016, 'Mystery'
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Science Fiction'
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Neo-Noir'
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Drama'
    UNION ALL SELECT 'Dune', 2021, 'Science Fiction'
    UNION ALL SELECT 'Dune', 2021, 'Epic'
    UNION ALL SELECT 'Dune', 2021, 'Adventure'
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Science Fiction'
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Epic'
    UNION ALL SELECT 'Dune: Part Two', 2024, 'War'
    UNION ALL SELECT 'The Matrix', 1999, 'Science Fiction'
    UNION ALL SELECT 'The Matrix', 1999, 'Action'
    UNION ALL SELECT 'The Matrix', 1999, 'Cyberpunk'
    UNION ALL SELECT 'The Godfather', 1972, 'Crime'
    UNION ALL SELECT 'The Godfather', 1972, 'Drama'
    UNION ALL SELECT 'The Godfather', 1972, 'Epic'
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Crime'
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Drama'
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Epic'
    UNION ALL SELECT 'Apocalypse Now', 1979, 'War'
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Drama'
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Psychological'
) seeded ON seeded.title = m.title AND seeded.release_year = m.release_year
WHERE NOT EXISTS (
    SELECT 1
    FROM movie_genres existing
    WHERE existing.movie_id = m.id
      AND existing.provider_id = 'curated'
      AND existing.source = 'curated-spring-2026'
      AND existing.genre = seeded.genre
);

INSERT INTO movie_people (
    movie_id,
    provider_id,
    source,
    person_name,
    role,
    billing_order
)
SELECT
    m.id,
    'curated',
    'curated-spring-2026',
    seeded.person_name,
    seeded.role,
    seeded.billing_order
FROM movies m
JOIN (
    SELECT 'Inception' AS title, 2010 AS release_year, 'Christopher Nolan' AS person_name, 'DIRECTOR' AS role, 0 AS billing_order
    UNION ALL SELECT 'Inception', 2010, 'Leonardo DiCaprio', 'CAST', 1
    UNION ALL SELECT 'Inception', 2010, 'Joseph Gordon-Levitt', 'CAST', 2
    UNION ALL SELECT 'Inception', 2010, 'Elliot Page', 'CAST', 3
    UNION ALL SELECT 'Inception', 2010, 'Tom Hardy', 'CAST', 4
    UNION ALL SELECT 'Inception', 2010, 'Michael Caine', 'CAST', 5
    UNION ALL SELECT 'Interstellar', 2014, 'Christopher Nolan', 'DIRECTOR', 0
    UNION ALL SELECT 'Interstellar', 2014, 'Matthew McConaughey', 'CAST', 1
    UNION ALL SELECT 'Interstellar', 2014, 'Anne Hathaway', 'CAST', 2
    UNION ALL SELECT 'Interstellar', 2014, 'Jessica Chastain', 'CAST', 3
    UNION ALL SELECT 'Interstellar', 2014, 'Mackenzie Foy', 'CAST', 4
    UNION ALL SELECT 'Interstellar', 2014, 'Michael Caine', 'CAST', 5
    UNION ALL SELECT 'Memento', 2000, 'Christopher Nolan', 'DIRECTOR', 0
    UNION ALL SELECT 'Memento', 2000, 'Guy Pearce', 'CAST', 1
    UNION ALL SELECT 'Memento', 2000, 'Carrie-Anne Moss', 'CAST', 2
    UNION ALL SELECT 'Memento', 2000, 'Joe Pantoliano', 'CAST', 3
    UNION ALL SELECT 'The Prestige', 2006, 'Christopher Nolan', 'DIRECTOR', 0
    UNION ALL SELECT 'The Prestige', 2006, 'Christian Bale', 'CAST', 1
    UNION ALL SELECT 'The Prestige', 2006, 'Hugh Jackman', 'CAST', 2
    UNION ALL SELECT 'The Prestige', 2006, 'Scarlett Johansson', 'CAST', 3
    UNION ALL SELECT 'The Prestige', 2006, 'Michael Caine', 'CAST', 4
    UNION ALL SELECT 'The Dark Knight', 2008, 'Christopher Nolan', 'DIRECTOR', 0
    UNION ALL SELECT 'The Dark Knight', 2008, 'Christian Bale', 'CAST', 1
    UNION ALL SELECT 'The Dark Knight', 2008, 'Heath Ledger', 'CAST', 2
    UNION ALL SELECT 'The Dark Knight', 2008, 'Aaron Eckhart', 'CAST', 3
    UNION ALL SELECT 'The Dark Knight', 2008, 'Gary Oldman', 'CAST', 4
    UNION ALL SELECT 'The Dark Knight', 2008, 'Michael Caine', 'CAST', 5
    UNION ALL SELECT 'Arrival', 2016, 'Denis Villeneuve', 'DIRECTOR', 0
    UNION ALL SELECT 'Arrival', 2016, 'Amy Adams', 'CAST', 1
    UNION ALL SELECT 'Arrival', 2016, 'Jeremy Renner', 'CAST', 2
    UNION ALL SELECT 'Arrival', 2016, 'Forest Whitaker', 'CAST', 3
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Denis Villeneuve', 'DIRECTOR', 0
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Ryan Gosling', 'CAST', 1
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Harrison Ford', 'CAST', 2
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Ana de Armas', 'CAST', 3
    UNION ALL SELECT 'Blade Runner 2049', 2017, 'Sylvia Hoeks', 'CAST', 4
    UNION ALL SELECT 'Dune', 2021, 'Denis Villeneuve', 'DIRECTOR', 0
    UNION ALL SELECT 'Dune', 2021, 'Timothee Chalamet', 'CAST', 1
    UNION ALL SELECT 'Dune', 2021, 'Zendaya', 'CAST', 2
    UNION ALL SELECT 'Dune', 2021, 'Rebecca Ferguson', 'CAST', 3
    UNION ALL SELECT 'Dune', 2021, 'Javier Bardem', 'CAST', 4
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Denis Villeneuve', 'DIRECTOR', 0
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Timothee Chalamet', 'CAST', 1
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Zendaya', 'CAST', 2
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Rebecca Ferguson', 'CAST', 3
    UNION ALL SELECT 'Dune: Part Two', 2024, 'Austin Butler', 'CAST', 4
    UNION ALL SELECT 'The Matrix', 1999, 'The Wachowskis', 'DIRECTOR', 0
    UNION ALL SELECT 'The Matrix', 1999, 'Keanu Reeves', 'CAST', 1
    UNION ALL SELECT 'The Matrix', 1999, 'Carrie-Anne Moss', 'CAST', 2
    UNION ALL SELECT 'The Matrix', 1999, 'Laurence Fishburne', 'CAST', 3
    UNION ALL SELECT 'The Matrix', 1999, 'Hugo Weaving', 'CAST', 4
    UNION ALL SELECT 'The Godfather', 1972, 'Francis Ford Coppola', 'DIRECTOR', 0
    UNION ALL SELECT 'The Godfather', 1972, 'Marlon Brando', 'CAST', 1
    UNION ALL SELECT 'The Godfather', 1972, 'Al Pacino', 'CAST', 2
    UNION ALL SELECT 'The Godfather', 1972, 'James Caan', 'CAST', 3
    UNION ALL SELECT 'The Godfather', 1972, 'Diane Keaton', 'CAST', 4
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Francis Ford Coppola', 'DIRECTOR', 0
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Al Pacino', 'CAST', 1
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Robert De Niro', 'CAST', 2
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Robert Duvall', 'CAST', 3
    UNION ALL SELECT 'The Godfather Part II', 1974, 'Diane Keaton', 'CAST', 4
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Francis Ford Coppola', 'DIRECTOR', 0
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Martin Sheen', 'CAST', 1
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Marlon Brando', 'CAST', 2
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Robert Duvall', 'CAST', 3
    UNION ALL SELECT 'Apocalypse Now', 1979, 'Laurence Fishburne', 'CAST', 4
) seeded ON seeded.title = m.title AND seeded.release_year = m.release_year
WHERE NOT EXISTS (
    SELECT 1
    FROM movie_people existing
    WHERE existing.movie_id = m.id
      AND existing.provider_id = 'curated'
      AND existing.source = 'curated-spring-2026'
      AND existing.role = seeded.role
      AND existing.person_name = seeded.person_name
);
