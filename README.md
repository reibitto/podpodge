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
- [youtube-dl](https://github.com/ytdl-org/youtube-dl) or [yt-dlp](https://github.com/yt-dlp/yt-dlp) must be installed (there's an [open issue](https://github.com/reibitto/podpodge/issues/6) for automatically downloading it)

_* The above are only requirements if `sourceType` is `youTube`. For `directory` you can ignore this._

## Usage

Run the server either using sbt (`sbt run`) or create an executable jar (`sbt assembly`) and run that. This will run the
Podpodge server at http://localhost:8080 by default (this can be changed with `PODPODGE_HOST` and `PODPODGE_PORT`). For
example, you might want to change `PODPODGE_HOST` to your network IP (like 192.168.1.100 or whatever it's set to) so that
you can access it from your phone on the same local network. Of course the other option is to host it on a "proper" public
server so that you can access it from anywhere.

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
