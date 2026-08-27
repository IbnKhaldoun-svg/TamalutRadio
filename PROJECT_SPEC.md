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

### `:core:designsystem` implementation contract

The first design-system module is authorized with these responsibilities only:

- centralize Atlas Night semantic color tokens and Material 3 `ColorScheme` definitions.
- expose a `ThemeMode` with `FOLLOW_SYSTEM`, `LIGHT`, and `DARK` values corresponding to `Segui sistema`, `Chiaro`, and `Scuro`.
- expose `TamalutRadioTheme(themeMode, content)`; `FOLLOW_SYSTEM` resolves through Compose `isSystemInDarkTheme()`.
- use Atlas Night dark background `#0E1116`, with elevated/surface anthracites derived from the same family.
- use sand/gold as the primary brand accent, deep Atlas green as secondary, and terracotta as tertiary/accent.
- use warm off-white / sand-tinted light surfaces while keeping readable Material 3 foreground contrast.
- keep Material Air ergonomics as a visual/interaction direction implemented through Material 3 tokens (comfortable rounded shapes and restrained hierarchy), not as a separate dependency.
- do not enable dynamic color in this first implementation because it would replace the approved Atlas Night identity.
- theme preference persistence remains the responsibility of the future `:core:preferences` module; this module only models and applies the requested mode.
- integrate the existing placeholder `:app` with `TamalutRadioTheme(ThemeMode.FOLLOW_SYSTEM)` so the design system is exercised by the real debug build.

Approved initial semantic palette anchors:

Dark:
- background `#0E1116`
- surface `#161B22`
- surface variant `#222831`
- sand/gold `#D8B36A`
- Atlas green `#4F8A73`
- terracotta `#C66A46`

Light:
- background `#FFF8EC`
- surface `#FFFBF5`
- surface variant `#F3EBDD`
- primary dark-gold `#705A12`
- Atlas green `#2F6B57`
- terracotta `#99462F`

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

### `:core:model` implementation contract

The next foundation module is authorized as a pure Kotlin/JVM module with no Android, Room, DataStore, Media3, or Hilt dependencies. It owns shared domain models only:

- typed identifiers for radio stations and generic media items.
- `StreamEndpoint` with a validated non-blank URL string.
- `RadioStation` with a primary endpoint and ordered fallback endpoints, preserving playback priority.
- base media types covering radio stations, local tracks, and future Drive tracks without implementing storage or playback behavior.
- `RecentlyPlayedEntry` using a media summary plus an epoch-millisecond timestamp, leaving persistence to later modules.
- small invariant checks and unit tests for identifier/endpoint validity and fallback ordering.

No database annotations, Android framework types, serialization framework, persistence APIs, network APIs, or playback APIs belong in `:core:model`. Verification requirement: `./gradlew :core:model:test :app:assembleDebug` must succeed on GitHub Actions before the code reaches `main`.

### `:core:preferences` implementation contract

The next foundation module is authorized as an Android library backed by AndroidX Preferences DataStore 1.2.1. Its first scope is intentionally limited to lightweight user preferences:

- persist theme preference as `FOLLOW_SYSTEM`, `LIGHT`, or `DARK`, with `FOLLOW_SYSTEM` as the safe default.
- persist an optional BCP-47 language tag for the future Italian / Arabic / French language selector.
- persist the last played source type and identifiers needed to resume the last radio station or media item.
- expose preferences as a Kotlin `Flow` plus explicit suspend setters; no UI belongs in this module.
- depend on `:core:model` for typed station/media identifiers and source type, but do not depend on `:core:designsystem`; the app composition layer maps the stored theme preference to `ThemeMode`.
- integrate `:app` so its existing `TamalutRadioTheme` reads the persisted theme mode instead of hard-coding `FOLLOW_SYSTEM`.
- keep Room, Media3, Hilt, station catalog data, recently-played history, sleep timer, equalizer settings, local-folder URI, and Drive-folder ID out of this first preferences commit.
- handle unknown enum strings defensively by falling back to defaults rather than crashing.
- no analytics, network access, cloud synchronization, or paid service dependency.

Verification requirement: unit tests for preference decoding/defaults must pass and GitHub Actions must successfully run `./gradlew :core:preferences:testDebugUnitTest :app:assembleDebug` before the code reaches `main`.

### `:core:database` implementation contract

The next foundation module is authorized as an Android persistence library using stable Room 3.0.2 (`androidx.room3`) with KSP 2.3.10. Its scope is deliberately limited to schema, Room entities/DAO, and persistence/domain mapping helpers:

- persist preinstalled and custom radio stations corresponding to `:core:model` `RadioStation`, including primary stream URL and ordered fallback stream URLs.
- distinguish custom stations from the preinstalled catalog without embedding the catalog itself in this module.
- persist station favorites separately so favorite state is not mixed into the shared domain model.
- persist/cache recently-played media metadata required by the approved local history: media ID, source type, title/subtitle, optional station ID, and last-played epoch-millisecond timestamp.
- provide Room DAO operations for station/fallback CRUD, favorite add/remove/query, and recently-played upsert/query/delete/trim primitives.
- expose explicit mapping helpers between Room station aggregates / recently-played entities and the existing `:core:model` types.
- database schema version starts at 1 and Room schema JSON must be exported/committed for future migration verification.
- no repository implementation, dependency injection, UI, playback, networking, catalog seeding, Media3, DataStore, or Hilt belongs in this module; repository orchestration is deferred to `:core:data`.
- compile-time Room SQL verification is mandatory; unit tests must cover domain/entity mapping invariants and fallback ordering.

