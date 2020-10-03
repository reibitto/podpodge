CREATE TABLE podcast
(
    id              integer primary key autoincrement,
    external_source text not null,
    title           text not null,
    description     text not null,
    category        text not null,
    generator       text not null,
    last_build_date text not null,
    publish_date    text not null,
    author          text not null,
    subtitle        text not null,
    summary         text not null,
    image           text,
    last_check_date text
);

CREATE TABLE episode
(
    id              integer primary key autoincrement,
    podcast_id      integer not null,
    guid            text    not null unique,
    external_source text    not null,
    title           text    not null,
    publish_date    text    not null,
    image           text,
    media_file      text,
    duration        integer,
    foreign key (podcast_id) references podcast (id) on delete cascade
);