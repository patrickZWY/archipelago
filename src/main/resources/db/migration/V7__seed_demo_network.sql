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
) VALUES
    (
        'demo.friend.one@archipelago.local',
        '$argon2id$v=19$m=65536,t=3,p=1$XlB805ZmSagZ8mGE6h7ZbQ$2uOsrRfqlOUDC/bCOmIlLVQIs6yY1EVnXb4SBByjErs',
        'demo-alex',
        'USER',
        TRUE,
        NULL,
        TRUE,
        0,
        FALSE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        'demo.friend.two@archipelago.local',
        '$argon2id$v=19$m=65536,t=3,p=1$XlB805ZmSagZ8mGE6h7ZbQ$2uOsrRfqlOUDC/bCOmIlLVQIs6yY1EVnXb4SBByjErs',
        'demo-riley',
        'USER',
        TRUE,
        NULL,
        TRUE,
        0,
        FALSE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO friendships (requester_user_id, recipient_user_id, status, creation_time, update_time)
VALUES
    (
        (SELECT id FROM users WHERE email = 'demo@archipelago.local'),
        (SELECT id FROM users WHERE email = 'demo.friend.one@archipelago.local'),
        'ACCEPTED',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        (SELECT id FROM users WHERE email = 'demo.friend.two@archipelago.local'),
        (SELECT id FROM users WHERE email = 'demo@archipelago.local'),
        'ACCEPTED',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

INSERT INTO connections (from_movie_id, to_movie_id, reason, weight, category, user_id, creation_time, update_time)
VALUES
    (
        1,
        2,
        'Alex groups Nolan epics around scale, practical spectacle, and emotional architecture.',
        2.0,
        'director',
        (SELECT id FROM users WHERE email = 'demo.friend.one@archipelago.local'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        2,
        3,
        'Alex links Nolan puzzles where fractured memory drives the plot mechanics.',
        1.5,
        'structure',
        (SELECT id FROM users WHERE email = 'demo.friend.one@archipelago.local'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        35,
        36,
        'Riley tracks sequel pairs that widen the family tragedy instead of simply repeating it.',
        2.2,
        'franchise',
        (SELECT id FROM users WHERE email = 'demo.friend.two@archipelago.local'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        36,
        37,
        'Riley follows Coppola from dynastic crime into institutional madness and moral decay.',
        1.6,
        'director',
        (SELECT id FROM users WHERE email = 'demo.friend.two@archipelago.local'),
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );
