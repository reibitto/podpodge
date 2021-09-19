ALTER TABLE configuration
    ADD downloader_path text null;

ALTER TABLE episode RENAME TO temp_episode;

CREATE TABLE episode
(
    id              integer primary key autoincrement,
    podcast_id      integer not null,
    guid            text    not null,
    external_source text    not null,
    title           text    not null,
    publish_date    text    not null,
    image           text,
    media_file      text,
    duration        integer,
    foreign key (podcast_id) references podcast (id) on delete cascade
);

INSERT INTO episode (id, podcast_id, guid, external_source, title, publish_date, image, media_file, duration)
SELECT id, podcast_id, guid, external_source, title, publish_date, image, media_file, duration
FROM temp_episode;

DROP TABLE temp_episode;
