create table if not exists h2h
(
    home_team_id    integer,
    away_team_id    integer,
    home_team_score integer,
    away_team_score integer,
    league_name     varchar(255),
    local_date_time timestamp,
    CONSTRAINT unique_h2h UNIQUE (home_team_id, away_team_id, local_date_time)
);

create table if not exists match
(
    home_team_id    integer,
    away_team_id    integer,
    home_team_score integer,
    away_team_score integer,
    public_id       integer,
    result          varchar(255),
    status          varchar(255),
    week_id         integer,
    local_date_time timestamp,
    last_processed_at timestamp,
    CONSTRAINT unique_match UNIQUE (home_team_id, away_team_id)
);

create table if not exists weeks
(
    id         serial primary key,
    is_current boolean,
    name       varchar(255),
    season_id  integer
);

create table if not exists teams
(
    public_id integer unique,
    code      varchar(255),
    name      varchar(255),
    logo      varchar(255)
);

create table if not exists predict
(
    user_id         integer,
    match_id        integer,
    home_team_score integer,
    away_team_score integer,
    points          integer,
    CONSTRAINT unique_predict UNIQUE (user_id, match_id)
);

create table if not exists users
(
    id          serial primary key,
    login       varchar(255) unique,
    password    varchar(255),
    role        varchar(255),
    telegram_id varchar(255)
);