Verification requirement: GitHub Actions must successfully run `./gradlew :core:database:testDebugUnitTest :app:assembleDebug` and produce a real debug APK before the code reaches `main`.

### `:core:data` implementation contract

The next foundation module is authorized as the repository/orchestration layer between `:core:model` and `:core:database`. Its scope is intentionally limited to local data coordination:

- expose repository contracts/implementations for radio stations, favorites, and recently-played history using the existing Room DAO layer and domain models.
- seed the approved initial catalog of nine radio stations from `PROJECT_SPEC.md` exactly once/idempotently, while keeping Radio Tachlit Sous Massa excluded because it remains an unresolved placeholder.
- preserve primary stream URLs and ordered fallback URLs through the existing persistence mappings.
- support custom station persistence through repository APIs without embedding UI validation flows.
- expose favorites with favorite stations ordered first when requested by consumers, while keeping favorite state outside the shared `RadioStation` model.
- record/query/clear a bounded recently-played history through the existing database primitives, dropping corrupt/unknown cached rows safely.
- depend on `:core:model` and `:core:database`; no Media3, ExoPlayer, playback service, Google Drive, networking client, Hilt, Compose, or feature UI belongs in this module.
- repository unit tests must cover idempotent catalog seeding, favorite coordination, fallback ordering, custom-station persistence, and recently-played bounds/mapping.

Verification requirement: GitHub Actions must successfully run `./gradlew :core:data:testDebugUnitTest :app:assembleDebug` and produce a real debug APK before the code reaches `main`.

### `:core:playback` implementation contract — sub-step 1/3

The playback foundation is intentionally split into three verified commits. Sub-step 1 establishes the service/session lifecycle only; automatic radio fallback and notification/media-button refinements remain for sub-steps 2 and 3.

Sub-step 1 responsibilities:

- create `:core:playback` as an Android library using stable Media3 1.11.0.
- host a single ExoPlayer inside a `MediaLibraryService` / `MediaLibrarySession`, not a plain `MediaSessionService`, so the service is ready for future Android Auto browsing.
- configure `AudioAttributes` with media usage/content type and `handleAudioFocus = true`; no custom audio-focus manager is added.
- accept generic Media3 `MediaItem` instances so future local-file items can be queued without changing the service architecture.
- expose a minimal browsable library root required by `MediaLibraryService`, without feature catalog wiring yet.
- define an explicit custom session command for Stop/Exit that stops playback, clears the queue, and terminates the playback service lifecycle safely.
- preserve Media3 default task-removal behavior: swiping the app away must not stop playback while playback is ongoing.
- declare foreground media-playback service permissions and both `androidx.media3.session.MediaLibraryService` and legacy `android.media.browse.MediaBrowserService` service actions.
- rely on Media3's standard session notification behavior in this foundation; explicit notification/button preference polishing is deferred to sub-step 3.
- do not add radio fallback retry logic yet; that is sub-step 2.
- no Google Drive integration, repository orchestration, UI, Hilt, sleep timer, equalizer, or feature modules belong in this commit.

Verification requirement: unit tests for pure playback command/configuration helpers plus GitHub Actions `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug` must succeed and produce a real debug APK before the code reaches `main`.

### `:core:playback` implementation contract — sub-step 2/3

Sub-step 2 adds bounded automatic radio-stream fallback on top of the verified MediaLibraryService foundation.

Responsibilities:

- depend on `:core:model` so playback can accept a `RadioStation` and preserve its exact `primaryStream -> fallbackStreams` order.
- introduce a small deterministic fallback state machine that tracks the current endpoint, attempted endpoints, and terminal success/exhausted state.
- expose a configurable maximum attempt count; the effective attempt budget is bounded by both this value and the station's number of available endpoints, preventing loops or repeated cycling.
- start radio playback from the primary endpoint and advance exactly one endpoint after a fatal Media3 `Player.Listener.onPlayerError` callback; non-fatal load events are left to ExoPlayer's own recovery policy.
- when an endpoint fails and another attempt remains, replace the current radio media item with the next endpoint, call `prepare()`, and preserve the caller's play intent.
- when the final allowed endpoint also fails, stop retrying, retain a terminal `EXHAUSTED` playback-fallback state with station ID / attempted count / last error code, and leave the fatal Media3 error visible to the MediaSession/controller rather than crashing or silently looping.
- local/generic Media3 `MediaItem` playback remains unaffected by radio fallback handling.
- no notification button customization, Android Auto catalog wiring, Google Drive, UI, Hilt, sleep timer, equalizer, or repository orchestration belongs in this sub-step.
- unit tests must simulate ordered fatal failures and verify primary -> fallback 1 -> fallback 2 progression, configurable attempt truncation, no retries after exhaustion, and explicit terminal error state.

Verification requirement: GitHub Actions must successfully run `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`, verify a real APK, and exercise the fallback unit tests before the code reaches `main`.

### `:core:playback` implementation contract — sub-step 3/3

Sub-step 3 finalizes system media controls and a minimal browsable Android Auto test library on top of the verified service/fallback foundation.

Responsibilities:

