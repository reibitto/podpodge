# Podpodge

![Scala CI](https://github.com/reibitto/podpodge/actions/workflows/scala.yml/badge.svg)

## What is it?

Podpodge is a server + client for converting YouTube playlists (or plain audio files in a directory) into audio-only RSS
feeds that podcast apps can consume.

Podpodge is written using [pekko-http](https://pekko.apache.org/docs/pekko-http/current/) +
[tapir](https://tapir.softwaremill.com) + [ZIO](https://zio.dev) + [Quill](https://getquill.io/). It's still a work in
progress in the sense that it doesn't have the nicest front-end yet (a Scala.js + [Slinky](https://slinky.dev/)
front-end will be coming). Though it does have built-in Swagger integration so that you don't have to construct the API
requests yourself for interacting with the DB and getting the RSS feed.

## Requirements

- You need to obtain a [YouTube API Key](https://developers.google.com/youtube/registering_an_application) and set
the `PODPODGE_YOUTUBE_API_KEY` environment variable.
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (preferred) or [youtube-dl](https://github.com/ytdl-org/youtube-dl) must be
installed and on your `PATH`, or set with `PODPODGE_DOWNLOADER_PATH`. `ffmpeg`/`ffprobe` must also be installed (yt-dlp
uses it for audio extraction, and Podpodge uses it to determine episode durations).
- A JS runtime on your `PATH`. [deno](https://deno.com/) is yt-dlp's default and is recommended. YouTube increasingly
requires solving JS signature challenges, and without a runtime downloads become less reliable.

None of the above is required if you use the [Docker image](#docker), which bundles all of it.

_* The above are only requirements if `sourceType` is `youTube`. For `directory` you can ignore this._

## Usage

Run the server using sbt (`sbt run`), as a [Docker container](#docker), or as a standalone jar built with `sbt uberjar`
(run it with `java -jar`). This runs the Podpodge server at http://localhost:8080 by default (configurable with
`PODPODGE_HOST` and `PODPODGE_PORT`). For example, you might want to change `PODPODGE_HOST` to your network IP (like
192.168.1.100) so that you can access it from your phone on the same local network. The other option is to host it on a
public server so that you can access it from anywhere.

`PODPODGE_HOST` is the address used for every URL in the generated RSS feeds, so your podcast app must be able to reach
it. Podpodge also binds to it unless `PODPODGE_BIND_HOST` is set. The two need to differ when Podpodge can't bind to the
address clients use, most commonly in Docker, where it must listen on `0.0.0.0`. The Docker image sets
`PODPODGE_BIND_HOST=0.0.0.0` for you.

Podpodge stores its database and downloaded media under `data/`, relative to the working directory (`/opt/docker/data`
in the Docker image).

If YouTube rejects downloads as coming from a bot, set `PODPODGE_DOWNLOADER_COOKIES_FROM_BROWSER` to a locally installed
browser (e.g. `firefox`) so yt-dlp uses its cookies. See yt-dlp's
[`--cookies-from-browser`](https://github.com/yt-dlp/yt-dlp#filesystem-options) docs.

### Docker

Prebuilt images for `linux/amd64` and `linux/arm64` are published to GitHub Container Registry:

```
docker run -d --name podpodge -p 8080:8080 -v podpodge-data:/opt/docker/data \
  -e PODPODGE_YOUTUBE_API_KEY=... \
  -e PODPODGE_HOST=192.168.1.100 \
  ghcr.io/reibitto/podpodge:latest
```

Or with the [`docker-compose.yml`](docker-compose.yml) in this repo:

```
PODPODGE_HOST=192.168.1.100 PODPODGE_YOUTUBE_API_KEY=... docker compose up -d
```

Use `ghcr.io/reibitto/podpodge:0.2.0` (or `:0.2`) to pin a version instead of tracking `latest`.

The image bundles yt-dlp, ffmpeg and deno, and runs as a non-root user. The `-v` volume persists the database and
downloaded episodes when the container is recreated.

Set `PODPODGE_HOST` to the hostname or IP your podcast app will use to reach the container (the machine's LAN IP, or a
public domain). If left at the default, feed URLs point at `localhost` and only work on the host machine.

#### Building the image yourself

`sbt Docker/publishLocal` builds a single-architecture image for your machine and tags it `podpodge:latest`. Releases are
built for both architectures by [`.github/workflows/docker-publish.yml`](.github/workflows/docker-publish.yml) on `v*`
tags.

To register a YouTube playlist as a Podcast, call the `POST /podcast/{sourceType}` route (where `sourceType` can be set
to `youTube` or `directory`). You can do this with the built-in Swagger integration (which is the default top-level page).

The playlist ID is what appears in the address bar when visiting a YouTube playlist page, like https://www.youtube.com/playlist?list=YOUTUBE_PLAYLIST_ID

*Note:* Private playlists aren't supported (might be possible after [this issue](https://github.com/reibitto/podpodge/issues/1) is addressed). Using unlisted playlists is the closest alternative for now.

If successful, this should return you a JSON response of the Podcast. You can then use the `POST /podcasts/check` route to check for new episodes:

(*Note:* There is an [issue](https://github.com/reibitto/podpodge/issues/8) for setting up CRON-like schedules per Podcast for automatic checks)

Once that's done, you can access the RSS feed URL and put it into whatever podcast app you use. It'll look something like this (the ID may be different if you have multiple podcasts):
http://localhost:8080/podcast/1/rss

## Contributing

Podpodge is fairly barebones and I mainly made it for myself because similar apps I tried at the time didn't quite work for me.
Plus, this was an exercise to learn how akka-http/pekko-http + ZIO + Quill (and eventually Slinky) work together. There are a bunch
more features that could potentially be added and I created some issues for those. Feel free to take any if you'd like.
Contributions are always welcome! 
