# Podpodge

## What is it?

Podpodge is a server + client for converting YouTube playlists into audio-only RSS feeds that podcast apps can consume.

Podpodge is written using [akka-http](https://doc.akka.io/docs/akka-http/current/index.html) + [ZIO](https://zio.dev) + [Quill](https://getquill.io/). It's still a work in progress in the sense that it doesn't
have a convenient front-end yet (a Scala.js + [Slinky](https://slinky.dev/) frontend will be coming). Podpodge is usable in its current state, but
it currently only exposes API routes that you have to call yourself for interacting with the DB and getting the RSS feed.

## Requirements

- You need to obtain a [YouTube API Key](https://developers.google.com/youtube/registering_an_application) and set
the `PODPODGE_YOUTUBE_API_KEY` environment variable.
- [youtube-dl](https://github.com/ytdl-org/youtube-dl/blob/master/README.md) must be installed (there's an [open issue](https://github.com/reibitto/podpodge/issues/6) for automatically downloading it)

## Usage

Run the server either using sbt (`sbt run`) or create an executable jar (`sbt assembly`) and run that. This will run the
Podpodge server at http://localhost:8080 by default (this can be changed with `PODPODGE_HOST` and `PODPODGE_PORT`). For
example, you might want to change `PODPODGE_HOST` to your network IP (like 192.168.1.100 or whatever it's set to) so that
you can access it from your phone on the same local network. Or properly host it on a "real" server if you'd like. 😉 

To register a YouTube playlist as a Podcast, call the following route:

```bash
curl -X POST http://localhost:8080/podcast/{YOUTUBE_PLAYLIST_ID}
```

(`YOUTUBE_PLAYLIST_ID` is what appears in the address bar when visiting a YouTube playlist page, like https://www.youtube.com/playlist?list=YOUTUBE_PLAYLIST_ID)

If successful, this should return you a JSON response of the Podcast. Using the ID returned (should be `1` if running for the first time),
you can call the `check` route to check for and download the episodes:

```bash
curl -X POST http://localhost:8080/podcast/1/check
```

(Note: There is an [issue](https://github.com/reibitto/podpodge/issues/8) for setting up CRON-like schedules per Podcast for automatic checks)

Once that's done, you can access the RSS feed URL and put it into whatever podcast app you use. It'll look something like http://localhost:8080/podcast/1/rss

## Contributing

Podpodge is fairly barebones and I mainly made it for myself because similar apps I tried at the time didn't quite work for me.
Plus, this was an exercise to learn how akka-http + ZIO + Quill (and eventually Slinky) work together. There are a bunch
more features that could potentially be added and I created some issues for those. Feel free to take any if you'd like.
Contributions are always welcome! 
