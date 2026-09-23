# ATTRIBUTION — rāyik (ArchiveTune-direct base)

Provenance record for the playback core. rāyik is a fork of
ArchiveTune, vendored **without git history**, so this file is the
paper trail. Keep it accurate.

## Upstream base

- Project: **ArchiveTune** (`rukamori/ArchiveTune`) — high-performance,
  privacy-focused YouTube Music client for Android (GPL-3.0, Kotlin,
  Compose M3, Hilt, Media3, Ktor, Room; minSdk 26, target/compile 37).
- Vendored from: `rukamori/ArchiveTune @ main 9ed48d8`
  plus submodules `core 04672ef`, `lyrics 8c00f93`,
  `morideobfuscator 2108e61`.
- Method: shallow clone + file copy (no `.git`, no history). Every
  vendored source file keeps its original per-file copyright header —
  do NOT remove or alter those notices (GPL-3.0 §4/§5).

## Copyright holders (all must stay credited)

1. **ArchiveTune (2026) | Original work by © Rukamori** — playback
   foundations, InnerTube core, decipher, UI, and everything vendored.
2. **rāyik contributors** — rebrand (`app.rayik.music`), scope strip,
   rayik UI, LRC parser (all tracked from the fresh root commit
   forward).

ArchiveTune itself acknowledges Metrolist (base framework), SimpMusic
(lyrics provider), and BetterLyrics — that credit travels with the
code above.

User-facing credits live in README.md and the in-app About screen
(`AboutAttributionRepository` points at `Aryan447/rayik`).

## Trademark (not GPL)

The **ArchiveTune™** name, logo, icon, and branding are NOT licensed
under the GPL — rāyik never presents itself as official ArchiveTune
and implies no endorsement by its maintainers.

## License: GPL-3.0-only

Upstream's LICENSE carries **no "or later" language** (verified), so
the combined work is **GPL-3.0-only**. rāyik's own notices match (see
README.md). The FSF GPLv3 text in LICENSE is unchanged.

## What was vendored (kept)

`app/` (incl. `db/` Room, `ui/theme`, `playback/MusicService`,
`utils/potoken`, `together/` dormant backend, `discord/` dormant
backend, `spotify/`), `core/` (InnerTube client), `lyrics/*`
(7 providers), `lastfm/`, `spotifycore/` (import/search/matching),
`morideobfuscator/` (youtubei signature decipher —
playback-critical), root Gradle files, `gradle/`, `buildSrc/`,
wrapper, `lint.xml`.

Stack as vendored: AGP 9.3.2, Kotlin 2.4.10, Media3 1.10.1,
Hilt 2.60.1, Ktor 3.5.1, Room 2.8.4, Java 21, minSdk 26,
target/compile 37.

## What was stripped at vendor time (v1 bans / out of scope)

Modules: `canvas/`, `shazamkit/`, `IconPack/`.
Source sets: `app/src/gms` (Cast + GMS auth), `app/src/tv`
(Leanback), `app/src/nightly`, `app/src/foss` extras,
`app/src/debug` Kotlin (LeakCanary).
Packages under `app/`: `widget/`, `musicrecognition/`, `aod/`,
`ai/`, `aicontentfilter/`, `backup/`, `podcast/`, `sponsorblock/`,
`playlistexport/`, `playlistimport/`, `lossless/`,
`mediainfo/`, `gatekeeper/`, `logcat/`, `privacy/`,
`viewmodels/`, full `ui/*` except `ui/theme/`, `ui/utils/`, and
`ui/screens/settings/ListenBrainzManager.kt` +
`DiscordPresenceManager.kt` (pure managers, not screens).
Files: `ui/player/Canvas*.kt`,
`ShowMediaInfo.kt` (dead with `viewmodels/` gone),
`RestoreBackupFileActivity`, `DebugActivity`.
Manifest: together deep-link, recognition tile/service, restore
activity, all widget receivers, Auto metadata, Discord OAuth
activity, DebugActivity, RECORD_AUDIO + mic foreground-service
permissions.
Gradle: gms/tv flavors, cast + glance + canvas/flac/shazam +
leakcanary deps, IconPack generator task.
Not vendored: `Koiverse.jks*`, server txt files, `fastlane/`,
`assets/`, upstream `.github/`, docs, README, PRIVACY.

Kept dormant (code ships, v1 hides): `together/` backend (session
defaults Idle, no UI entry point) and `discord/` backend (RPC
defaults OFF, no OAuth UI) — retained to avoid high-risk surgery in
playback-critical paths; no user-facing surface.

## Playback-hardening gap (honest)

The prior tree's Jio-class hardening (multi-client fallback walk,
concurrent verify probes, inline PO mint) is NOT in upstream and does
NOT ship here. ArchiveTune plays per its own releases
(`StreamClientUtils` + `StreamChunkResolver` + `potoken/` +
`morideobfuscator/youtubei`), but enforcement-network behavior must
be re-proven with the §1-style device repro before any "music plays"
claim. See plan.md.