- keep Media3 `MediaLibraryService` / `MediaLibrarySession` as the single playback/session authority and use Media3's foreground media notification lifecycle rather than a parallel notification/player stack.
- publish explicit media-button preferences for play/pause, next, and the existing Stop/Exit custom session command so compatible SystemUI notification, lock-screen, Android Auto, and Media3 controller surfaces can expose the same session controls.
- authorize Stop/Exit only for the Media3 notification controller, Android Auto companion controller, and trusted controllers; invoking it must stop playback, clear the queue, reset fallback state, and stop the playback service.
- keep next functional by providing a small ordered test playlist of three real stations already present in `:core:data`'s seed catalog. The production `:core:playback` module must not depend on `:core:data`; a test-only dependency may assert that the temporary test catalog exactly matches the seed entries to prevent drift.
- refine the library tree to `TamalutRadio -> Radio di test -> playable station items`, with valid browsable/playable metadata and direct `getItem` lookup suitable for Media3 browsers and legacy Android Auto browsing.
- resolve browser/controller requests that contain only station media IDs into playable Media3 radio items and an ordered playlist, starting from the selected station, so Android Auto selection and the notification's Next control operate on the same queue.
- expose Radio Azawan as the primary manual verification station, using the exact seed ID/name/URL and the existing radio fallback media-item factory.
- wire `:core:playback` into the current `:app` APK and add only a temporary placeholder-level manual test action that connects through a Media3 browser/controller and prepares Radio Azawan paused; the real `feature:radio` UI remains deferred.
- the manual test action must not create a second player or bypass the service. Playback must start from the notification/lock-screen Play control after the test station has been prepared.
- keep Google Drive, full radio UI, Hilt, equalizer, sleep timer, favorites UI, and production catalog/repository orchestration out of this sub-step.
- unit tests must cover media-button definitions, Stop/Exit exposure policy, browsable root/category/station metadata, station lookup, ordered playlist resolution/Next semantics, and exact synchronization of the temporary test stations with the `:core:data` seed.

Verification requirement: GitHub Actions must successfully run `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`, verify a real APK containing the playback service, and perform a network smoke probe that receives bytes from the Radio Azawan seed stream before the code reaches `main`.


### `:feature:radio` implementation contract — sub-step 1/2

The first feature module is split into two verified commits. Sub-step 1 establishes the real radio catalog/favorites UI and local repository wiring; Media3 playback selection is completed in sub-step 2.

Responsibilities:

- create `:feature:radio` as a Compose Android library using the existing Atlas Night / Material 3 design system.
- expose a Radio screen with two sections/tabs: `Preferiti` and `Tutte le radio`.
- load data through the existing `RadioStationRepository` and `FavoriteStationRepository` from `:core:data`; do not duplicate catalog business logic in the feature.
- seed the approved initial station catalog idempotently before presenting the list.
- expose immutable UI state from a ViewModel and keep repository/database work off the composable layer.
- allow toggling favorites from each station row through a visible star/heart-style icon; UI state must refresh immediately after repository mutation.
- show a clear empty state when there are no favorites and a recoverable error state if initial loading fails.
- wire the real Room database and repositories from `:app` explicitly for now; Hilt remains deferred.
- replace the placeholder app content with the new Radio screen, while retaining the existing Atlas Night theme and persisted theme preference.
- station row selection is exposed as a callback but does not start Media3 playback in this sub-step; playback wiring and removal of the temporary `Prepara Radio Azawan` test path are reserved for sub-step 2/2.
- no Google Drive, custom-station editor, now-playing UI, sleep timer, equalizer, or other feature modules belong in this commit.
- unit tests must cover initial load/seeding, favorite toggling, favorites/all filtering, empty favorites, and repository failure state through lightweight fakes.

Verification requirement: GitHub Actions must successfully run `./gradlew :feature:radio:testDebugUnitTest :app:assembleDebug`, verify the debug APK, and ensure the feature is wired into the app before the code reaches `main`.


### `:feature:radio` implementation contract — sub-step 2/2

Sub-step 2 completes the feature by wiring station selection to the verified Media3 playback service.

Responsibilities:

- add a feature-level playback gateway whose production implementation connects to `TamalutPlaybackService` through Media3 `MediaBrowser`; no second player is created.
- selecting any repository-backed `RadioStation` must send a `RadioMediaItemFactory` media item to the MediaLibraryService, call `prepare()`, and start playback immediately.
- preserve the station's primary/fallback plan because playback receives the full domain station through the existing factory.
- expose playback connection/failure feedback in the Radio UI without crashing or blocking favorite interactions.
- remove the temporary `Prepara Radio Azawan` button and its test-only app callback/browser path; the Radio screen becomes the only app-level way to start radio playback.
- keep notification, lock-screen, Stop/Exit, Next, audio focus, fallback, and Android Auto behavior owned by `:core:playback`.
- retain Atlas Night theming, repository-backed tabs, and favorite toggling from sub-step 1/2.
- no Google Drive, custom-station editor, now-playing feature, Hilt, sleep timer, or equalizer belongs in this commit.
- unit tests must cover station-selection delegation, successful playback state, failure state, and ensure data/favorite state remains intact.

Verification requirement: GitHub Actions must successfully run `./gradlew :feature:radio:testDebugUnitTest :app:assembleDebug`, verify a real APK and the merged MediaLibraryService, before the code reaches `main`.

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

### `:feature:library` implementation contract — sub-step 1/2

The local-music feature is split into two verified commits. Sub-step 1 establishes SAF folder selection, durable access, recursive audio scanning, and the functional library list; playback wiring remains for sub-step 2.

Responsibilities:

