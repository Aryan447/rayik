# rāyik — music with opinions

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
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew assembleRelease          # release APK (debug-signed with -PciDebugSigning in CI)
```

Requirements: JDK 17, Android SDK (compile 37, target 36, min 26).
No global Gradle install needed — use the wrapper. Signing happens via CI
secrets only; never commit keystores or credentials.

Optional: an InnerTube API key improves resolution resilience. Provide it
via env `YOUTUBE_INNERTUBE_API_KEY`, `-PinnertubeApiKey`, or
`local.properties` (`innertube.apiKey`). The app works without one.

## How it plays (honest version)

1. Search runs over public Piped instances (keyless, on-device HTTPS).
2. Resolution runs **on your device** through YouTube's InnerTube `player`
   endpoint — no backend, no key required. URLs are transient and handed
   straight to ExoPlayer; nothing is ripped or stored permanently.
3. Piped `/streams` is the fallback when direct resolution is bot-blocked.

YouTube actively fights unofficial clients (bot-checks, per-network 403s),
so playback can break without a code change on our side. The app reports
honest errors with the HTTP status and logs resolve + playback failures to
logcat under `RayikPlayer` — include those lines in bug reports.

## Tech stack

Kotlin · Jetpack Compose M3 · Media3 ExoPlayer (`MediaSessionService`,
OkHttp + transient cache) · Koin · Room · Navigation3 · Coil · Coroutines /
StateFlow · JUnit (pure, JVM-testable rules for selection, queue, errors)

## Project structure

```
app/            Android app (Kotlin, Compose, Media3, Koin, Room)
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

[GPL-3.0-or-later](LICENSE) © 2026 aryan447.
