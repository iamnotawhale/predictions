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
    espn_id varchar(20),
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

alter table match
    add column if not exists live_score_message_id integer;

alter table match
    add column if not exists odd_home numeric(6, 2);

alter table match
    add column if not exists odd_draw numeric(6, 2);

alter table match
    add column if not exists odd_away numeric(6, 2);

alter table match
    add column if not exists odd_over_under numeric(6, 2);

alter table match
    add column if not exists odd_home_team_total numeric(6, 2);

alter table match
    add column if not exists odd_away_team_total numeric(6, 2);

alter table match
    add column if not exists finished_at timestamp;

alter table predict
    add column if not exists bonus_match_id integer;

create table if not exists bonus_match
(
    public_id             integer primary key,
    competition           varchar(64) not null,
    espn_id               varchar(32) not null,
    home_team_id          integer,
    away_team_id          integer,
    home_name             varchar(255),
    away_name             varchar(255),
    home_logo_url         varchar(512),
    away_logo_url         varchar(512),
    home_espn_code        varchar(16),
    away_espn_code        varchar(16),
    home_team_score       integer,
    away_team_score       integer,
    result                varchar(16),
    status                varchar(32),
    local_date_time       timestamp,
    finished_at           timestamp,
    live_score_message_id integer,
    constraint unique_bonus_match_espn unique (competition, espn_id)
);

create table if not exists user_week_bonus_match
(
    user_id         integer   not null,
    week_id         integer   not null,
    match_public_id integer   not null,
    created_at      timestamp not null default now(),
    primary key (user_id, week_id)
);

create table if not exists notification_weekly_results_sent
(
    week_id integer primary key,
    sent_at timestamp not null
);

create table if not exists notification_reminder_sent
(
    user_id integer,
    match_public_id integer,
    reminder_minutes integer,
    sent_at timestamp not null,
    primary key (user_id, match_public_id, reminder_minutes)
);

alter table users
    add column if not exists betting_recommender_enabled boolean default false;

create table if not exists footystats_team_stats
(
    week_id integer not null,
    team_code varchar(8) not null,
    scored_overall numeric(6, 2),
    scored_home numeric(6, 2),
    scored_away numeric(6, 2),
    conceded_overall numeric(6, 2),
    conceded_home numeric(6, 2),
    conceded_away numeric(6, 2),
    xg_overall numeric(6, 2),
    xg_home numeric(6, 2),
    xg_away numeric(6, 2),
    xga_overall numeric(6, 2),
    xgd_overall numeric(6, 2),
    extended_json text,
    fetched_at timestamp not null,
    primary key (week_id, team_code)
);

create table if not exists footystats_league_snapshot
(
    week_id integer primary key,
    avg_home_scored numeric(6, 2),
    avg_away_scored numeric(6, 2),
    avg_home_conceded numeric(6, 2),
    avg_away_conceded numeric(6, 2),
    fetched_at timestamp not null
);

create table if not exists match_recommendation
(
    match_public_id integer primary key,
    week_id integer not null,
    recommended_home integer not null,
    recommended_away integer not null,
    expected_home_goals numeric(6, 3) not null,
    expected_away_goals numeric(6, 3) not null,
    score_probability numeric(8, 6),
    explanation_json text not null,
    computed_at timestamp not null,
    kickoff_home integer,
    kickoff_away integer,
    kickoff_frozen_at timestamp
);