- create `:feature:library` as an Android/Compose feature module.
- select exactly one user folder with Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE` / `OpenDocumentTree`; do not request broad storage/media permissions.
- persist the granted read URI permission with `takePersistableUriPermission` and persist the selected tree URI through the existing `:core:preferences` DataStore.
- restore the persisted tree URI on later launches and rescan it automatically when access remains valid.
- recursively enumerate only documents below the selected SAF tree using `ContentResolver` / `DocumentsContract`; include audio MIME types and safe audio-extension fallback, ignore non-audio files, and tolerate unreadable child documents without crashing the whole scan.
- represent scanned tracks with stable `:core:model` `MediaId` values derived from their document URI plus display title/URI metadata needed by the later playback step.
- expose a minimal Compose Library screen with folder-selection action, loading/empty/error states, and an ordered list of discovered tracks.
- keep track taps non-playing in this sub-step; Media3 playback and current-track indication are explicitly deferred to sub-step 2/2.
- add unit tests for audio filtering/order, recoverable scan failures, persisted-folder restoration, and ViewModel state transitions.
- no Google Drive, Room schema changes, Hilt, broad storage permission, or separate player belongs in this sub-step.

Verification requirement: GitHub Actions must successfully run `./gradlew :feature:library:testDebugUnitTest :app:assembleDebug` and verify the generated APK. Debug APK artifacts are not uploaded unless explicitly requested for physical testing.

### `:feature:library` implementation contract — sub-step 2/2

Sub-step 2 completes local music by wiring the verified SAF library to the existing Media3 playback service.

Responsibilities:

- keep `TamalutPlaybackService` / `MediaLibraryService` as the only player/session authority; `:feature:library` must not create an ExoPlayer or other secondary player.
- add a feature-level local playback gateway whose production implementation connects to the existing service through Media3 `MediaBrowser`.
- convert scanned `LocalAudioTrack` entries into generic Media3 `MediaItem` instances using their stable media IDs, SAF content URIs, titles, and MIME types where available; do not add radio-specific fallback metadata.
- tapping a local track must replace the current service queue with the full ordered scanned-track list, start at the tapped item, call `prepare()`, and start playback immediately.
- preserve the scanner's deterministic list order as the Media3 queue order so standard Previous/Next commands move through the selected folder playlist.
- observe Media3 media-item transitions from the shared session and expose the current local media ID to UI state so the matching row shows `In riproduzione`; when the shared player switches to a non-local item, no local row remains marked current.
- starting local playback must replace any radio item/queue already loaded in the shared player; starting radio playback must likewise replace the local queue, relying on the existing radio gateway's `setMediaItem` behavior and the single service/player.
- keep notification, lock-screen, audio focus, Stop/Exit, Android Auto session ownership, and playback lifecycle in `:core:playback`; local playback only supplies generic media items and queue selection.
- retain the existing SAF tree permission, persisted-folder restoration, scanning, refresh, and error behavior from sub-step 1/2.
- add unit tests for local MediaItem mapping/queue order, selected start index, playback success/failure state, current-track indication, and gateway delegation; verify structurally that both radio and local playback target the same `TamalutPlaybackService` and replace the active Media3 queue rather than creating parallel playback.
- no Google Drive, Room schema changes, Hilt, broad storage permission, separate player, sleep timer, equalizer, or unrelated navigation work belongs in this sub-step.

Verification requirement: GitHub Actions must successfully run `./gradlew :feature:library:testDebugUnitTest :feature:radio:testDebugUnitTest :app:assembleDebug`, verify a real debug APK and merged `TamalutPlaybackService`, and confirm the APK still requests no broad storage/media permission. The APK must not be uploaded as an artifact unless explicitly requested.

### Visual refinement plan — Atlas Night / Material Air / Sahara Pulse

The existing functional Radio and local Library features now enter a three-commit visual refinement pass. The established `:core:designsystem` theme remains the single palette/theme authority; this work refines composition and components without replacing theme persistence or playback/data architecture.

- [ ] UI polish sub-step 1/3: application shell and bottom navigation. Replace the provisional top buttons with a Material 3 bottom navigation bar for `Radio`, `Musica`, `In Riproduzione`, and `Impostazioni`; keep Radio and Musica wired to their real routes, and provide restrained Atlas Night placeholders for the latter two destinations. Preserve current ViewModel instances, playback services, repository wiring, system-following theme behavior, and edge-to-edge-safe content padding. Use Material icons and coherent destination labels, with selection styling driven by the existing Material 3 color scheme. Verification: `./gradlew :app:assembleDebug` plus structural checks for all four destinations and no playback/data regressions.

- [ ] UI polish sub-step 2/3: Radio visual refinement. Keep existing catalog, favorites, tabs, playback gateway, and errors unchanged while refining header hierarchy, spacing, station cards, leading radio icon treatment, favorite action, borders/elevation, and current-station emphasis. A currently playing radio station must display a compact `LIVE` badge and an accessible `In riproduzione` state. Visuals must use Atlas Night semantic colors through `MaterialTheme`, with restrained Sahara Pulse sand/gold, Atlas green, and terracotta accents rather than hard-coded unrelated colors. Verification: `:feature:radio:testDebugUnitTest :app:assembleDebug` and structural checks that playback/favorites behavior remains wired.

- [ ] UI polish sub-step 3/3: local Music visual refinement. Keep SAF selection/persisted permission/scanning/playback behavior unchanged while refining the screen header, selected-folder panel, actions, empty/loading/error presentation, local-track cards, leading music icon treatment, metadata hierarchy, borders/elevation, and current-track emphasis. The current local item must retain an accessible `In riproduzione` indication. Verification: `:feature:library:testDebugUnitTest :app:assembleDebug` plus merged-manifest checks confirming no broad storage permission was added.

Across all three sub-steps: do not add a second player, navigation framework migration, Google Drive implementation, Room changes, Hilt, sleep timer, equalizer, release signing changes, or APK artifact upload. Real debug APKs are built only for verification and are not published as GitHub Actions artifacts unless explicitly requested.

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
- [x] Gradle 9.5.0 wrapper committed and verified.
- [x] Root Gradle Kotlin DSL skeleton committed and verified with `./gradlew help`.
- [x] `:app` module with minimal Compose `MainActivity` and Atlas Signal launcher resources committed.
- [ ] Module graph committed.
- [x] Minimal app APK build verified by CI with `./gradlew :app:assembleDebug`.
- [ ] Release signing plumbing verified.
- [x] Temporary downloadable debug APK artifact published for device testing (Actions run `33073747137`, artifact `TamalutRadio-debug-apk`, 7-day retention).
- [x] Temporary bootstrap branches `branding-bootstrap`, `foundation-gradle-validate`, and `app-bootstrap-validate` removed.
- [x] Obsolete `wrapper-bootstrap` branch removed after its wrapper commit reached `main`.
- [x] `README.md` committed with project overview, GitHub Releases sideload instructions, and current development status.
- [x] `:core:designsystem` Atlas Night Material 3 light/dark theme implementation committed and verified by `./gradlew :app:assembleDebug`.
- [x] `:core:model` pure Kotlin shared domain models committed and verified with `:core:model:test` + `:app:assembleDebug`.
- [x] `:core:preferences` DataStore-backed theme/language/last-source preferences committed and verified with `:core:preferences:testDebugUnitTest` + `:app:assembleDebug`.
- [x] `:core:database` Room station/favorites/recent metadata persistence committed and verified with `:core:database:testDebugUnitTest` + `:app:assembleDebug`; Room schema v1 exported.
- [x] `:core:data` repository/seeding/favorites/recently-played orchestration committed and verified with `:core:data:testDebugUnitTest` + `:app:assembleDebug`.
- [x] `:core:playback` sub-step 1/3: MediaLibraryService / ExoPlayer foundation, native audio focus, generic MediaItem support, and explicit Stop/Exit committed and verified.
- [x] `:core:playback` sub-step 2/3: bounded automatic radio primary→fallback recovery committed and verified.
- [x] `:core:playback` sub-step 3/3: notification/media-button controls, browsable Android Auto test library, and manual on-device radio playback verification completed.
- [x] `:feature:radio` sub-step 1/2: repository-backed radio/favorites Compose screen committed and verified.
- [x] `:feature:radio` sub-step 2/2: station selection wired to real MediaLibraryService playback; temporary Radio Azawan test path removed and verified.
- [x] `:feature:library` sub-step 1/2: SAF folder selection, persisted tree URI, recursive audio scan, and functional local-library list committed and verified.
- [x] `:feature:library` sub-step 2/2: Media3 local-track playback, ordered queue Previous/Next, current-track indication, and shared-player radio/local replacement committed and verified.

## Decision log

### 2026-08-27 — General Atlas Night UI refinement authorized

Authorized a three-step aesthetic pass over the existing app: 1/3 Material 3 bottom navigation and four-destination shell, 2/3 Radio cards/header with a `LIVE` badge for the active station, and 3/3 local Music cards/header/folder presentation. The pass must preserve existing functional behavior and reuse the current `:core:designsystem` light/dark theme. `In Riproduzione` and `Impostazioni` may remain minimal placeholders in the shell step. Each code step requires a real debug APK build with no artifact upload.


### 2026-08-27 — Feature library Media3 playback sub-step 2/2 completed

`:feature:library` sub-step 2/2 is implemented with final validated code at `d071c3a216aa8b40bfcb5889f0fcb025b869c4a2` (initial implementation `b42d4b43bbf7ff549dd6d100bf8591258611a863`). Local SAF tracks are converted to generic Media3 items only at the playback boundary; tapping a row sends the full deterministic scanned list to the existing `TamalutPlaybackService` through `MediaBrowser.setMediaItems(...)`, starts at the tapped index, prepares, and plays. Media-item transitions from the shared session update the Library UI so the current local row shows `In riproduzione`; a non-local current media ID clears that marker. Radio and local playback both target the same MediaLibraryService player: local playback replaces the active queue with `setMediaItems`, while the existing radio gateway replaces it with `setMediaItem`, so switching source cannot leave parallel players running. Unit tests cover queue order/metadata/start index, gateway delegation, playback success/failure, and current-track transitions. GitHub Actions run `33114097614` passed `:feature:library:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, and `:app:assembleDebug`, verified the merged `TamalutPlaybackService`, confirmed no `ExoPlayer.Builder` exists in `:feature:library`, and confirmed the APK requests no `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE` permission. The real debug APK SHA-256 was `62993eb49aede1c0a9430ffedde165831bdd3a28b7435dde79cf0fc58e0dc362`; no APK artifact was uploaded. An earlier validation run exposed a JVM-only test coupling to Android `Uri`; the test seam was corrected before the clean code was promoted to `main`.


