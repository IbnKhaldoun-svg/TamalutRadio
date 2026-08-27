# TamalutRadio — PROJECT_SPEC.md

Single source of truth for TamalutRadio. **Update this file before code changes.**

## Current decisions

- UI: **Atlas Night** base + Material 3 / Material Air ergonomics + restrained Sahara Pulse accents.
- Theme: **Follow system** by default, with manual `Chiaro / Scuro / Segui sistema` override persisted locally.
- **Logo/Icona: APPROVATO — Atlas Signal, tutti gli asset sorgente committati.**
- Icon proportions: generous safe-area margins based on refinement #3; verified for circle, squircle and rounded-square adaptive masks.
- Android: native Kotlin + Jetpack Compose; **minSdk 26 / compileSdk 37 / targetSdk 37**.
- Build baseline: **AGP 9.3.0 / Gradle 9.5.0 / JDK 17 / Compose BOM 2026.08.00**.
- Playback: **Media3 1.11.0**, `MediaLibraryService` / `MediaLibrarySession`, ExoPlayer-native audio focus.
- Persistence: **Room + DataStore** with separate responsibilities.
- Home widget: **Jetpack Glance**.
- Android Auto: browsable media library through the Media3 service; no distracting custom car UI.
- Google Drive OAuth/API setup: **deferred** until the Drive feature implementation phase.
- Radio Tachlit Sous Massa: placeholder remains until a reliable identifying reference is provided.
- Approved extra features: **Recently played** and **automatic fallback stream URLs**.
- Distribution: signed APK via GitHub Releases / sideload; zero mandatory paid services or subscriptions.

## Approved UI / theme requirements

The final UI uses centralized Material 3 design tokens in `:core:designsystem`.

Dark palette:
- AMOLED-friendly anthracite/black surfaces.
- sand/gold primary accent.
- terracotta + deep Atlas green secondary accents.

Light palette:
- warm off-white / sand-tinted surfaces.
- same accent family as dark mode.
- Material 3 accessibility/contrast preserved.

Main destinations:
- Radio
- Music (local / Drive)
- Now Playing
- Settings

Languages:
- Italian
- Arabic (RTL)
- French

## Approved branding source assets

Store source assets under `assets/branding/` (not yet in Android `mipmap-*`):

- `atlas-signal-master-1024.png`
- `atlas-signal-adaptive-foreground-1024.png`
- `atlas-signal-adaptive-background-1024.png`
- `atlas-signal-monochrome-1024.png`

All four approved source assets are committed. Android launcher density resources and adaptive-icon XML will be generated later from these approved sources without changing the approved proportions.

## Architecture / module graph

Approved initial modules:

- `:app`
- `:core:model`
- `:core:data`
- `:core:database`
- `:core:preferences`
- `:core:playback`
- `:core:designsystem`
- `:feature:radio`
- `:feature:library`
- `:feature:nowplaying`
- `:feature:settings`
- `:feature:drive`
- `:feature:widget`

The initial bootstrap must provide a minimal launchable `MainActivity` placeholder so CI can verify the project compiles before feature implementation.

## Playback requirements

- Internet radio and local/Drive media through Media3/ExoPlayer.
- Playback continues with screen off / lock screen / other apps, subject to Android lifecycle constraints.
- Foreground media service + MediaSession system controls.
- Correct audio focus/ducking with Waze and Google Maps prompts.
- Stream reconnect/retry and bounded fallback URLs.
- Sleep timer.
- Simple equalizer/presets with graceful device/API fallback.

## Radio catalog — initial approved base

| Station | Initial stream |
|---|---|
| Radio Azawan | `https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3` |
| Radio Plus Agadir 92.4 | `https://stream-158.zeno.fm/bqdbb6hd0neuv` |
| HIT RADIO Maroc | `https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3` |
| Radio Mars | `https://radiomars.ice.infomaniak.ch/radiomars-128.mp3` |
| Aswat FM | `https://broadcast.ice.infomaniak.ch/aswat-high.mp3` |
| MFM Radio | `https://a5.asurahosting.com:7980/radio.mp3` |
| Medina FM Amazigh | `https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3` |
| Radio Italia Solo Musica Italiana | `https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8` |
| Radio Sportiva | `https://sportiva.inmystream.it/stream/sportiva` |

**Radio Tachlit Sous Massa** remains an explicit placeholder. Do not silently substitute another station.

Radio requirements:
- favorites first.
- add/remove favorites.
- add custom station URL.
- validate errors clearly.
- ordered fallback URLs per station where available.
- `lastVerifiedAt`/health metadata where practical.

## Local music

Use Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), persist URI access, recursively scan audio files inside the selected tree only, and build the playlist without broad storage permission.

## Google Drive

Deferred. Planned direction remains Drive REST API v3 + modern Google authorization, least-privilege scope compatible with the final picker UX, authenticated Media3 reads, and no paid quota/billing without explicit approval.

## Persistence

DataStore:
- language
- theme mode
- selected local folder URI
- selected Drive folder ID when implemented
- last station/media item
- sleep timer defaults
- equalizer/UI preferences

Room:
- default/custom stations
- favorites
- fallback URLs
- recently played history
- optional cached media metadata

## Release / signing requirements

Release tags use semantic versioning (`vMAJOR.MINOR.PATCH`). GitHub Actions must build the signed APK and attach it to Releases with a SHA-256 checksum.

Expected repository secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Never commit the keystore or passwords.

## Approved additional features

### Recently played

Persist a bounded local history for stations and applicable tracks, most recent first.

### Automatic fallback stream

Try the primary endpoint first; after qualifying playback/network failures, try ordered fallbacks with bounded attempts. If all fail, expose a clear final error.

## Proposed but NOT approved

Do not implement yet:
- station JSON backup/export/import.
- Drive Wi-Fi-only mode.
- separate Android Auto mini-favorites.
- per-station volume normalization.
- sleep timer “end of track”.
- stream diagnostics screen.

## Implementation status

### Phase 0 — decisions
- [x] UI selected.
- [x] Logo/Icona selected.
- [x] **Atlas Signal variant 3 / adaptive margins approved.**
- [ ] Exact Radio Tachlit Sous Massa reference supplied.

### Phase 1 — foundation
- [x] Technical baseline approved.
- [x] Initial modular Gradle/Compose bootstrap authorized.
- [x] Branding source files committed under `assets/branding/`.
- [ ] Gradle wrapper and version catalog committed.
- [ ] Module graph committed.
- [ ] Minimal app build verified by CI.
- [ ] Release signing plumbing verified.

## Decision log

### 2026-08-27 — UI / architecture / scope

Approved Atlas Night + Material 3 + Sahara Pulse accents; system/light/dark theme modes; MediaLibraryService; minSdk 26 / compileSdk 37 / targetSdk 37; Media3 1.11.0; Room + DataStore; ExoPlayer-native audio focus; Glance Home widget; initial radio catalog; recently played; automatic stream fallbacks. Google Drive setup deferred.

### 2026-08-27 — Branding final approval

**Logo/Icona: APPROVATO — Atlas Signal, tutti gli asset sorgente committati.** Variant 3 and its generous adaptive-icon safe-area margins remain the approved proportions. Source files are committed under `assets/branding/` as master, adaptive foreground, adaptive background, and monochrome/themed assets.

### 2026-08-27 — Gradle/Compose bootstrap authorized

Authorized baseline: AGP 9.3.0, Gradle 9.5.0, JDK 17, compileSdk/targetSdk 37, minSdk 26, Compose BOM 2026.08.00, Media3 1.11.0, the approved modular structure, and a minimal launchable `MainActivity` for build verification.
