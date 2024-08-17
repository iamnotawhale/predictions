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
    CONSTRAINT unique_match UNIQUE (home_team_id, away_team_id, local_date_time)
);

create table if not exists weeks
(
    id         integer,
    is_current boolean,
    name       varchar(255),
    season_id  integer
);

create table if not exists standing
(
    team_id       integer unique,
    games         integer,
    won           integer,
    draw          integer,
    lost          integer,
    goals_against integer,
    goals_scored  integer,
    points        integer

);

create table if not exists teams
(
    public_id integer unique,
    code      varchar(255),
    name      varchar(255),
    logo      varchar(255)
);

create table if not exists points
(
    user_id integer unique,
    value   integer
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