### 2026-08-27 — Feature library Media3 playback sub-step 2/2 authorized

Authorized `:feature:library` sub-step 2/2: scanned SAF tracks become generic Media3 items played through the existing `TamalutPlaybackService`; tapping a track installs the whole scanned list as the ordered queue at the selected index, enabling Previous/Next. The Library UI must track the current local media item through the shared session. Radio and local playback remain mutually exclusive because both replace the queue of the same MediaLibraryService player. No APK artifact is published unless explicitly requested.


### 2026-08-27 — Feature library SAF/scanning sub-step 1/2 completed

`:feature:library` sub-step 1/2 is implemented and committed in `f861eba53ab02ef2efb1b2221ff44fb773a86a54`. The app now exposes provisional Radio / Musica locale switching; the local screen selects one SAF tree with `OpenDocumentTree`, persists read access with `takePersistableUriPermission`, stores the selected tree URI in the existing `:core:preferences` DataStore, restores it on later launches, and recursively scans only in-tree audio documents with stable `MediaId` values. Unit tests cover recursive filtering/order, unreadable child folders, root-access errors, persisted-folder restoration, folder selection, and recoverable scan failures. GitHub Actions run `33108580230` passed `:core:preferences:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:assembleDebug`; the real debug APK SHA-256 was `6ecbd3a8bafd81d1c0f41218a4ab25ed1132a01463c1196794fbc5a320c7e821`. The merged manifest was verified to contain no `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, or `MANAGE_EXTERNAL_STORAGE` permission. No debug artifact was uploaded. Local-track Media3 playback and current-track indication remain sub-step 2/2.


### 2026-08-27 — Feature library SAF/scanning sub-step 1/2 authorized

Authorized `:feature:library` sub-step 1/2: SAF tree selection, durable read permission, DataStore persistence of the selected local-folder URI, recursive in-tree audio scanning, and a minimal functional track list. Playback remains sub-step 2/2. Development APK artifacts are now on-demand only; when explicitly requested, prefer 1–2 day retention rather than 7 days. CI for normal sub-steps verifies the APK without uploading it.


### 2026-08-27 — UI / architecture / scope

Approved Atlas Night + Material 3 + Sahara Pulse accents; system/light/dark theme modes; MediaLibraryService; minSdk 26 / compileSdk 37 / targetSdk 37; Media3 1.11.0; Room + DataStore; ExoPlayer-native audio focus; Glance Home widget; initial radio catalog; recently played; automatic stream fallbacks. Google Drive setup deferred.

### 2026-08-27 — Branding final approval

**Logo/Icona: APPROVATO — Atlas Signal, tutti gli asset sorgente committati.** Variant 3 and its generous adaptive-icon safe-area margins remain the approved proportions. Source files are committed under `assets/branding/` as master, adaptive foreground, adaptive background, and monochrome/themed assets.

### 2026-08-27 — Gradle/Compose bootstrap authorized

Authorized baseline: AGP 9.3.0, Gradle 9.5.0, JDK 17, compileSdk/targetSdk 37, minSdk 26, Compose BOM 2026.08.00, Media3 1.11.0, the approved modular structure, and a minimal launchable `MainActivity` for build verification.

### 2026-08-27 — App module bootstrap step

Next foundation change is limited to `:app`: application ID `com.tamalut.radio`, minSdk 26 / compileSdk 37 / targetSdk 37, minimal Compose placeholder UI, and launcher resources derived from the approved Atlas Signal branding sources. Verification requirement: GitHub Actions must successfully run `./gradlew :app:assembleDebug` and produce a debug APK before proceeding to `core/*` or `feature/*` modules.

### 2026-08-27 — Pre-designsystem maintenance

Before implementing `:core:designsystem`, complete three repository-maintenance steps: publish a temporary downloadable debug APK artifact for immediate device testing, remove the temporary bootstrap branches `branding-bootstrap`, `foundation-gradle-validate`, and `app-bootstrap-validate`, and add `README.md` with project description, GitHub Releases installation guidance, and current development status. The already committed `:app` module has been verified with a successful real `./gradlew :app:assembleDebug` build.

### 2026-08-27 — Pre-designsystem maintenance completed

Completed before `:core:designsystem`: downloadable debug APK artifact published from a successful `:app:assembleDebug` run; temporary branches `branding-bootstrap`, `foundation-gradle-validate`, and `app-bootstrap-validate` removed; `README.md` added with project overview, release sideload instructions, and current development status.

### 2026-08-27 — Design system implementation authorized

Authorized next foundation commit: create only `:core:designsystem` plus the minimum root/app wiring required to consume it. The module will centralize the approved Atlas Night semantic palette, Material 3 dark/light schemes, comfortable Material Air-inspired shapes, and `FOLLOW_SYSTEM / LIGHT / DARK` theme selection. Preference persistence is explicitly deferred to `:core:preferences`. Verification requirement: a real GitHub Actions `./gradlew :app:assembleDebug` must succeed before the code reaches `main`.

### 2026-08-27 — Design system implementation completed

`:core:designsystem` is committed and consumed by `:app`. It provides centralized Atlas Night color tokens, Material 3 light/dark `ColorScheme`s, Material Air-inspired rounded shapes, and `ThemeMode.FOLLOW_SYSTEM / LIGHT / DARK`. `MainActivity` uses `FOLLOW_SYSTEM` by default. CI run `33075397300` successfully built and verified the debug APK; theme persistence remains deferred to `:core:preferences`.

### 2026-08-27 — Core model implementation authorized

Authorized next foundation commit: create only `:core:model` plus the minimum root/settings wiring needed for a pure Kotlin/JVM module. The module owns typed IDs, radio station primary/fallback stream endpoints, base media item/source types, and recently-played entries. Room, DataStore, Media3, Hilt, Android framework types, networking, and persistence are explicitly excluded. CI must pass `:core:model:test` and produce the real debug APK with `:app:assembleDebug`.

### 2026-08-27 — Core model implementation completed

`:core:model` is committed as a pure Kotlin/JVM module. It provides typed `StationId` / `MediaId`, validated `StreamEndpoint`, `RadioStation` with ordered primary/fallback playback streams, `MediaSourceType`, `MediaItemSummary`, and `RecentlyPlayedEntry`. No Android, Room, DataStore, Media3, Hilt, networking, or persistence APIs are present. CI run `33079784030` passed `:core:model:test` and `:app:assembleDebug`; the verified debug APK SHA-256 is `8b3dde17ab1f56fe8615ccd3fa303e1f86260667ea6fe3dd84646383ef592306`.

### 2026-08-27 — Core preferences implementation authorized

Authorized next foundation commit: create `:core:preferences` with AndroidX Preferences DataStore 1.2.1, persisting theme mode, optional language tag, and last played source/station/media identifiers. The app composition layer will map the stored theme preference to the existing `:core:designsystem` `ThemeMode`, avoiding a design-system dependency from preferences. Room, Media3, Hilt, networking, recently-played history, and unrelated settings remain excluded. CI must pass `:core:preferences:testDebugUnitTest` and produce the real debug APK with `:app:assembleDebug`.

### 2026-08-27 — Core preferences implementation completed

`:core:preferences` is committed with AndroidX Preferences DataStore 1.2.1. It persists `FOLLOW_SYSTEM / LIGHT / DARK`, an optional BCP-47 language tag, and the last played source plus typed station/media identifiers from `:core:model`. Unknown enum values decode to safe defaults. `:app` now collects the stored preferences and maps the persisted theme selection to the existing `:core:designsystem` `ThemeMode`. Room, Media3, Hilt, networking, recently-played history, and unrelated settings remain excluded. CI run `33081261153` passed `:core:preferences:testDebugUnitTest` and `:app:assembleDebug`; the verified debug APK SHA-256 is `e9da98065c3727f4b3d0f85d42a84b963ee6d8543f7cc02096d5bd4da7512c60`.

### 2026-08-27 — Core database implementation authorized

Authorized next foundation commit: create `:core:database` using stable Room 3.0.2 (`androidx.room3`) with KSP 2.3.10. The module persists preinstalled/custom radio stations with ordered fallback URLs, station favorites, and recently-played metadata/cache mapped to `:core:model`. It owns entities, DAO, the Room database declaration, schema export, and mapping helpers only. Repository orchestration, UI, Hilt, Media3, networking, catalog seeding, and DataStore integration remain excluded and are deferred to later modules. CI must pass `:core:database:testDebugUnitTest` and `:app:assembleDebug`.

### 2026-08-27 — Core database foundation completed

`:core:database` is implemented and committed in `c9d8f21832812d647ce74bc56aaf964ac5989c7f` using Room 3.0.2 (`androidx.room3`) with KSP 2.3.10. The persistence-only module contains Room entities/DAO and mapping helpers for preinstalled/custom radio stations with ordered fallback streams, station favorites, and recently-played metadata/cache; repository orchestration and UI remain deferred to `:core:data` and feature modules. Room schema v1 is exported at `core/database/schemas/com.tamalut.radio.core.database.TamalutDatabase/1.json`. GitHub Actions run `33083889979` passed `./gradlew :core:database:testDebugUnitTest :app:assembleDebug`, generated a real debug APK, and verified its SHA-256 as `f71bb761f2afde189164d2d2a8a518335ee0645840694224c1aeca0e5d38ddb4`.

### 2026-08-27 — Core data implementation authorized

Authorized next foundation commit: create `:core:data` as the local repository/orchestration layer over `:core:database` and `:core:model`. It will seed the nine approved radio stations idempotently, coordinate custom stations, favorites, and bounded recently-played history, while Media3/playback, Google Drive, networking, UI, and Hilt remain deferred. CI must pass `:core:data:testDebugUnitTest` and `:app:assembleDebug`.

### 2026-08-27 — Core data implementation completed

`:core:data` is implemented and committed in `492b496186baac2aed19caac281a18a7103e07a1` as the local repository/orchestration layer over `:core:database` and `:core:model`. It seeds the nine approved radio stations idempotently (excluding unresolved Radio Tachlit Sous Massa), coordinates custom stations and favorites, preserves ordered stream fallbacks, and maintains bounded recently-played history with safe domain mapping. Media3/playback, Google Drive, networking, UI, and Hilt remain deferred. GitHub Actions run `33085614244` passed `./gradlew :core:data:testDebugUnitTest :app:assembleDebug`; the verified debug APK SHA-256 is `c10f19e3e32f839e897302bb0fd81f5a1adb04e23a2863261530cd3c9bd81964`.


### 2026-08-27 — Core playback foundation sub-step 1/3 completed

`:core:playback` foundation is implemented and committed in `14873c8cb33a6797bfd3c7fe4b0d2d6b8acd040a` using Media3 1.11.0. `TamalutPlaybackService` is a `MediaLibraryService` owning one ExoPlayer and `MediaLibrarySession`, with media/music `AudioAttributes` and ExoPlayer-managed audio focus (`handleAudioFocus = true`). The service exposes a minimal browsable root for future Android Auto, accepts generic Media3 `MediaItem` queues including future local files, declares the media-playback foreground-service permissions/actions, preserves playback across task removal through the Media3 service lifecycle, and provides an explicit trusted-controller Stop/Exit custom command that stops playback, clears the queue, and stops the playback service. Automatic radio primary→fallback recovery remains sub-step 2/3; notification/button and Android Auto browsing polish remain sub-step 3/3. GitHub Actions run `33087262971` passed `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`, produced a real debug APK, and verified SHA-256 `f88ea521be57ca0c94dcca521967dc1bd7b007e71d23735cdd5a78c3dcce86ec`.

### 2026-08-27 — Core playback fallback sub-step 2/3 completed

Bounded automatic radio fallback is implemented and committed in `62b6396846c9d08eb21ff6fe38024558a2e1c4e1`. `RadioMediaItemFactory` carries the exact `primary -> fallbackStreams` plan from `:core:model`; fatal Media3 `onPlayerError` callbacks advance exactly one endpoint, with the effective attempt budget capped by both configurable `maxAttempts` (default 3) and available endpoints. Exhaustion is explicit through `RadioFallbackState.Exhausted` with station ID, attempted count, maximum attempts, and final Media3 error code; no retry loop is restarted and the final player error remains visible to MediaSession/controllers. Generic/local Media3 items are unaffected. GitHub Actions run `33089219860` passed `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`, produced a real debug APK, and verified SHA-256 `b598aa5c78fe6b9c460f44476ce014053c54698e046b686f3f887929d7ca8a23`. Notification/media-button and Android Auto browsing polish remain sub-step 3/3.

### 2026-08-27 — Core playback controls sub-step 3/3 completed

`:core:playback` sub-step 3/3 is implemented and committed in `05739c788b82cab18585c15608cc8c26361c8e5d`. Media3 now exposes Play/Pause, Next, and explicit Stop/Exit through the shared MediaLibrarySession/system control surface; the service remains the single playback authority for notification, lock-screen, and Android Auto controllers. The minimal browsable library is `TamalutRadio -> Radio di test` with Radio Azawan, HIT RADIO Maroc, and Radio Mars, and selecting a station resolves to an ordered playable queue so Next advances between real seed stations. `INTERNET` is declared and the app includes a temporary `Prepara Radio Azawan` manual-test action that prepares the station through MediaBrowser without creating a second player. GitHub Actions run `33092744338` passed `./gradlew :core:playback:testDebugUnitTest :app:assembleDebug`, verified the merged playback service, produced a real debug APK with SHA-256 `d8780e2c96b42a1df8d27a38bbc6575faabaf66e384047c44a4ae68db3e93738`, and received 432000 bytes from the approved Radio Azawan stream during the network smoke probe. Manual on-device verification was subsequently confirmed by the user: after pressing `Prepara Radio Azawan`, playback started successfully from the media notification Play control, audio was heard from the phone, and the media controls behaved as expected. This completes all three `:core:playback` foundation sub-steps.

### 2026-08-27 — Feature radio UI/favorites sub-step 1/2 authorized

Authorized the first `:feature:radio` commit for the repository-backed Compose screen only: `Preferiti` / `Tutte le radio`, idempotent initial catalog seeding, favorite toggling, explicit app composition of Room/data repositories, Atlas Night theme consumption, and unit tests. Station selection will be exposed but Media3 playback integration and removal of the temporary Radio Azawan preparation path remain sub-step 2/2. CI must pass `:feature:radio:testDebugUnitTest` and `:app:assembleDebug`.

### 2026-08-27 — Feature radio UI/favorites sub-step 1/2 completed

`:feature:radio` sub-step 1/2 is implemented and committed in `ab7707aea02f618535277fb1fdd9b62c91659ff1`. The app now renders a real Atlas Night / Material 3 radio screen backed by the existing Room/data repositories, with `Preferiti` and `Tutte le radio` tabs, idempotent seed loading, visible star controls for adding/removing favorites, optimistic favorite UI updates with rollback on repository failure, empty-favorites messaging, and recoverable load errors. `:app` explicitly composes `TamalutDatabase`, `RadioStationRepository`, `FavoriteStationRepository`, and the feature ViewModel factory; Hilt remains deferred. Station row selection is exposed but intentionally does not start Media3 playback yet, and the temporary Radio Azawan manual-test path remains until sub-step 2/2. GitHub Actions run `33095959100` passed `./gradlew :feature:radio:testDebugUnitTest :app:assembleDebug`, verified the feature wiring and produced a real debug APK with SHA-256 `97f4aa610096c495cdd6672aec3d5759418bccaf6b64b800939ec649bba8f261`. Sub-step 2/2 will connect every station selection to the existing `MediaLibraryService` and remove the temporary Radio Azawan test control.

### 2026-08-27 — Feature radio playback sub-step 2/2 completed

`:feature:radio` is completed in commit `795efb516febdab473f45483c798e06044351e41`. The Radio ViewModel now delegates station selection to a feature-level Media3 playback gateway backed by `MediaBrowser` and the existing `TamalutPlaybackService`; it sends the selected repository-backed `RadioStation` through `RadioMediaItemFactory`, then prepares and starts playback without creating a second player. The full primary/fallback plan is preserved for every station. The Radio UI exposes playback success/failure state while retaining the repository-backed `Preferiti` / `Tutte le radio` tabs and favorite mutations. The temporary app-level `Prepara Radio Azawan` path and its dedicated browser callback were removed, making the Radio screen the app-level radio playback entry point. GitHub Actions run `33097183525` passed `./gradlew :feature:radio:testDebugUnitTest :app:assembleDebug`, verified the merged MediaLibraryService and absence of the temporary test button, produced a real debug APK, and verified SHA-256 `45f01b4ac53a819c887386356e9f05c1ea35bc3c47386486377b5aedca8486d7`. This completes both `:feature:radio` implementation sub-steps.

