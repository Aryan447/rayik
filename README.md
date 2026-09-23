# rāyik — music with opinions

[![CI Build](https://github.com/Aryan447/rayik/actions/workflows/build.yml/badge.svg)](https://github.com/Aryan447/rayik/actions/workflows/build.yml)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/Aryan447/rayik)](https://github.com/Aryan447/rayik/releases)

> **Opinionated music streaming — sane defaults, pro-level control.**
> Opinionated for everyday listening, fully customizable for pros.

rāyik (`raay` [opinion] + `rasik` [connoisseur]) is an unofficial,
community-driven YouTube Music client for Android. It tells you what it
thinks and why — you always get the last word.

> [!IMPORTANT]
> **Disclaimer.** rāyik is an independent, community-driven project. It is
> **not** affiliated with, endorsed by, or connected to Google LLC, YouTube,
> or YouTube Music in any way. It uses no official API key. Use it at your
> own discretion — and keep a YouTube Music subscription if you want to
> support the artists you hear.

## Features (v1)

- **Search** YouTube Music — songs, artists, albums — with tap-to-play
- **Raay home** — an opinionated morning pick with its reason attached
- **Player queue** — gapless Media3 playback, repeat/shuffle, seek, retry
- **Lyrics view** — synced follow-along where available
- **Pinned offline** — capped pins for tunnels and flights, transient cache
- **Quality toggle** — Data saver / Auto / High, honored per rendition
- **15 themes** — Gold first, Hacker last, an Indian street-color cluster in
  between; light / dark / AMOLED with picker preview and keyword search

## Install

rāyik is **not on the Google Play Store** and never will be as an
unofficial client. Get it from:

- **GitHub Releases** — signed APKs attached to every `v*` tag
- **F-Droid** — submission planned (see roadmap)

Requires Android 8.0 (API 26)+. Allow *Install unknown apps* for your
browser when sideloading.

## Build from source

```bash
./gradlew :app:testFossMobileUniversalDebugUnitTest   # unit tests
./gradlew assembleFossMobileUniversalRelease          # release APK (debug-signed with -PciDebugSigning in CI)
```

Requirements: JDK 21, Android SDK (compile/target 37, min 26).
No global Gradle install needed — use the wrapper. Signing happens via CI
secrets only; never commit keystores or credentials.

Optional: an InnerTube API key improves resolution resilience. Provide it
via env `YOUTUBE_INNERTUBE_API_KEY`, `-PinnertubeApiKey`, or
`local.properties` (`innertube.apiKey`). The app works without one.

## How it plays (honest version)

1. Search runs over YouTube Music's InnerTube `search` endpoint.
2. Resolution runs **on your device** through the ArchiveTune playback
   pipeline (`StreamClientUtils` + `StreamChunkResolver`): InnerTube
   player requests, BotGuard PO-token mint, NewPipe + youtubei signature
   decipher, and bot-detection recovery. URLs are transient and handed
   straight to ExoPlayer; nothing is ripped or stored permanently.

YouTube actively fights unofficial clients (bot-checks, per-network 403s),
so playback can break without a code change on our side. The app reports
honest errors and logs resolve + playback failures to logcat —
include those lines in bug reports.

## Tech stack

Kotlin · Jetpack Compose M3 · Media3 1.10.1 ExoPlayer
(`MediaLibraryService`, OkHttp + transient cache) · Hilt · Room ·
Coil · Coroutines / StateFlow · JUnit. Playback core forked from
ArchiveTune — see [ATTRIBUTION.md](ATTRIBUTION.md).

## Project structure

```
app/            Android app (Kotlin, Compose, Media3, Hilt, Room)
core/           InnerTube client + page parsers
database/       Room entities/DAOs
lyrics/         lyric providers (lrclib primary)
spotifycore/    Spotify import/search/matching
moriextractor/ + morideobfuscator/  signature decipher (playback-critical)
docs/           static coming-soon site (GitHub Pages, no build)
gradle/         version catalog (libs.versions.toml)
.github/        CI (unit tests + release assemble/bundle) and releases
```

## Roadmap

- Extractor hardening (client fallback rotation, signature/`n`-param handling)
- Optional Google login (captured session cookies) for library/sync
- Spotify playlist import → resolve via streaming layer
- F-Droid metadata + submission
- See `plan.md` for the full phased plan

## Contributing

PRs welcome — start with [CONTRIBUTING.md](CONTRIBUTING.md). Small scope, strict gates, no silent failures.

## License

[GPL-3.0-only](LICENSE) © 2026 aryan447 — narrowed from -or-later at the
playback-core vendor (upstream grants GPL-3.0 with no "or later" language).
Playback core: ArchiveTune © Rukamori, same
license — see [ATTRIBUTION.md](ATTRIBUTION.md).
