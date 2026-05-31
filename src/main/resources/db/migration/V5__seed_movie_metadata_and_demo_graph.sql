UPDATE movies
SET tagline = 'Your mind is the scene of the crime.',
    synopsis = 'A thief who steals corporate secrets through dream-sharing takes one last job: planting an idea inside a target instead of extracting one.',
    genres = 'Science Fiction, Thriller, Heist',
    runtime_minutes = 148,
    cast_members = 'Leonardo DiCaprio, Joseph Gordon-Levitt, Elliot Page, Tom Hardy',
    director_notes = 'Nolan combines nested action with precise exposition and practical large-format spectacle.'
WHERE title = 'Inception' AND release_year = 2010;

UPDATE movies
SET tagline = 'Mankind was born on Earth. It was never meant to die here.',
    synopsis = 'When crop blight pushes Earth toward collapse, a former pilot joins a mission through a wormhole to search for habitable worlds.',
    genres = 'Science Fiction, Drama, Adventure',
    runtime_minutes = 169,
    cast_members = 'Matthew McConaughey, Anne Hathaway, Jessica Chastain, Mackenzie Foy',
    director_notes = 'Built around emotional scale, relativistic structure, and tactile large-format imagery.'
WHERE title = 'Interstellar' AND release_year = 2014;

UPDATE movies
SET tagline = 'Some memories are best forgotten.',
    synopsis = 'A man with short-term memory loss uses notes and tattoos to hunt the person he believes murdered his wife.',
    genres = 'Thriller, Mystery, Neo-Noir',
    runtime_minutes = 113,
    cast_members = 'Guy Pearce, Carrie-Anne Moss, Joe Pantoliano',
    director_notes = 'Reverse chronology turns audience recall into the engine of suspense.'
WHERE title = 'Memento' AND release_year = 2000;

UPDATE movies
SET tagline = 'Why are they here?',
    synopsis = 'A linguist races to decode the language of newly arrived aliens as global tensions escalate.',
    genres = 'Science Fiction, Drama, Mystery',
    runtime_minutes = 116,
    cast_members = 'Amy Adams, Jeremy Renner, Forest Whitaker',
    director_notes = 'Villeneuve and Johann Johannsson shape awe through patience, grief, and circular revelation.'
WHERE title = 'Arrival' AND release_year = 2016;

UPDATE movies
SET tagline = 'The key to the future is finally unearthed.',
    synopsis = 'A replicant hunter is drawn into a buried mystery that could upend the balance between humans and replicants.',
    genres = 'Science Fiction, Neo-Noir, Drama',
    runtime_minutes = 164,
    cast_members = 'Ryan Gosling, Harrison Ford, Ana de Armas, Sylvia Hoeks',
    director_notes = 'Austere pacing and monumental production design recast the sequel as a meditation on memory and identity.'
WHERE title = 'Blade Runner 2049' AND release_year = 2017;

UPDATE movies
SET tagline = 'An offer you cannot refuse.',
    synopsis = 'The aging patriarch of a crime family prepares to hand power to a son who wanted a life outside the organization.',
    genres = 'Crime, Drama, Epic',
    runtime_minutes = 175,
    cast_members = 'Marlon Brando, Al Pacino, James Caan, Diane Keaton',
    director_notes = 'Coppola stages power as ritual, family inheritance, and moral corrosion.'
WHERE title = 'The Godfather' AND release_year = 1972;

UPDATE movies
SET tagline = 'I know it was you, Fredo. You broke my heart.',
    synopsis = 'Michael Corleone tries to expand his empire while parallel flashbacks chart his father''s rise in America.',
    genres = 'Crime, Drama, Epic',
    runtime_minutes = 202,
    cast_members = 'Al Pacino, Robert De Niro, Robert Duvall, Diane Keaton',
    director_notes = 'The sequel broadens the original into a study of succession, exile, and institutional decay.'
WHERE title = 'The Godfather Part II' AND release_year = 1974;

UPDATE movies
SET tagline = 'This is the end.',
    synopsis = 'During the Vietnam War, a captain travels upriver to assassinate a rogue colonel who has become a cult figure.',
    genres = 'War, Drama, Psychological',
    runtime_minutes = 147,
    cast_members = 'Martin Sheen, Marlon Brando, Robert Duvall, Laurence Fishburne',
    director_notes = 'Coppola reframes war as operatic nightmare and colonial hallucination.'
WHERE title = 'Apocalypse Now' AND release_year = 1979;

UPDATE movies
SET tagline = 'Welcome to the real world.',
    synopsis = 'A hacker discovers the world is a machine simulation and joins a resistance to free humanity.',
    genres = 'Science Fiction, Action, Cyberpunk',
    runtime_minutes = 136,
    cast_members = 'Keanu Reeves, Carrie-Anne Moss, Laurence Fishburne, Hugo Weaving',
    director_notes = 'The Wachowskis merge action grammar, philosophy, and digital paranoia into a precise pop myth.'
WHERE title = 'The Matrix' AND release_year = 1999;

UPDATE movies
SET tagline = 'Why so serious?',
    synopsis = 'Batman confronts the Joker as chaos spills through Gotham and tests every institution meant to contain it.',
    genres = 'Crime, Thriller, Superhero',
    runtime_minutes = 152,
    cast_members = 'Christian Bale, Heath Ledger, Aaron Eckhart, Gary Oldman',
    director_notes = 'Nolan scales comic-book stakes through procedural detail and urban dread.'
WHERE title = 'The Dark Knight' AND release_year = 2008;

UPDATE movies
SET tagline = 'Beyond fear, destiny awaits.',
    synopsis = 'Paul Atreides joins the Fremen while rival houses and empires fight for control of Arrakis.',
    genres = 'Science Fiction, Epic, Adventure',
    runtime_minutes = 155,
    cast_members = 'Timothee Chalamet, Zendaya, Rebecca Ferguson, Javier Bardem',
    director_notes = 'Villeneuve treats world-building as physical environment, ritual, and strategic silence.'
WHERE title = 'Dune' AND release_year = 2021;

UPDATE movies
SET tagline = 'Long live the fighters.',
    synopsis = 'Paul embraces a perilous messianic path as war spreads across the Imperium.',
    genres = 'Science Fiction, Epic, War',
    runtime_minutes = 166,
    cast_members = 'Timothee Chalamet, Zendaya, Rebecca Ferguson, Austin Butler',
    director_notes = 'The sequel intensifies the first film''s scale while foregrounding fanaticism and political consequence.'
WHERE title = 'Dune: Part Two' AND release_year = 2024;

INSERT INTO users (
    email,
    password,
    username,
    role,
    enabled,
    verification_token,
    verified,
    failed_login_attempts,
    deleted,
    creation_time,
    update_time
) VALUES (
    'demo@archipelago.local',
    '$argon2id$v=19$m=65536,t=3,p=1$XlB805ZmSagZ8mGE6h7ZbQ$2uOsrRfqlOUDC/bCOmIlLVQIs6yY1EVnXb4SBByjErs',
    'demo',
    'USER',
    TRUE,
    NULL,
    TRUE,
    0,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO connections (from_movie_id, to_movie_id, reason, weight, category, user_id, creation_time, update_time)
VALUES
    (1, 2, 'Both films channel Nolan''s fascination with time, emotional sacrifice, and large-format spectacle.', 2.4, 'director', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (1, 3, 'Each story turns memory limits into the structure of the narrative itself.', 2.1, 'structure', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 5, 'Both build sci-fi tension through language, cosmology, and grief rather than simple combat.', 1.7, 'theme', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5, 6, 'Villeneuve links first contact and dystopian futures through controlled pacing and monumental design.', 2.3, 'director', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (35, 36, 'The second chapter extends the Corleone family saga while deepening the cost of inheritance.', 2.0, 'franchise', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (36, 37, 'Coppola pivots from family dynasty to war nightmare, but both films stage power as corruption.', 1.4, 'director', (SELECT id FROM users WHERE email = 'demo@archipelago.local'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
