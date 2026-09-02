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
- Music sources: **local SAF folder only**. Google Drive integration is retired from product scope because the complexity/fragility of `drive.file` + Picker behavior exceeds the practical benefit. `:core:cloud` remains provider-neutral and contains only an empty `CloudMusicSource` abstraction for possible future providers.
- Radio Tachlit Sous Massa: placeholder remains until a reliable identifying reference is provided.
- Approved extra features: **Recently played** and **automatic fallback stream URLs**.
- Distribution: signed APK via GitHub Releases / sideload; debug APK test builds are distributed as GitHub prerelease assets rather than Actions artifacts, avoiding Actions artifact storage quota. Debug tags use `debug-<UTC date/time>-<short commit>`.

### Debug APK release distribution contract

- [x] Replace debug APK distribution through `actions/upload-artifact` with a permanent GitHub Actions workflow that builds `:app:assembleDebug` and attaches the APK directly to a GitHub prerelease.
- The workflow must support manual execution against `main` (or an explicitly supplied ref) and generate an automatic unique tag in the form `debug-<UTC date/time>-<short commit>`.
- The Release must target the exact commit used for the build, include the APK SHA-256 in its notes, and publish a clearly named `TamalutRadio-debug-<short commit>.apk` asset.
- Debug release publication must not use `actions/upload-artifact`, must not depend on Actions artifact storage, and must keep the existing Android build baseline unchanged.
- The first validation release must build the approved polished snapshot `f685090bd5997b13e56753949cfe375d3ff93156`.
- Verification: a real GitHub Actions run must complete `:app:assembleDebug`, create the prerelease, expose the APK asset via GitHub Releases, and leave no temporary branch behind.

### Persistent debug signing contract

- [x] **Dedicated persistent debug key v1.** All GitHub Actions `debug` APKs must be signed with the same dedicated debug-only key, strictly separate from the production/release signing key. Existing production/release keystore secrets must never be reused to sign debuggable builds.
- [x] **Private persistent key storage + centralized setup.** Because the current GitHub connector cannot create or modify repository Secrets, the dedicated debug-only keystore may be generated once and stored outside Git history as a private repository GitHub Release asset under the internal signing-material release `debug-signing-key-v1`. A committed setup script must download it into `RUNNER_TEMP`, verify its committed SHA-256 checksum, and export the signing path/credentials for Gradle. The keystore itself must never be committed.
- [x] **CI enforcement.** `:app` must use the persistent debug signing config whenever the signing environment is present and must fail fast on GitHub Actions if the persistent signing environment is missing, so CI can never silently fall back to a runner-generated `~/.android/debug.keystore`. Local developer builds may continue using the local Android debug keystore when not running in GitHub Actions.
- [x] **Permanent release workflow integration.** `.github/workflows/publish-debug-release.yml` must provision the persistent debug key before `:app:assembleDebug`, and future temporary validation workflows that build a debug APK must call the same setup script.
- [x] **Two-build signature proof.** Verification must run two consecutive independent GitHub Actions debug builds on the same validated commit and prove that `apksigner` reports the same signer certificate SHA-256 digest for both. The verification must also document the historical mismatch between prior ephemeral builds.
- Transition note: APKs already installed from the old ephemeral-signing era cannot be updated in-place to the new key; one final uninstall/reinstall is required when moving to persistent debug key v1. After that transition, future debug APKs signed with v1 must install as updates over each other.

Validation record — persistent debug signing v1:
- Historical mismatch confirmed: prior GitHub Actions debug APKs used different ephemeral signer certificates.
- Dedicated debug-only key v1 is stored outside Git history as the private draft-release asset `debug-signing-key-v1`; production/release signing material is not reused.
- Pinned debug signer certificate SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- `:app` fails fast on GitHub Actions when persistent signing is absent or incomplete, preventing fallback to runner-generated `~/.android/debug.keystore`.
- Permanent `publish-debug-release.yml` provisions and verifies the v1 signer before publishing. The signer parser accepts the current `Signer #1 certificate SHA-256 digest:` output and the compatible `V2 Signer:` form.
- Validated implementation snapshot: `036c13d03cb58bf5f38dc24cb0c83ce54b6599e8`. Independent green runs `33167475063` and `33167507333` both produced signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`; the verified APK SHA-256 was `15dec3e8e978af2ca2f094717c49f8e646e3066c6070e610fc16d36f1454d5aa`.

### Shared playback state and in-app controls refinement contract

This refinement is one cohesive playback-state objective split into three implementation sub-steps:

- [x] **1/3 — Shared playback state + Radio/Music synchronization bug fix.** `:core:playback` becomes the single observable source of truth for the active Media3 item, source type (`RADIO` vs local music), play/pause state, queue position/capabilities, repeat mode, and shuffle state. `:feature:radio` and `:feature:library` must derive their `In riproduzione` highlighting from that shared state rather than storing independent successful-play selections. Switching from radio to a local track must immediately clear the previous radio highlight, and switching back to radio must clear the local-track highlight.
- [x] **2/3 — Persistent in-app mini-player.** Add a compact Atlas Night mini-player above the bottom navigation while a current Media3 item exists, plus a functional `In Riproduzione` destination using the same controller/state. At minimum expose play/pause, previous, and next controls; controls must operate the existing `TamalutPlaybackService` / `MediaLibrarySession` and must not create a second player.
- [x] **3/3 — Local music repeat + shuffle.** `:feature:library` exposes shuffle plus repeat modes OFF / ONE / ALL for local queues only. These controls delegate to the shared Media3 controller (`shuffleModeEnabled` / `repeatMode`) and must not alter radio fallback behavior or enable repeat/shuffle for radio playback.
- Shared state must remain valid when playback changes from notification/lock screen/media buttons as well as from inside the app.
- Existing radio favorites, automatic fallback streams, SAF folder selection/persisted permission, local queue ordering, notification/lock-screen controls, and background playback behavior must remain intact.
- Explicit tests must cover radio -> local transition clearing the stale radio marker, local -> radio transition clearing the stale local marker, mini-player transport delegation/state projection, and local repeat/shuffle mapping including radio rejection/no-op behavior.
- Verification requirement: GitHub Actions must run the relevant `:core:playback`, `:feature:radio`, and `:feature:library` unit tests plus `:app:assembleDebug` successfully before code reaches `main`.


### Mini-player discovery and Now Playing detail refinement contract

- [x] **1/2 — Local playback modes in the persistent mini-player.** When the active shared source is `LOCAL`, the persistent mini-player must expose shuffle plus repeat controls using the same shared `PlaybackController` and the same repeat cycle already used by Music (`OFF -> ALL -> ONE -> OFF`). Those controls must be hidden for Radio and must immediately reflect changes made from either the mini-player or the Music screen.
- [x] **2/2 — Local playlist continuity default + non-redundant Now Playing.** Every newly created local queue must start with repeat mode `ALL` (loop playlist) by default, while the user remains free to cycle to `ONE` or `OFF`. Radio playback must continue to force repeat OFF and shuffle OFF. Keep the `In Riproduzione` destination, but redefine it as an expanded detail view rather than a duplicate transport bar: prominent source/cover area, current item/source/status and local playback-mode summary; the persistent mini-player remains the global transport surface.
- The expanded Now Playing view must not render a second previous/play-next control row immediately above the persistent mini-player.
- Existing shared-state synchronization, notification/lock-screen controls, radio fallback, SAF library behavior and the single `TamalutPlaybackService` / `MediaLibrarySession` architecture must remain unchanged.
- Explicit tests must cover local-only visibility/projection of mini-player shuffle/repeat controls, shared-controller delegation for shuffle/repeat, repeat-cycle mapping, local queue default `ALL`, and radio preservation of repeat OFF / shuffle OFF.
- Verification requirement: GitHub Actions must run `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` successfully before code reaches `main`.

### Notification launch, radio grouping, and compact playback chrome refinement contract

- [x] **1/3 — Media notification opens Now Playing.** The `MediaLibrarySession` must expose a real activity `PendingIntent` through Media3 `setSessionActivity(...)`. Tapping the media notification or lock-screen media surface must open/bring TamalutRadio to the foreground on the `In Riproduzione` destination, including when `MainActivity` is already alive and receives a new intent. Playback must continue uninterrupted and no second player/session may be created.
- [x] **2/3 — Radio list grouped into visual sections.** The `Tutte le radio` list must be visually grouped in deterministic sections `Marocco`, `Italia`, and `Sport`, with section headers and each station rendered exactly once. Existing favorites, station selection, LIVE state, fallback behavior, and the `Preferiti` tab must remain functional. Unknown/future stations must have a safe fallback group rather than disappearing.
- [x] **3/3 — Clarify manual music rescan + compact local mini-player.** Keep the manual folder rescan because it explicitly re-runs the SAF scan for recovery when provider changes are not reflected immediately; rename `Aggiorna` to `Riscansiona` and add a brief explanation that it forces a new read of the authorized folder. Redesign the local mini-player onto a single compact row: shuffle/repeat remain local-only, use smaller visual icons with accessible touch targets, sit in the same control row as previous/play/next, and remove the extra text row such as `Loop playlist`.
- The radio grouping is presentation taxonomy only for the current catalog and must not require a Room schema migration.
- Explicit tests must cover notification-launch destination routing, category grouping/order/coverage including unknown fallback, manual-rescan delegation, local-only mini-player mode projection/action delegation, and preservation of radio behavior.
- Verification requirement: GitHub Actions must run `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` successfully, plus structural checks for the Media3 session activity and single-row mini-player, before code reaches `main`.

### User-selectable radio filter and local queue repeat-default refinement contract

- [x] **1/2 — User-selectable radio filter.** Replace the always-stacked `Marocco / Italia / Sport` grouping under `Tutte le radio` with a single user-controlled filter selector above the list: `Tutte / Marocco / Italia / Sport`. Only stations matching the active filter are shown; `Tutte` shows the complete flat list. `Preferiti` remains independent and flat. Existing favorite toggles, selected/playing state, LIVE badge, fallback playback, notification/lock-screen behavior, and shared playback state must remain unchanged.
- [x] **Radio classification metadata stays presentation-only for now.** Do not add a persisted Room category column or migrate the database solely for this filter. Maintain explicit filter metadata for the built-in catalog in `:feature:radio`; unknown/custom stations remain visible under `Tutte` and are not silently dropped. `Radio Mars` must be classified as `Marocco`, not `Sport`; `Radio Sportiva` remains `Sport`.
- [x] **2/2 — New local queues reliably default to repeat ALL.** Every call that creates/replaces a local playback queue from Music must leave Media3 in `REPEAT_MODE_ALL` after the new queue has been installed, regardless of the previous local repeat mode. Reselecting any track from the Music list creates/replaces the queue and must therefore restore playlist loop `ALL`. User changes to `ONE` or `OFF` remain valid until another new local queue is created. Radio continues to force repeat OFF and shuffle OFF.
- Explicit tests must cover filter options and station coverage, `Radio Mars -> Marocco`, unknown-station visibility under `Tutte`, flat unfiltered rendering semantics, and repeat-default restoration after a prior OFF/ONE state when a new local queue is created.
- Verification requirement: a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with the persistent debug signing v1 setup before validated code is promoted to `main`.

Validation record — radio filter and local repeat default refinement:
- Validated code commit: `4ddc1eb21f928f10d3626f08290bfab670cae875` (`fix: add radio filters and restore playlist loop default`).
- GitHub Actions run `33168965654` passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 4m 42s, 193 actionable tasks (153 executed, 40 from cache).
- Radio UI now uses one user-selected `Tutte / Marocco / Italia / Sport` filter above the flat `Tutte le radio` list; `Radio Mars` is `Marocco`, `Radio Sportiva` is the only built-in `Sport` station, and unknown/custom stations remain visible under `Tutte`. No Room/database migration was introduced.
- Every new/replaced local Music queue installs its media items first and then applies Media3 `REPEAT_MODE_ALL` before prepare/play. A repeat-order test protects `set-items -> repeat-all -> prepare -> play`; user-selected OFF/ONE remains active only until another Music-list selection creates a new queue.
- Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`; validation APK SHA-256: `aed3a8cc7fe9de5e25decbcefeeb6001d1c14a50e34baddfb157e15513824779`.

### Floating overlay implementation contract — sub-step 1/2

- [x] **1/2 — Foundation, permission and Settings.** Add the floating-player foundation without playback transport controls yet. The overlay must use Android `TYPE_APPLICATION_OVERLAY` and remain visible when the user leaves TamalutRadio with Home, without being tied to the `MainActivity` window lifecycle.
- Declare `android.permission.SYSTEM_ALERT_WINDOW`, but never treat it as an ordinary runtime permission or request it silently. Enabling the feature from `Impostazioni` must first show an in-app explanation that the optional permission allows the future floating player to stay above apps such as Maps/Waze; only explicit `Continua` opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for `package:com.tamalut.radio`.
- Persist one `overlayEnabled` DataStore preference in `:core:preferences`, default `false`. A request to enable the overlay must not persist `true` until `Settings.canDrawOverlays(...)` confirms that the special permission is granted. Denial/cancellation keeps the preference disabled and the rest of the app fully functional.
- Replace the current Settings placeholder with an Atlas Night `Player flottante` setting that exposes the enable/disable switch plus clear permission status. Disabling from Settings removes the overlay immediately and persists `false`.
- The foundation window is application-process owned rather than Activity-window owned and must not add a second foreground service merely to keep the overlay alive. Android gives visible `TYPE_APPLICATION_OVERLAY` processes adjusted importance; the existing Media3 playback service remains the only playback/foreground-service authority.
- The sub-step 1 overlay surface is intentionally minimal: TamalutRadio identity plus an accessible close action. `precedente / play-pausa / successivo` are reserved for sub-step 2/2, where they will observe/delegate to the existing shared `PlaybackController`; no temporary player or duplicate playback state is allowed here.
- Closing the overlay removes only the overlay window, persists `overlayEnabled=false`, and must never stop, pause, clear, or otherwise mutate the current Media3 playback/session.
- On app launch/resume, reconcile the persisted preference with the current special-permission state: enabled + granted restores/shows the window; revoked/missing permission hides it and safely resets the preference to disabled. Permission denial or later revocation must not crash or block Radio, Music, notification/lock-screen playback, or Now Playing.
- Keep the overlay non-focusable/non-modal so it does not capture keyboard focus or block interaction with the underlying app outside its compact bounds. No broad storage permission, Room migration, playback-service duplication, Google Drive work, sleep timer, equalizer, or unrelated feature work belongs in this sub-step.
- Explicit tests must cover the persisted overlay default/decoding and the enablement decision policy (`disable`, `show`, `request permission`). Structural verification must confirm `SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`, the explicit permission explanation/settings intent, persistence wiring, close-without-playback calls, and absence of a new overlay foreground service or player.
- Verification requirement: a real GitHub Actions run must pass `:core:preferences:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with persistent debug signer v1 before promotion to `main`; the final `main` snapshot must then be published through the permanent GitHub Releases debug workflow for physical testing.

Validation record — floating overlay foundation sub-step 1/2:
- Clean promoted implementation commit: `9d37f04cfc001fdb8f59bad8d6b47ff34cd458cc` (`feat: add floating overlay foundation`), preceded by spec-before commit `b4a42bd93b566a6635f832e9b8899293750937d1`.
- GitHub Actions validation run `33236784011`, attempt 3, passed `:core:preferences:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 5m 56s, 183 actionable tasks (145 executed, 38 from cache), plus structural checks for `SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`, explicit overlay-permission settings routing, persistence wiring, and absence of an overlay ExoPlayer/foreground service.
- Validation used persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`; validation APK SHA-256: `ad5fc4e7da9c44219d63c3cd6a7b087d3fc0b0000ef0655beeb1c654ba218fb7`.
- `Impostazioni` now exposes the optional `Player flottante` switch and permission state. Enabling without permission shows an explicit explanation before Android's `Visualizza sopra altre app` screen; denial/revocation leaves the app functional and disables the preference safely.
- The foundation overlay is application-process owned, non-focusable/non-modal, remains independent of the Activity when Home is pressed, and closing it removes only the window and disables the preference without touching Media3 playback. Transport controls remain intentionally deferred to sub-step 2/2 and must use the existing shared `PlaybackController`.

### Floating overlay refinement contract — sub-step 1.5/2

- [x] **1.5/2 — Edge-tab UX and external-session lifecycle.** Refine the verified overlay foundation before adding playback transport controls. The normal collapsed surface becomes a very small edge tab/handle that minimizes obstruction over Maps/Waze; tapping it expands an Atlas Night control shell toward the screen center and tapping the collapse affordance returns to the edge tab. The 1.5 shell must not include fake/non-functional previous/play/next controls; those remain reserved for 2/2.
- Treat four concepts as independent state: persisted user preference (`overlayEnabled`), Android special-permission state (`Settings.canDrawOverlays`), transient external-session state, and transient collapsed/expanded UI state. `overlayEnabled` is the permanent user choice and may only be changed by the Settings switch; it must never be changed by the overlay X or by permission revocation.
- Turning `Player flottante` ON persists `overlayEnabled=true` even when the special permission is currently missing. The existing in-app explanation remains mandatory before opening `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`; denial/cancellation/revocation leaves the preference ON and Settings must show that permission is required. Turning the switch OFF persists `false` and removes any visible overlay immediately.
- An external overlay session begins when TamalutRadio leaves the foreground through a user-leave/Home flow while playback is actively running, the permanent preference is enabled, and overlay permission is granted. Entering foreground ends the external session, hides the overlay, clears a temporary dismiss, and resets the next external session to the collapsed tab. The explicit overlay-permission Settings launch must be suppressible so the app does not create a spurious overlay session merely because it opened Android Settings itself.
- The overlay X is **session-only dismiss**: it removes the current overlay window and marks the current external session dismissed, but must not mutate `overlayEnabled`, permission state, Media3 playback, queue, repeat/shuffle, or service lifecycle. While the same external session remains active it must stay hidden; after TamalutRadio returns to foreground, the dismiss resets, so a later Home/user-leave with active playback can show the overlay again automatically.
- Playback `isPlaying` is an entry condition, not a continuous visibility condition: once an external session/overlay has been admitted, pausing playback must not immediately destroy it because sub-step 2/2 needs the user to be able to resume from the overlay. A truly cleared/absent current Media3 item ends/hides the overlay session.
- Make the edge tab draggable. During touch drag, update `WindowManager.LayoutParams.x/y`; on release, clamp the position to the visible display bounds and snap to the nearest LEFT/RIGHT edge. The panel must expand inward from the selected edge. Touch handling must still distinguish a tap (expand/collapse) from a drag and keep the overlay non-focusable/non-modal.
- Persist overlay placement separately from transient session/UI state in `:core:preferences`: edge (`LEFT`/`RIGHT`) plus a normalized vertical position rather than raw pixels, with safe defaults and defensive clamping/decoding. Reopening a later external session restores the chosen edge/relative height and adapts it to the current display size.
- Separate responsibilities explicitly: `FloatingOverlayCoordinator` owns visibility/lifecycle decisions; `FloatingOverlayWindow` owns `WindowManager`, rendering, drag/snap and geometry; `OverlaySessionState` owns only ephemeral external-session/dismiss/expanded state; `UserPreferences` owns only persistent enablement/placement. The app and coordinator must reuse the existing shared Media3 `PlaybackController`/state connection rather than creating a second player, MediaSession, playback service, or duplicate playback source of truth.
- Explicit unit tests must cover: permanent preference vs session dismiss; foreground reset; admission only for enabled+permission+actively-playing Home/user-leave; paused overlay continuity after admission; cleared-current-item hiding; permission revocation preserving preference; edge snap left/right; bounds clamping; normalized-position conversion; DataStore defaults/decoding for edge/vertical position; and Settings toggle policy where missing permission requests explanation without forcing the preference back OFF.
- Structural verification must confirm `SYSTEM_ALERT_WINDOW` and `TYPE_APPLICATION_OVERLAY` remain; the X path contains no `setOverlayEnabled(false)` and no playback stop/pause/clear call; drag updates `WindowManager.LayoutParams`; no overlay ExoPlayer/new MediaSession/new foreground service is added; and playback controls remain absent from the 1.5 shell.
- Verification requirement: a real GitHub Actions run must pass `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with persistent debug signer v1 before promotion to `main`. After clean promotion, record validation in this spec, publish the exact final `main` snapshot through the permanent debug Release workflow, and remove every temporary validation branch/workflow.

Validation record — floating overlay refinement sub-step 1.5/2:
- Spec-before commit: `1acff3db93422424b35155043296348433a02a08` (`docs: define floating overlay refinement 1.5`). Clean product implementation commit: `e81180d4c0d811502860e46e61e2ea5c78d3c62f` (`feat: refine floating overlay lifecycle and edge tab`).
- Real GitHub Actions validation run `33256306614` passed `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 5m 51s, 188 actionable tasks (149 executed, 39 from cache). The validated run head `572714b0078cea54e8dac41caee09393135ed018` differs from the clean implementation only by temporary CI workflow commits; the product/source tree under validation is the promoted implementation.
- Structural validation passed for `SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`, non-focusable/non-modal flags, `WindowManager.updateViewLayout` drag updates, edge snap/normalized placement, session-only dismiss, Home/user-leave routing, permission-settings suppression, and absence of overlay-owned ExoPlayer/MediaSession/foreground-service or 1.5 playback transport calls.
- Validation APK SHA-256: `1dafc12e604ae2dff5d5160620bf8f27ea37f6193fe2cd9fab9358541c2ccf61`. Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- The permanent Settings preference is now independent from permission/session state; X dismisses only the current external session; returning to TamalutRadio resets that dismiss; a later Home/user-leave can automatically restore the collapsed edge tab when playback is active. Permission denial/revocation no longer forces the preference OFF.
- The overlay foundation now uses a draggable collapsed edge tab with LEFT/RIGHT snap, inward expansion, persisted edge plus normalized vertical placement, and an expanded 1.5 shell containing only collapse and session-dismiss affordances. Previous/play-pause/next remain intentionally deferred to sub-step 2/2 pending physical validation of 1.5.

### Floating overlay 1.5 startup-crash hotfix contract

- [x] **Hotfix — launch safety after 1.5.** Physical testing of Release `TamalutRadio-debug-67c7247.apk` showed an immediate Android crash before the app UI became usable. Code review identified eager construction of `FloatingOverlayWindow` from the process-scoped coordinator during `MainActivity.onResume()`: on Android 11+ it called `applicationContext.createWindowContext(TYPE_APPLICATION_OVERLAY, ...)`, although an Application context has no associated Display and Android documents this path as unsupported.
- Preserve the approved process-scoped Coordinator/SessionState/Preferences model, but make all overlay window/UI infrastructure lazy: constructing `TamalutRadioRuntime`, `Media3PlaybackController`, or `FloatingOverlayCoordinator`, and calling `onAppForeground()`, must not create a window context, obtain an overlay `WindowManager`, inflate/create overlay views, or otherwise require an attached Display.
- When an external session actually needs to show the overlay and `Settings.canDrawOverlays(...)` is true, resolve an explicit primary `Display` through `DisplayManager`; on API 30+ create a display-associated/window context for `TYPE_APPLICATION_OVERLAY` from that Display. If a usable display/window context cannot be obtained, fail closed by leaving the overlay hidden rather than crashing the application. Pre-API-30 behavior may continue using the application WindowManager path.
- `FloatingOverlayWindow.hide()` and ordinary foreground reconciliation must remain safe before any window host has ever been created. Permission denial/revocation must likewise never force window-host construction. No change to permanent preference/session-dismiss semantics, playback lifecycle, transport-control scope, or persisted edge/vertical placement is allowed.
- Add deterministic unit/structural coverage proving the overlay host is lazy and that the startup path does not invoke `createWindowContext`. Existing geometry/session/preference/playback tests remain required.
- Add a real Android emulator launch smoke test to CI: install the built debug APK on API 35+ with overlay permission not granted, cold-start `com.tamalut.radio/.MainActivity`, verify the process remains alive and capture/check logcat for an uncaught `FATAL EXCEPTION`/process death. This emulator smoke is mandatory in addition to `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`.
- The fixed APK must use persistent debug signer v1, be promoted cleanly to `main`, receive a spec-after validation record, be published by the permanent debug Release workflow, and all temporary crash-fix/older 1.5 branches and workflows must be removed before declaring it ready for physical testing.

Validation record — floating overlay 1.5 startup-crash hotfix:
- Physical testing identified `TamalutRadio-debug-67c7247.apk` / main `67c7247a83fa5a832f0221a5203d507091c003a2` as a startup-crashing build. Root cause: eager `FloatingOverlayWindow` construction reached `applicationContext.createWindowContext(TYPE_APPLICATION_OVERLAY, ...)` during `MainActivity.onResume()` before an overlay was needed, using a context without an associated Display.
- Clean promoted spec-before: `4f0d932579dac07231cceafcf5ec5c415abdd19a` (`docs: define overlay startup crash hotfix`). Clean promoted product fix: `a9a9bb11da5c0a2f99381fb292cb196122a05195` (`fix: defer floating overlay window host creation`). Temporary branch heads differ only by validation workflow files.
- The overlay host is now lazy. Foreground reconciliation does not create WindowContext/WindowManager/views. `show()` first checks overlay permission, then resolves an explicit Display and creates the display-associated overlay context; host-creation failures fail closed, while `hide()` never materializes a host.
- GitHub Actions run `33257490244` passed `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, launch-safety structural checks, and persistent-signer verification. Its later emulator stage was blocked by hosted-runner disk, before application launch.
- Mandatory runtime gate run `33259264146` succeeded on Android 35: the signed APK was installed with `SYSTEM_ALERT_WINDOW` denied, `MainActivity` cold-start returned `Status: ok` and `LaunchState: COLD`, process PID `2170` remained alive, MainActivity stayed active, logcat contained no fatal TamalutRadio exception, and the run emitted `MANUAL_AVD_COLD_LAUNCH=PASS`.
- Validated APK SHA-256: `95244e2f1932556b725a6ee5f51ac5dc5446490101728dbc355ec0348d5776b1`. Persistent debug signer SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- The approved 1.5 behavior is unchanged: Settings is the permanent preference, X dismisses only the current external session, permission revocation does not switch the preference off, drag/snap/normalized placement remain, and no duplicate player/MediaSession/foreground service or 2/2 transport controls were added.

### Floating overlay navigation-exit lifecycle refinement contract — sub-step 1.5 edge-case

- [x] **Home / Recenti / Back-to-home parity.** The floating overlay must become eligible whenever TamalutRadio becomes no longer visible because the user leaves the launcher activity while playback is active, regardless of whether the system transition was initiated by Home, the Recent-apps overview, or Back from the root destination/task. The three navigation paths must share one lifecycle admission path rather than depending exclusively on `Activity.onUserLeaveHint()`.
- Treat `Activity.onStop()` as the authoritative Activity-visibility boundary for external-session admission because Android defines it as the point where the Activity is no longer visible. `onPause()` must not admit the overlay because a paused Activity can still be visible. `onUserLeaveHint()` may remain only as a non-authoritative hint/compatibility signal and must not be the sole trigger.
- `MainActivity.onStop()` must notify the process-scoped `FloatingOverlayCoordinator`, which decides whether to begin an external overlay session using the existing permanent preference, special-permission state, active-playback entry condition, current-item state and session-dismiss rules. Returning through `onResume()` continues to end/reset the external session exactly as in the approved 1.5 model.
- Configuration changes must not create an external overlay session: when `MainActivity.isChangingConfigurations` is true, the stop transition is ignored for overlay admission. The refinement must not add custom Back interception that consumes or alters Android predictive/system Back navigation.
- Preserve suppression for the app-initiated overlay-permission Settings flow. Calling the existing permission action must suppress the next real background/stop transition; that suppression is consumed by the corresponding `onStop()` and must not accidentally survive into a later genuine Home/Recenti/Back exit. Returning to TamalutRadio resets any unused suppression safely.
- Preserve all approved 1.5 semantics: Settings is the permanent preference; X is current-external-session dismiss only; permission revocation does not switch the preference off; pause after admission does not hide the overlay; clearing the current Media3 item ends it; drag/snap/normalized placement and lazy window-host startup safety remain unchanged; no second player, MediaSession or foreground service is introduced.
- Add deterministic tests with explicit Home, Recenti and root-Back scenarios proving that each `onStop` transition is admitted under enabled+permission+active-playback conditions. Add tests proving permission-Settings suppression blocks exactly the intended stop, configuration-change stop is ignored, foreground return clears/reset state, and a later genuine exit remains eligible.
- Structural verification must confirm `MainActivity` routes `onStop()` to the coordinator, passes `isChangingConfigurations`, does not admit the overlay from `onPause()`, and the permission launcher requests stop suppression before opening `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.
- Verification requirement: a real GitHub Actions run must pass `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with persistent debug signer v1 before clean promotion to `main`. After promotion, record a spec-after validation entry, publish the exact final `main` snapshot through the permanent debug Release workflow, and remove every temporary branch/workflow.

Validation record — floating overlay navigation-exit lifecycle refinement:
- Spec-before commit: `08693a9576c997f430fd84a33860efce09c3c6e1` (`docs: define overlay navigation exit lifecycle refinement`). Clean product/test commit: `d3959f9b180c50f6c81e4b0afeced7892b285017` (`fix: show overlay for all app exit navigation paths`). Temporary branch validation head differed only by CI workflow commits.
- Real GitHub Actions validation run `33261937165` passed `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 4m 49s, 188 actionable tasks (149 executed, 39 from cache).
- Structural validation emitted `NAVIGATION_EXIT_LIFECYCLE_STRUCTURE=PASS`: `MainActivity.onStop()` now routes the non-visible Activity transition to the process-scoped coordinator with `isChangingConfigurations`; `onUserLeaveHint()` is no longer the overlay admission trigger, and no `onPause()` or custom Back interception was introduced.
- Home, Recenti, and root Back are covered by explicit unit tests through the same stop gate. Configuration-change stops are ignored; the app-initiated overlay-permission Settings transition consumes a one-shot stop suppression; returning to foreground clears any unused suppression so the next genuine exit is eligible. Existing 1.5 preference, session-dismiss, playback, drag/snap, placement, and lazy WindowHost semantics remain unchanged.
- Validation APK SHA-256: `42e9f51210b6736e11ccdb4c9a947591e85815800d6f4a4463d474becb917de9`. Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.

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
- Music (local only)
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
- `:core:cloud`
- `:feature:radio`
- `:feature:library`
- `:feature:nowplaying`
- `:feature:settings`
- `:feature:widget`

The initial bootstrap must provide a minimal launchable `MainActivity` placeholder so CI can verify the project compiles before feature implementation.

### `:core:model` implementation contract

The next foundation module is authorized as a pure Kotlin/JVM module with no Android, Room, DataStore, Media3, or Hilt dependencies. It owns shared domain models only:

- typed identifiers for radio stations and generic media items.
- `StreamEndpoint` with a validated non-blank URL string.
- `RadioStation` with a primary endpoint and ordered fallback endpoints, preserving playback priority.
- base media types covering radio stations, local tracks, and provider-neutral future cloud tracks without implementing storage or playback behavior.
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
- seed the approved initial catalog idempotently before presenting the list.
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

- [x] UI polish sub-step 1/3: application shell and bottom navigation. Replace the provisional top buttons with a Material 3 bottom navigation bar for `Radio`, `Musica`, `In Riproduzione`, and `Impostazioni`; keep Radio and Musica wired to their real routes, and provide restrained Atlas Night placeholders for the latter two destinations. Preserve current ViewModel instances, playback services, repository wiring, system-following theme behavior, and edge-to-edge-safe content padding. Use Material icons and coherent destination labels, with selection styling driven by the existing Material 3 color scheme. Verification: `./gradlew :app:assembleDebug` plus structural checks for all four destinations and no playback/data regressions.

- [x] UI polish sub-step 2/3: Radio visual refinement. Keep existing catalog, favorites, tabs, playback gateway, and errors unchanged while refining header hierarchy, spacing, station cards, leading radio icon treatment, favorite action, borders/elevation, and current-station emphasis. A currently playing radio station must display a compact `LIVE` badge and an accessible `In riproduzione` state. Visuals must use Atlas Night semantic colors through `MaterialTheme`, with restrained Sahara Pulse sand/gold, Atlas green, and terracotta accents rather than hard-coded unrelated colors. Verification: `:feature:radio:testDebugUnitTest :app:assembleDebug` and structural checks that playback/favorites behavior remains wired.

- [x] UI polish sub-step 3/3: local Music visual refinement. Keep SAF selection/persisted permission/scanning/playback behavior unchanged while refining the screen header, selected-folder panel, actions, empty/loading/error presentation, local-track cards, leading music icon treatment, metadata hierarchy, borders/elevation, and current-track emphasis. The current local item must retain an accessible `In riproduzione` indication. Verification: `:feature:library:testDebugUnitTest :app:assembleDebug` plus merged-manifest checks confirming no broad storage permission was added.

Across all three sub-steps: do not add a second player, navigation framework migration, Google Drive implementation, Room changes, Hilt, sleep timer, equalizer, release signing changes, or APK artifact upload. Real debug APKs are built only for verification and are not published as GitHub Actions artifacts unless explicitly requested.

### Radio transient audio-focus live-edge resume refinement contract

- [x] **RADIO transient audio-focus loss -> gain resumes at the real live edge.** `TamalutPlaybackService` already delegates Android audio-focus handling to the single ExoPlayer through `setAudioAttributes(..., true)`; do not add a parallel player/session or a competing app-owned AudioManager focus stack. Extend the existing RADIO live-resume policy to observe Media3 playback suppression transitions: when an already-playing RADIO item enters `Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`, remember that a focus-driven live resume is pending; when suppression returns to `Player.PLAYBACK_SUPPRESSION_REASON_NONE` while `playWhenReady` is still true, discard the stale paused radio buffer and reconnect the exact current radio/fallback MediaItem through the existing `stop() -> setMediaItem(currentItem) -> prepare() -> play()` path before audible resume.
- The focus-driven reconnect is RADIO-only. LOCAL must keep normal ExoPlayer focus behavior and resume from the exact previous position after transient focus loss/gain; it must never be stopped/re-prepared merely because focus returns.
- Manual pause/resume behavior remains unchanged: RADIO explicit pause -> Play still reconnects to live edge and LOCAL explicit pause -> Play remains position-preserving. If the user manually pauses while audio focus is transiently lost, focus gain must not force playback; the pending focus reconnect is cleared/consumed without auto-playing, and a later explicit RADIO Play follows the existing manual live-resume gate.
- Preserve the current radio fallback plan/state and reuse `reconnectCurrentRadioAtLiveEdge(...)`; do not duplicate the stop/set/prepare/play sequence in a second implementation. Source changes to LOCAL and service stop/destroy must clear any pending focus-resume state.
- Add deterministic unit coverage for RADIO transient focus loss -> gain reconnect, LOCAL transient focus loss -> gain no reconnect, gain without prior transient loss no reconnect, and manual pause during transient focus loss not auto-resuming on gain. Existing `RadioLiveResumeGateTest` manual-pause cases must remain green.
- Verification requirement: a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; structural checks must prove the service handles `onPlaybackSuppressionReasonChanged`, keys specifically on `PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`, reuses `reconnectCurrentRadioAtLiveEdge`, retains the single ExoPlayer/MediaLibrarySession architecture, and does not introduce a custom `AudioManager.OnAudioFocusChangeListener`. Verify persistent debug signer v1 and record APK SHA-256 before promotion.

Validation record — radio transient audio-focus live-edge resume:
- Spec-before commit: `83f6b2db05c7f423dd03e6852286f7ba7b085c47` (`docs: define radio audio focus live resume`). Clean implementation/test commit: `ec073d7d57cf343ec4c0923155e4f55105c65d05` (`fix: reconnect live radio after transient audio focus`).
- Real GitHub Actions validation run `33278130090`, job `99168406983`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 3m 42s; 214 actionable tasks (172 executed, 42 from cache); structural gate `RADIO_FOCUS_LIVE_RESUME_STRUCTURE=PASS`.
- Root cause confirmed in the Media3 path: `TamalutPlaybackService` already delegates focus management to ExoPlayer with `setAudioAttributes(..., true)`. A transient focus loss can leave `playWhenReady=true` while Media3 applies `PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`, so the earlier manual-pause gate did not run when focus returned.
- The service now observes `Player.Listener.onPlaybackSuppressionReasonChanged(...)`. An already-playing RADIO item records a pending live reconnect on transient audio-focus suppression; when suppression returns to `PLAYBACK_SUPPRESSION_REASON_NONE` with `playWhenReady=true`, it reuses the existing `reconnectCurrentRadioAtLiveEdge(...)` path (`stop -> set current MediaItem -> prepare -> play`) and therefore discards the stale buffered radio position. No custom `AudioManager.OnAudioFocusChangeListener`, second player, or second MediaSession was introduced.
- LOCAL transient focus loss/gain remains standard position-preserving ExoPlayer resume and never enters the reconnect path. If the user explicitly pauses RADIO while focus is lost, focus gain does not force playback; a later explicit Play continues to use the already validated manual live-resume path. Source changes to LOCAL and service stop/destroy clear pending focus state.
- Added deterministic coverage for RADIO transient focus loss -> gain reconnect, LOCAL no-reconnect, gain without prior loss, and manual pause while focus is lost; all previous manual RADIO/LOCAL resume tests remain green.
- Validation APK SHA-256: `e6725c5fd05000d940b5d972602b2b5749c79b0a3ba533a4f50ae48e97362da9`. Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- CI approval is complete. Physical verification remains pending for at least one real transient audio-focus route (for example Instagram/video audio) plus RADIO and LOCAL regression checks before this refinement is declared physically approved.

## Local music

Use Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), persist URI access, recursively scan audio files inside the selected tree only, and build the playlist without broad storage permission.

## Cloud music provider architecture — Google Drive retired

Decision effective 2026-08-31:
- Google Drive is removed completely from the active TamalutRadio product scope. The reason is product scope, not a technical failure: the `drive.file` / Picker path and its diagnostic gates introduced more complexity and fragility than the feature's real benefit justifies.
- Historical reference: the previous Google Drive authorization/probe work, including the A1/A2/B/C diagnostic gate, was closed correctly for the behavior being tested. Retiring Drive does **not** reclassify that work as failed; it records a deliberate scope reduction after learning the practical limits of the integration.
- The only active music source is the existing local Storage Access Framework folder. Its persisted tree permission, recursive audio scan, queue behavior, repeat/shuffle behavior, Media3 playback, mini-player, notification, Now Playing, Android Auto and floating-overlay behavior remain regression boundaries.
- `:feature:drive`, Google OAuth/AuthorizationClient/Picker code, Drive REST code, Drive-specific UI/probes, Drive-specific persistence and Drive-specific tests must be removed. No Google Drive API or Google Sign-In / Play Services auth dependency may remain solely for Drive.
- `:core:cloud` remains as a minimal pure-Kotlin provider-neutral module. `CloudMusicSource` stays as an intentionally empty marker/abstraction with **no concrete implementations**, no Google dependency, no provider registry, no networking, no credentials and no playback behavior. This preserves a clean seam for a future provider only if one is explicitly approved (for example a provider offering an app-folder-style scope).
- No future cloud provider is authorized by this decision. Any provider must be specified and approved separately before implementation.

Removal verification contract:
- structural checks must prove there is no `:feature:drive` module in the active Gradle graph, no Drive/OAuth/Picker source under `:app` or feature modules, no Drive-specific Settings probe/card, and no Google Drive / Google Sign-In dependency left in project Gradle files.
- `:core:cloud` must compile independently and contain the empty `CloudMusicSource` abstraction without concrete provider implementations.
- local music unit tests and app regressions must pass, and a real GitHub Actions run must build `:app:assembleDebug` with persistent debug signer v1 and generate a verifiable APK before the removal reaches final closure.

## Persistence

DataStore:
- language
- theme mode
- selected local folder URI
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
- cloud-provider Wi-Fi-only mode.
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

## Proposte future approvate, da pianificare

### Overlay flottante di controllo sopra altre app

- **Percorso overlay 1/2 + 1.5/2 + 2/2 + refinement completamente validato in CI e fisicamente il 2026-08-29: Home, Recenti, root Back, permesso/revoca, pausa, Impostazioni, drag/snap, dismiss session-only, controlli playback, sincronizzazione esterna, capability, transizioni RADIO↔LOCAL, riconnessione RADIO al live edge, auto-collapse a 4 secondi e ritorno all'app tutti confermati.**
- Prevedere un overlay flottante che possa restare visibile sopra altre app, incluse Google Maps e Waze, anche dopo l'uscita da TamalutRadio tramite tasto Home.
- L'overlay dovrà offrire controlli minimi `precedente / pausa-play / successivo`, collegati alla stessa sessione Media3 condivisa e senza creare un secondo player.
- L'utente dovrà poter chiudere l'overlay senza fermare la riproduzione in corso; la chiusura è temporanea per la sessione esterna corrente e non disattiva la preferenza permanente.
- Richiede il permesso speciale Android **Visualizza sopra altre app** (`SYSTEM_ALERT_WINDOW`), da richiedere solo con azione e consenso espliciti dell'utente e con UX dedicata per stato permesso/negazione/revoca. La preferenza permanente resta separata dallo stato del permesso.
- Il raffinamento 1.5 introduce edge-tab minimale, drag/snap ai bordi, posizione persistita e lifecycle Coordinator/SessionState; il 2/2 aggiunge `precedente / play-pausa / successivo` nel solo pannello espanso, tutti collegati allo stato/controller Media3 condiviso.

### Floating overlay playback controls contract — sub-step 2/2

- [x] **2/2 — Shared playback transport controls in the expanded overlay.** Add `precedente / play-pausa / successivo` only to the expanded floating-overlay panel opened from the edge tab. The collapsed edge tab remains a minimal navigation affordance and must not gain transport actions.
- The overlay must observe the exact same `PlaybackController.state` / `PlaybackState` already used by the persistent mini-player, notification/session surfaces, Radio/Music synchronization, and Now Playing. Do not create a second player, MediaBrowser/controller, MediaSession, foreground service, or feature-owned playback state.
- Previous, play/pause, and next taps must delegate to the existing process-shared `PlaybackController` instance owned by `TamalutRadioRuntime`. Play/pause presentation must update from real shared `PlaybackState.isPlaying` changes regardless of whether the change originated from the overlay, notification/lock screen/media button, mini-player, or another in-app surface.
- Previous/next enabled state must be driven directly by `PlaybackState.canSkipPrevious` / `canSkipNext`, matching the actual Media3 capabilities at that moment. Disabled transport controls must not dispatch playback commands.
- Transport taps must not collapse, dismiss, move, or end the external overlay session. Conversely, tapping X, collapsing the edge panel, or dragging/snapping the overlay must never invoke play/pause/previous/next or otherwise alter playback.
- Radio and local music must use the same overlay transport path. Radio must preserve its existing fallback/live semantics; local music must preserve current queue ordering, repeat/shuffle behavior, and boundary capabilities including repeat-one/single-item cases.
- Keep the 1.5 lifecycle contract unchanged: Home/Recenti/root-Back admission through `onStop`, permission-Settings stop suppression, configuration-change suppression, session-only X dismissal, permission/preference separation, lazy WindowManager host, edge snap, and persisted normalized vertical placement.
- Add deterministic unit tests for transport delegation to the shared controller, play/pause icon/state synchronization when the real state changes, previous/next capability gating, control taps preserving expanded/session state, and regression coverage for both RADIO and LOCAL playback projections/actions.
- Verification requirement: a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, plus structural checks proving the overlay consumes shared playback state/controller and does not create another player/session/service. The persistent debug signing v1 certificate must be verified before promotion to `main`.

Validation record — floating overlay playback controls 2/2:
- Clean spec-before commit on `main`: `5f23f448dbee83eca5ec9ad54efe27c26e6b5073` (`docs: define floating overlay playback controls 2/2`).
- Clean implementation commit on `main`: `8c934e3a8ae66e677b4dd2191d8e985f7bf0c28c` (`feat: add shared playback controls to floating overlay`); validated source implementation on the temporary branch: `60c2471a0dc0da25d56d09d0697e7b99c8c46ad7`.
- GitHub Actions validation run `33263483135` passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 4m 42s, 193 actionable tasks (153 executed, 40 from cache).
- Structural result `OVERLAY_PLAYBACK_SHARED_ARCHITECTURE=PASS`: the overlay observes `playbackController.state`, projects the current shared `PlaybackState`, delegates only to the same process-shared `PlaybackController`, and contains no additional ExoPlayer, MediaBrowser, MediaSession, or foreground service.
- Play/Pause presentation is derived from real `PlaybackState.isPlaying`; Previous/Next enabled state and dispatch are gated by `canSkipPrevious` / `canSkipNext`. External state changes therefore reconcile back into the expanded overlay through the existing shared-state collector.
- Transport click handlers are isolated from edge-tab drag/collapse and X dismissal. Drag, collapse and session-only close contain no playback action path; transport clicks do not mutate overlay expanded/session state.
- Regression tests cover RADIO and LOCAL state/action paths, including preservation of local repeat/shuffle state and radio semantics.
- Persistent debug signer SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`; validation APK SHA-256: `e30c34ee27bc499c1b831e1c36a5911e36b872976281e1bfc32f1544d31de004`.
- CI validation and physical verification of the expanded overlay controls are complete; sub-step 2/2 is physically approved as part of the fully validated floating-overlay path.

### Radio live resume + overlay inactivity/app-entry refinement contract

- [x] **1/3 — Radio resume returns to the real live edge.** After a genuine pause→resume of an already-loaded RADIO item, `TamalutPlaybackService` must discard the paused source/buffer and re-install/re-prepare the same current radio `MediaItem` before continuing playback. LOCAL music must preserve ordinary position-preserving pause/resume and must never be re-prepared solely because playback resumes.
- Preserve the active radio fallback item/plan, single ExoPlayer/MediaLibrarySession architecture, repeat/shuffle rules, and initial-start behaviour; initial radio start must not double-prepare.
- [x] **2/3 — Expanded overlay auto-collapses after inactivity.** Arm a **4,000 ms** inactivity timeout after expansion. Any explicit interaction with expanded controls re-arms it. Timeout collapses only the UI to the edge tab; it must not dismiss the external session, move the overlay, change persistence, or touch playback. Hide/foreground/session-end paths cancel pending timers.
- [x] **3/3 — Dedicated return-to-app affordance.** Add a clearly separate TamalutRadio app-entry control in the expanded panel, isolated from Previous / Play-Pause / Next. It must reuse the same Now Playing PendingIntent construction used by the Media3 session activity, foreground `MainActivity` on `ACTION_OPEN_NOW_PLAYING`, and never issue playback commands. `onAppForeground()` remains the reset point for temporary dismiss/session state.
- Extract/reuse one shared Now Playing PendingIntent factory in `:core:playback`; notification/session and overlay must not maintain separate launch semantics.
- Preserve every physically approved overlay behavior from 1/2 + 1.5 + 2/2 and all RADIO/LOCAL playback semantics outside these three refinements.
- Add deterministic tests for RADIO pause→resume reconnect vs LOCAL normal resume, initial-start/source-transition safety, 4-second timer arm/re-arm/cancel/stale-task safety, and isolation of app-entry from transport. Structural verification must prove service and overlay use the same PendingIntent factory.
- Verification requirement: a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify persistent debug signer v1 and APK SHA-256; structurally prove one player/session, RADIO-only re-prepare, LOCAL preservation, 4,000 ms timeout, shared app-launch PendingIntent, and no playback side effects from collapse/app-entry.

Validation record — radio live resume + overlay inactivity/app-entry refinement:
- The complete pre-existing floating-overlay path `1/2 + 1.5 + 2/2` was physically approved by the user on 2026-08-29 after all 30 checklist cases passed across Radio, external synchronization/capabilities, overlay/playback independence, and RADIO↔LOCAL transitions.
- Clean spec-before commit: `911ea59ed24db7b0b1df77443763b6ab7dcda4b6` (`docs: define radio live resume and overlay polish`). Clean implementation/test commit: `be8cdeb14e5a5aea51fa889f67b8c9da04c8509b` (`fix: reconnect live radio and polish floating overlay`). Temporary helper workflows are excluded from the clean `main` product history.
- Real GitHub Actions validation run `33265518003` passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: BUILD SUCCESSFUL in 6m 15s; 193 actionable tasks (153 executed, 40 from cache); structural gate `LIVE_RESUME_OVERLAY_POLISH_STRUCTURE=PASS`.
- RADIO pause→resume reconnects the same active radio/fallback MediaItem through the single `TamalutPlaybackService`, discarding the paused source/buffer and re-preparing before play. LOCAL pause/resume remains position-preserving and never enters this reconnect path; initial radio start does not double-prepare and switching to LOCAL clears pending radio-resume state.
- Expanded overlay inactivity auto-collapses after 4,000 ms; explicit interaction re-arms the timer, lifecycle/session paths cancel it, and generation protection prevents stale callbacks. Timeout changes only expanded/collapsed UI state and does not touch playback, persistence, edge/height, or external-session semantics.
- The expanded panel has a dedicated TamalutRadio app-entry control separated from Previous / Play-Pause / Next. Overlay and Media3 notification/session reuse `PlaybackLaunchContract.createNowPlayingPendingIntent(...)` / `ACTION_OPEN_NOW_PLAYING`; app-entry does not issue playback commands. `MainActivity.onResume()` remains the foreground/session-dismiss reset point.
- Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`; validation APK SHA-256: `f502581dfcf48863ac5cb6487c909dad01112d731025c8324324fd65619732d3`.
- Setup-only validation run `33265451507` failed before compilation because the temporary workflow lacked access to the private persistent debug-signing release; no product result is attributed to that run.
- Status: implementation, CI validation, final Release verification, and physical validation A-D are complete for all three refinements. RADIO live pause→resume reconnect, 4,000 ms overlay auto-collapse, and the dedicated return-to-app affordance all passed physical testing on 2026-08-29. The complete floating-overlay path `1/2 + 1.5 + 2/2 + refinement` is therefore fully validated both in CI and physically.

## Decision log

### 2026-08-31 — Google Drive integration retired by product-scope decision

Google Drive is removed from TamalutRadio after evaluating the real integration cost of `drive.file`, AuthorizationClient/Picker behavior and the diagnostic workflow. This is explicitly a scope decision rather than a technical-failure verdict: the A1/A2/B/C diagnostic gate had been closed correctly for its tested behavior. The app returns to one active music source, the local SAF folder. `:core:cloud` is retained only as a pure provider-neutral seam with an intentionally empty `CloudMusicSource` abstraction and no concrete providers; any future provider (for example one with an app-folder-style permission model) requires a new explicit approval.

### 2026-08-29 — Google Drive music source authorized

Authorized the final original-plan feature as three verified sub-steps: (1) minimal provider-neutral `:core:cloud` contract plus Google `AuthorizationClient`/Picker foundation using only `drive.file`; (2) one selected Drive folder, persistence of folder ID only, recursive audio scan and `Locale / Google Drive` Music UX with explicit offline handling; (3) authenticated Drive playback through the existing single `TamalutPlaybackService`/MediaLibrarySession with no persisted Google credentials or parallel player. The repository is public, so no Web/server OAuth client secret, access token, refresh token, auth code or keystore may enter Git history.

### 2026-08-29 — Floating overlay refinement physically approved; complete overlay path closed

Physical checklist A-D completed with every case passing on the final Release `debug-20260829-210309-88c102a` targeting `88c102aee5db2b4e6e4d6a5567f090807cdb7dab`. RADIO pause→resume reconnects to the current live edge after a long pause while LOCAL resumes at the preserved track position; the expanded overlay auto-collapses after approximately 4 seconds of inactivity and correctly re-arms after interaction without affecting playback; the dedicated TamalutRadio app-entry affordance returns to Now Playing without issuing transport commands, and a subsequent external overlay session is created normally. APK SHA-256 `f502581dfcf48863ac5cb6487c909dad01112d731025c8324324fd65619732d3` and persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6` were verified by final Release run `33274897181`. With the earlier 30/30 approval of overlay 1/2 + 1.5 + 2/2, the entire floating-overlay path including this refinement is now fully validated in CI and physically.

### 2026-08-29 — Complete overlay physically validated; live-resume and overlay polish authorized

Physical checklist 2/2 completed with all 30 cases passing across Radio, external notification synchronization, Previous/Next capabilities, overlay/playback independence, and RADIO↔LOCAL transitions. The entire floating-overlay path 1/2 + 1.5 + 2/2 is therefore physically approved. Follow-up authorized: RADIO pause→resume reconnects to the live edge; the expanded panel auto-collapses after 4 seconds of inactivity; and a dedicated transport-separated app-entry affordance reuses the notification Now Playing launch contract. LOCAL pause/resume and every already-approved overlay behavior are regression boundaries.

### 2026-08-29 — Floating overlay playback controls 2/2 authorized

Physical validation of sub-step 1/2 plus refinement 1.5 is complete across Home, Recenti, root Back, permission/revocation, pause continuity, Settings suppression, drag/snap, collapse/expand, and session-only dismissal. Sub-step 2/2 is authorized to add Previous / Play-Pause / Next inside the expanded overlay only. The controls must project the existing shared Media3 `PlaybackState`, delegate exclusively to the process-shared `PlaybackController`, reflect external play/pause changes in real time, honor `canSkipPrevious` / `canSkipNext`, and preserve every 1.5 lifecycle/window interaction without playback side effects. Tests must include delegation, state synchronization, capability gating, and RADIO/LOCAL regressions before a real signed debug APK is promoted.

### 2026-08-29 — Floating overlay UX/lifecycle refinement 1.5 authorized

Physical testing of sub-step 1/2 confirmed in-place APK update, the explicit pre-permission explanation, persistence above Maps after Home, and close-without-stopping-audio behavior. Refinement 1.5 is authorized before transport controls: replace the wide fixed pill with a draggable edge tab that expands inward, split permanent preference/permission/external-session/UI state, make X a session-only dismiss, automatically create a new overlay session on a later Home/user-leave while playback is active, preserve `overlayEnabled` across permission denial/revocation, and persist edge plus normalized vertical placement. `FloatingOverlayCoordinator`, `FloatingOverlayWindow`, `OverlaySessionState`, `UserPreferences`, and the existing shared Media3 playback state must keep those responsibilities separate. Previous/play-pause/next remain deferred to sub-step 2/2.

### 2026-08-28 — Notification launch, grouped Radio, rescan clarification, and compact mini-player completed

Code commit `59b00390af0d21bd6bf113b645e9563ef637f3fb` completes the physical-test refinement. The Media3 `MediaLibrarySession` now exposes a `sessionActivity` PendingIntent that routes notification/lock-screen taps to `In Riproduzione`; `MainActivity` handles both cold launch and `onNewIntent` without creating another player/session. `Tutte le radio` is grouped visually as Marocco (6), Italia (1), and Sport (2), with unknown/future station IDs falling back to `Altro`; `Preferiti` remains a flat filtered list and the grouping requires no Room migration. The manual Music action is retained as `Riscansiona` because it explicitly re-runs the SAF folder scan, with explanatory UI text clarifying that it is a recovery action rather than a permission check. The local mini-player is now a single compact row: shuffle/repeat stay local-only as smaller visual icons with standard accessible IconButton touch targets, followed by previous/play-next, with no second mode row or `Loop playlist` label. GitHub Actions run `33160550788` passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, plus structural checks for `setSessionActivity`, `onNewIntent`, radio grouping, rescan copy, single-row playback chrome, and absence of `SYSTEM_ALERT_WINDOW`. Build completed successfully in 6m 3s; debug APK SHA-256: `b88dcaf5644efe8cbcca492d63aecf6fccc8a1cdd10f6b9e0e5a30adbf89bff7`.

### 2026-08-28 — Mini-player playback discovery refinement completed

The refinement is implemented by code commit `090f536b8103b6bd37f8313b867c377d379f2fbe`. The persistent mini-player now exposes shuffle and repeat controls only while the shared source is local music; the controls observe and mutate the same Media3 session state as the Music screen and use the shared repeat cycle `OFF -> ALL -> ONE -> OFF`. Every newly created local queue now starts in repeat `ALL` (loop playlist) so sequential playback wraps naturally by default, while Radio still explicitly forces repeat OFF and shuffle OFF. `In Riproduzione` remains as a distinct expanded detail destination with a prominent source/cover area, current title/source/playback status and local mode summary, while previous/play-pause/next remain exclusively in the persistent mini-player to avoid stacked duplicate players. GitHub Actions validation run `33150049604` passed `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, structural checks for the non-duplicated Now Playing surface, and the manifest check that `SYSTEM_ALERT_WINDOW` remains absent. The debug APK SHA-256 was `349d154d5e35a6f1aa3725d169fe359a4a12482eddb744e3812079428ae2ed64`. No second player was introduced and the approved future floating-overlay proposal remains unimplemented.

### 2026-08-28 — Shared playback state, internal controls, and local repeat/shuffle completed

The playback-state refinement is implemented by code commits `f2783c8935cf64f3fc1e83c3c3a27e204e75f076` and `0ea7050e395da3341ed362e22f9cef925d0424f2`. `:core:playback` now exposes one shared Media3 controller/state observed by Radio, local Music, the persistent mini-player, and the functional `In Riproduzione` destination. Radio and local-track `In riproduzione` markers are derived from the active shared source, so radio -> local and local -> radio transitions clear the stale marker on the inactive feature. The mini-player above bottom navigation and the Now Playing destination delegate play/pause/previous/next to the same `TamalutPlaybackService` session; no second player or feature-owned MediaBrowser remains. Local Music adds shuffle and repeat OFF / ALL / ONE controls, with policy/tests rejecting repeat/shuffle changes for radio playback. GitHub Actions validation run `33146979944` passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, plus structural/manifest checks. The debug APK SHA-256 was `141f861addd9f3551cb601eb3d89ba5ae56c9c723e81ff3694ec14dc17c2e643`. `SYSTEM_ALERT_WINDOW` remains absent: the approved floating-overlay proposal is recorded for future planning only and was not implemented in this refinement.


### 2026-08-28 — Debug APK distribution moved to GitHub Releases

Debug APK distribution is now implemented by permanent workflow `.github/workflows/publish-debug-release.yml` in `d0ccb0dbef099d81a67bee29160bdee9c0ae11d0`. The workflow supports manual builds from `main` or an explicit ref, resolves and checks out the exact target commit, runs `:app:assembleDebug`, computes the APK SHA-256, creates a unique `debug-<UTC date/time>-<short commit>` GitHub prerelease targeting that commit, and attaches `TamalutRadio-debug-<short commit>.apk` directly to the Release without `actions/upload-artifact`. Validation run `33144891560` succeeded against polished snapshot `f685090bd5997b13e56753949cfe375d3ff93156` and published prerelease `debug-20260828-053504-f685090`. The APK asset SHA-256 is `75e29a967281f4dd396d78c8a17c8d4745f989260c09ecd980aded8949c26d8f`. This distribution path does not consume GitHub Actions artifact storage quota.

### 2026-08-28 — UI polish sub-step 3/3 completed

Local Music visual refinement is implemented in `06b14638c286d119a8a0d03fc0abe05b57ee2c72`. The screen now uses a coherent Atlas Night header hierarchy, a shaped selected-folder SAF panel, refined actions and loading/empty/error states, and rounded bordered/elevated local-track cards with leading music icon treatment, clearer metadata hierarchy, and current-track emphasis retaining the accessible `In riproduzione` indication. SAF folder selection/persisted permission/scanning and `LibraryViewModel::playTrack` behavior remain unchanged, and playback still delegates to the existing shared Media3 service with no feature-level ExoPlayer. GitHub Actions run `33117308858` passed `:feature:library:testDebugUnitTest` and `:app:assembleDebug`, verified the local Music UI wiring, SAF boundaries, merged manifest, absence of `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE`, and `MANAGE_EXTERNAL_STORAGE`, and produced a real debug APK. The APK SHA-256 was `7134761b3db31271385f0a6b1341f6ebcc6a11f3d19bdaea5e98e3862e80cdcc`; no APK artifact was uploaded. This completes the three-step Atlas Night / Material Air / Sahara Pulse visual refinement pass.


### 2026-08-27 — UI polish sub-step 2/3 completed

Radio visual refinement is implemented in `e790cbd563d5120ef8e59c39c4152febb986ccce`. The screen now uses a stronger Atlas Night header hierarchy, transparent Material 3 tab presentation, shaped status surfaces, rounded bordered/elevated station cards, leading radio icon treatment, clearer favorite affordance, and current-station emphasis. The active station displays a compact Sahara Pulse/terracotta `LIVE` badge together with `In riproduzione`, while catalog, favorites and `RadioViewModel::playStation` behavior remain unchanged. GitHub Actions run `33116565196` passed `:feature:radio:testDebugUnitTest` and `:app:assembleDebug`, verified the UI wiring and absence of a feature-level ExoPlayer. The real debug APK SHA-256 was `0ad76b3f7b965d9b65e37095fcbb71e78cb77f530da28f0aa3a9cbb3425a9f45`; no APK artifact was uploaded.


### 2026-08-27 — UI polish sub-step 1/3 completed

Application shell refinement is implemented in `89c599cd18356d066bd0b8bc54181813587767a3`. The provisional top-button switcher is replaced by a Material 3 `NavigationBar` with icon/label destinations for Radio, Musica, In Riproduzione, and Impostazioni. Radio and Musica retain their existing ViewModels/routes; the latter two destinations are intentionally minimal themed placeholders. GitHub Actions run `33115829966` passed `:app:assembleDebug` and structural destination checks. The real debug APK SHA-256 was `6951fb4ffd2c8c20c9872b3be931c9a617627a73f73a3f152d17805edc4c80d3`; no APK artifact was uploaded.


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

Validation record - radio audio focus live resume - physical approval:
- [x] Physical checklist A-E completed and approved on 2026-08-30.
- RADIO transient audio-focus loss followed by gain resumes at the current live edge rather than the frozen pre-loss buffer.
- LOCAL transient audio-focus loss followed by gain preserves the exact playback position with no radio-style reconnect.
- Critical manual-intent guard approved: if the user manually pauses RADIO while transient audio focus is lost, focus gain / suppression clear does not auto-resume playback; the later explicit Play reconnects to the current live edge.
- Repeated Instagram/video focus-loss path and the existing manual pause -> Play live-edge path both passed physical validation.
- Approved physical-test APK: `TamalutRadio-debug-67d5d57.apk`, tag `debug-20260829-223213-67d5d57`, target `67d5d5774682c70898669f05922f190818f87f93`.
- APK SHA-256: `e6725c5fd05000d940b5d972602b2b5749c79b0a3ba533a4f50ae48e97362da9`; signer SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.

### Google Drive folder access probe contract

- [ ] Before implementing the permanent Drive library UX, perform one real-device authorization/picker probe using only the already-approved `https://www.googleapis.com/auth/drive.file` scope.
- The probe must use the existing Google Play services `AuthorizationClient` request with `PICKER_OAUTH_TRIGGER=true`, `PICKER_ALLOW_FOLDER_SELECTION=true`, `setOptOutIncludingGrantedScopes(true)`, and consent/account selection prompts; legacy Google Sign-In remains forbidden.
- The selected folder ID must be obtained from `AuthorizationResult.getTokenResponseParams()` / `picked_file_ids`; the access token may be used only in memory for Drive API calls and must never be persisted, logged, committed, or written to DataStore/Room/files.
- The probe must call Drive API v3 `files.list` with a parent query for the selected folder, return direct children metadata, locate at least one child folder when present, and repeat `files.list` on that child folder to verify nested access with `drive.file`.
- The physical-test UI may be a temporary diagnostic surface under Settings, but it must clearly report authorization, selected folder ID in redacted form, direct-child result, nested-folder result, and recoverable network/API errors. It must not become the final Music/Drive UX and must not start playback.
- No automatic fallback to `drive.readonly` or broader Drive scopes is allowed. If direct or nested enumeration fails because `drive.file` does not grant access to existing children, stop at the gate and record the exact result before changing scope or UX.
- Verification before physical testing: unit tests for picked-ID parsing, Drive query/JSON parsing and nested-probe decision flow; real GitHub Actions build of `:feature:drive:testDebugUnitTest`, app regression tests and `:app:assembleDebug`; structural checks must prove `drive.file` only, no legacy GoogleSignIn, no offline/server auth, and no token persistence.

Validation record — Google Drive folder access probe:
- Clean implementation snapshot: `17a80694e49b3922826032c6f16a1be951faac0b` (`feat: add Google Drive folder access probe`).
- The temporary Settings diagnostic uses the existing `AuthorizationClient` flow and Picker folder selection, parses `picked_file_ids`, keeps the bearer access token in memory only, and calls Drive API v3 `files.list` first on the selected folder and then on the first visible child folder. It does not persist credentials and does not start playback.
- Scope remains exactly `https://www.googleapis.com/auth/drive.file`; no `drive.readonly`, broad Drive scope, legacy `GoogleSignIn`, offline access, server auth code, token persistence, second player, or Drive playback path was introduced.
- Initial CI run `33332293906` reached the app compile boundary and failed because the Drive module exposed Google authorization types through a Gradle `implementation` dependency; no product conclusion was drawn. The dependency boundary was corrected to `api(play-services-auth:22.0.0)` because those Google types are part of the module public API, plus Kotlin cross-module smart-casts were stabilized.
- Final GitHub Actions run `33332605337` passed `:feature:drive:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, and structural security checks: `DRIVE_FOLDER_ACCESS_PROBE_STRUCTURE=PASS`. BUILD SUCCESSFUL in 3m 42s; 219 actionable tasks (179 executed, 40 from cache).
- Validation APK SHA-256: `d840232ec718c37606f4dd972978886021efc6382475383e19af212436e3f960`; persistent debug signer SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [ ] Physical folder-access verdict remains pending. The probe must be run on the configured Google test account against a known non-empty test folder containing at least one direct audio file and one subfolder with an audio file. `drive.file` is not approved for the final folder UX until both direct and nested enumeration are observed on-device.
- If the physical probe cannot enumerate known pre-existing direct/nested children, stop at this gate; do not broaden the OAuth scope without an explicit product decision.

### Drive A1/A2/B/C same-token diagnostic contract

- [ ] Before drawing a final conclusion about `drive.file` child visibility, run one additional real-device diagnostic using exactly the existing `https://www.googleapis.com/auth/drive.file` scope and one Picker-selected folder. Do not request `drive.readonly`, broad `drive`, offline access, or any new OAuth scope.
- The selected item must still be verified read-only with Drive API v3 `files.get`; the temporary UI must show both the real selected folder name and a redacted folder ID. Access tokens remain memory-only and must never be persisted or logged.
- All measurements A1, A2, B, and C must run sequentially inside the same probe attempt using the exact same in-memory access token and selected folder ID.
- **A1 — parent-filter baseline:** call `files.list` with `q='<selectedFolderId>' in parents and trashed = false`, `corpora=user`, `spaces=drive`, `pageSize=1000`, `orderBy=name_natural`, and fields sufficient to expose `nextPageToken`, `incompleteSearch`, and `files(id,name,mimeType,parents)`. Record each page separately, including item count, presence/absence of `nextPageToken`, `incompleteSearch`, and returned item IDs/names/types/parents.
- **A2 — same-token repeat:** immediately repeat the exact A1 request without reopening Picker or obtaining a different token. Compare the final ordered/set result with A1 so same-token server inconsistency can be distinguished from grant changes between authorization sessions.
- **B — forced pagination:** repeat the A1 parent query with `pageSize=1` and follow every `nextPageToken` until exhaustion, including partial/empty pages. Record page-by-page telemetry and compare the final item-ID set with A1/A2.
- **C — authorized-universe query:** using the same token, call `files.list` with `q='trashed = false'`, `corpora=user`, `spaces=drive`, `pageSize=1000`, `orderBy=name_natural`, and fields sufficient for `nextPageToken`, `incompleteSearch`, and `files(id,name,mimeType,parents)`. No parent predicate is allowed in C. Record page telemetry and the final visible item-ID set.
- The diagnostic report must automatically classify the key comparison: if C exposes exactly the same single item ID as the parent-filter measurements, report that the current `drive.file` session's visible universe is consistent with a one-item grant; if C exposes a larger set while A1/A2/B isolate one item, report that the discrepancy is parent-query-specific and requires further investigation before closing the gate. Other combinations must be reported without overclaiming.
- The existing Drive implementation remains read-only by code: only HTTP GET, `files.get`, and `files.list`; create/update/delete/upload remain forbidden and protected by tests/structural CI.
- Unit tests must cover query construction for A/C, `corpora=user`, `orderBy=name_natural`, `incompleteSearch` parsing, partial/empty-page pagination, pageSize=1 traversal, same-token A1/A2 comparison, and C-vs-parent-set classification. A real GitHub Actions run must pass Drive tests, app regressions, `:app:assembleDebug`, read-only structural checks, persistent debug signer verification, and record the exact APK SHA-256 before physical testing.
- Physical folder-access verdict remains pending until this A1/A2/B/C diagnostic is run on the known non-empty test folder. Do not broaden OAuth scope based on the earlier one-visible-child observations alone.

### Drive A1/A2/B/C diagnostic implementation record

- The preceding physical `ece87fa` probe was not treated as a final scope verdict: the same selected folder (redacted as `1WEj…X7Ac`) exposed one direct child per authorization attempt, but the visible child changed between attempts (`Yay_Rane_Lhawanwa.mp3` then `B2`) and no subfolder was visible. This inconsistency triggered the same-token diagnostic below rather than any OAuth scope broadening.
- Clean product snapshot: `6a05483ae62c6d287b0f6ced4fc55bc415249788` (`feat: expose Drive A1 A2 B C diagnostics`), built on the approved spec-before contract.
- The temporary Settings probe now performs, after one folder Picker selection and one in-memory `drive.file` authorization result, sequential A1/A2/B/C measurements using the exact same access-token value and selected folder ID. The real folder name from `files.get` plus a redacted ID are shown.
- A1 uses the selected-parent query with `corpora=user`, `spaces=drive`, `pageSize=1000`, `orderBy=name_natural`; A2 immediately repeats A1 with the same token; B repeats the parent query with `pageSize=1` and follows all page tokens including empty intermediate pages; C uses `q='trashed = false'` without a parent predicate and the same token.
- Every measurement records page number, item count, `nextPageToken` presence, `incompleteSearch`, item name/type/redacted ID/redacted parents, total rows and unique IDs. Classification first rejects `incompleteSearch=true` as inconclusive, then checks same-token A1/A2 consistency, forced-pagination B consistency, and C both including and excluding the selected folder ID.
- The Drive code boundary remains read-only: OAuth scope stays exactly `https://www.googleapis.com/auth/drive.file`; only HTTPS GET, `files.get`, and `files.list` are admitted; create/update/delete/upload remain forbidden. Tokens are memory-only and are not persisted or logged.
- GitHub Actions validation run `33367672145`, job `99411612030`, passed `DRIVE_A1_A2_B_C_STRUCTURE=PASS`, `:feature:drive:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`. Unit/regression suite: BUILD SUCCESSFUL in 2m01s, 169 actionable tasks (126 executed, 43 cache); APK assemble: BUILD SUCCESSFUL in 1m43s.
- Validated and published APK SHA-256: `ff46c3dffd26d218cbb7073f3b597c3d618950bbbc007879de3f44d24e56e1e8`. Persistent debug signer SHA-256 remains `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- Publication run `33368035350` successfully rebuilt the exact product snapshot, verified the expected byte-identical APK checksum and signer, and published prerelease tag `debug-20260831-072543-6a05483`, target `6a05483ae62c6d287b0f6ced4fc55bc415249788`, asset `TamalutRadio-debug-6a05483.apk`.
- [ ] Physical A1/A2/B/C verdict remains pending on the known non-empty test folder. Capture the complete on-device report. Do not broaden OAuth scope or start permanent Drive UX until the same-token A1/A2/B/C evidence has been interpreted.

### Google Drive Option A — direct multi-file Picker architecture (supersedes folder enumeration)

- [x] **Product decision — Option A.** Keep OAuth scope exactly `https://www.googleapis.com/auth/drive.file`. Do not add `drive.metadata.readonly`, `drive.readonly`, broad `drive`, offline access, server auth codes, or refresh-token persistence.
- [x] **Folder-enumeration architecture retired.** The permanent Drive music UX must no longer depend on selecting one Drive folder and recursively enumerating its pre-existing children. Earlier folder-selection/recursive-scan requirements in the provisional 2/3 contract are superseded by this section.
- [ ] **Next implementation objective — direct multi-file selection only.** Reconfigure the Android Google Picker authorization flow to use `PICKER_OAUTH_TRIGGER` plus `PICKER_ALLOW_MULTIPLE`, with `setOptOutIncludingGrantedScopes(true)` and the existing consent/account-selection policy. The final file-selection path must not enable `PICKER_ALLOW_FOLDER_SELECTION` / folder selection for automatic enumeration.
- The user navigates inside Google Drive folders in the Google Picker and explicitly selects the audio files to add. A single Picker session may select multiple files; additional Picker sessions may add files from other folders/subfolders as needed. Each selected file ID is therefore explicitly granted through the Picker under `drive.file`.
- The Picker should be restricted to appropriate audio MIME types when the Android Picker parameter supports the required set without excluding valid supported audio; MIME/extension fallback policy must be documented before final library ingestion.
- This first objective ends at Picker configuration, returned `picked_file_ids` parsing, and physical UX verification. Persistence/merge/deduplication of selected Drive files, `CloudMusicSource` library integration, authenticated downloads, and Media3 playback remain a later objective.
- Tokens remain memory-only. No Google access token, refresh token, authorization code, account password, or bearer token may enter DataStore, Room, MediaItem metadata, logs, source code, or app-controlled disk storage.
- The temporary `Probe Google Drive A1/A2/B/C` Settings surface must remain available only until the direct multi-file Picker flow is proven in a signed physical build; once the new selection flow works, remove/disable that diagnostic Settings entry rather than retaining it as permanent UX.

#### Native Picker “Select all” finding / UX boundary

- Current Google documentation for desktop/mobile Picker explicitly exposes multiple selection through `allow_multiple=true` / Android `PICKER_ALLOW_MULTIPLE`, plus MIME filtering, file-ID filtering, and optional folder selection. It does **not** expose or document a `Select all` / `PICKER_SELECT_ALL` feature, parameter, callback, or app-controlled toolbar command.
- Therefore TamalutRadio must not assume or contractually depend on a native “Seleziona tutto” affordance. Enabling multiselect is the only documented app-side control relevant to bulk selection. If a particular Google Picker build/account/device surfaces an internal contextual “Select all” action, it may be used by the user, but TamalutRadio cannot enable, force, expose, or reliably test that undocumented UI through the Picker API.
- Do not implement an app-owned “select all files in this Drive folder” outside the Picker. With `drive.file`, TamalutRadio cannot know the complete pre-existing contents of an ungranted folder before explicit user selection; a custom out-of-Picker select-all would therefore violate the diagnosed capability boundary.
- First physical verification of the multi-file Picker must explicitly inspect whether the current Google UI shows any native contextual Select-all command after multiselect is active. Absence is an accepted Picker limitation and must be reported before proposing any custom UX.

### Google Drive diagnostic gate closure — A1/A2/B/C

- [x] **Gate closed — 2026-08-31.** The folder-enumeration diagnostic is complete as a product decision gate. The observed behavior is accepted as the known least-privilege limitation of `drive.file`, not a TamalutRadio pagination/query bug: selecting/granting a folder does not provide a reliable contract for recursively discovering all of its pre-existing children.
- A1/A2/B/C were built and validated specifically to distinguish parent-query/pagination behavior from the visible authorization universe while keeping the same token and `drive.file` scope. The diagnostic evidence is sufficient for architecture selection; no broader scope will be requested to preserve folder enumeration.
- Resolution: adopt direct Picker selection of the desired files (Option A), potentially across multiple sessions/folders, so every Drive file consumed by TamalutRadio is explicitly user-selected/granted.
- The prior provisional `2/3 — Drive folder selection, persistence and audio scan` architecture is superseded. The next 2/3 work must be redefined around direct selected-file ingestion; recursive folder scanning is no longer a product requirement.
- No `drive.metadata.readonly` fallback is authorized. Any future request for a broader Drive scope requires a new explicit product/security decision and spec-before commit.

### 2026-08-31 — Google Drive Option A authorized; diagnostic folder gate closed

The Drive folder-access/A1-A2-B-C gate is closed with the product conclusion that the observed inability to enumerate all pre-existing descendants is a `drive.file` capability boundary rather than an app query/pagination defect worth solving by broadening OAuth scope. Option A is approved: keep only `drive.file`, switch the Google Picker to direct multiple-file selection, let users navigate folders and explicitly grant the audio files they want, and allow successive selection sessions for additional folders. Current Google Picker documentation exposes multiselect but no app-controllable/documented Select-all feature, so TamalutRadio will not invent an out-of-Picker select-all. The immediate implementation objective is Picker multiselect plus physical inspection of the native Picker UI; selected-file library/CloudMusicSource integration is deferred to the following objective.

### Validation record — Google Drive integration removal closure

- [x] **Ciclo chiuso — 2026-08-31.** Google Drive è stato rimosso dal prodotto per scelta di scope: la complessità/fragilità operativa di `drive.file` + Picker è superiore al beneficio reale per TamalutRadio. Il precedente gate diagnostico A1/A2/B/C resta uno storico tecnicamente chiuso correttamente e non rappresenta un fallimento tecnico.
- [x] **Unica sorgente musicale attiva: cartella locale SAF.** Il modulo `:feature:drive`, OAuth/`AuthorizationClient`, Picker Drive, probe A1/A2/B/C, implementazioni REST Drive e relativi test specifici sono rimossi dal grafo prodotto.
- [x] **Astrazione cloud preservata senza provider.** `:core:cloud` resta provider-neutral; `CloudMusicSource` è intenzionalmente una interfaccia marker vuota, senza implementazioni concrete e senza dipendenza da `:core:model`, pronta solo per un futuro provider approvato esplicitamente.
- [x] **Modello/persistenza ripuliti.** `MediaSourceType.DRIVE` è rimosso. Un eventuale valore DataStore storico `"DRIVE"` viene ignorato come `lastPlayed` non più valido e non viene reinterpretato come elemento locale.
- [x] **Gate strutturale:** `DRIVE_REMOVAL_STRUCTURE=PASS` sul commit prodotto validato `4fabc8f3652b152adcd0a963cf8686d80fb50c3e`.
- [x] **Dipendenze runtime:** `DRIVE_RUNTIME_DEPENDENCIES=ABSENT`; nessuna `play-services-auth`, `google-api-services-drive`, `google-http-client` o `google-oauth-client` nel `debugRuntimeClasspath` dell'app.
- [x] **Test reali GitHub Actions:** run `33375438027`, job `99435757393`, PASS per `:core:model:test`, `:core:cloud:test`, `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`; `BUILD SUCCESSFUL in 57s`, 158 task azionabili (119 eseguite, 39 da cache).
- [x] **Build APK reale:** `:app:assembleDebug` PASS nello stesso run; `BUILD SUCCESSFUL in 1m 30s`, 165 task azionabili (46 eseguite, 119 up-to-date).
- [x] **APK verificato:** 23,516,532 byte; SHA-256 `838ca84811180532243b6f4f4c1861eaf97ea7723d9e0a8f655cac1d489018d3`; signer debug v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] **Permessi APK controllati:** restano i permessi funzionali già necessari (`SYSTEM_ALERT_WINDOW`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`); nessuna integrazione Google Drive/OAuth è presente nel prodotto.
- [x] **Promozione:** il commit prodotto validato `4fabc8f3652b152adcd0a963cf8686d80fb50c3e` è stato promosso fast-forward su `main` dal run `33375841545`, senza introdurre workflow CI temporanei nella cronologia prodotto.
- Il cleanup dei branch temporanei Drive/removal è repository hygiene successiva a questo commit di chiusura e non modifica il prodotto validato.

### Overlay 1.5 physical validation — 17/17 PASS (2026-08-31)

- [x] Physical validation on the current no-Drive debug build passed all 17 targeted checks: Settings/card presence, explicit overlay permission flow, active-playback admission, Home, expand/collapse, cross-edge drag/snap, vertical drag/bounds, inward expansion from both edges, persisted placement, Recenti, root Back, session-only X dismissal, X preserving the permanent preference, new-session restoration after X, paused-overlay continuity after admission, and general stability/no duplicate overlay/player behavior.
- [x] The result closes the physical gate for the approved 1.5 lifecycle/window model. Home/Recenti/root-Back parity, edge placement, session-only dismissal, pause continuity, and foreground reset are approved on a real device.
- [x] Google Drive removal did not alter overlay navigation or reorder other Settings cards; the physical overlay validation therefore remains scoped to the overlay behavior itself.

### Floating overlay playback controls contract — sub-step 2/2 recertification

- [x] **2/2 — Previous / Play-Pause / Next in the expanded overlay.** The expanded floating overlay must expose exactly the three transport controls `Precedente`, `Play/Pausa`, and `Successivo`; the collapsed edge tab remains a minimal expand/drag affordance with no transport actions.
- The controls must observe the same process-shared `PlaybackController.state` / `PlaybackState` already used by the app playback chrome and Media3 session. They must delegate only to the existing `PlaybackController` owned by `TamalutRadioRuntime` and therefore to the existing Media3 `MediaLibrarySession`/player.
- No second ExoPlayer, MediaBrowser/controller, MediaSession, foreground service, playback queue, or feature-owned playback source of truth may be created for the overlay.
- Play/Pause presentation must follow the real shared `PlaybackState.isPlaying` and update when playback changes from the overlay, notification/lock screen/media buttons, mini-player, Music, Radio, or other existing shared surfaces.
- Previous/Next enabled state and command dispatch must follow `PlaybackState.canSkipPrevious` / `canSkipNext`; disabled controls must never dispatch skip commands.
- Radio and local music use the same transport path. Radio fallback/live behavior must remain unchanged; local queue ordering, repeat/shuffle state, and boundary capabilities must remain unchanged.
- Transport taps must not collapse, dismiss, move, or terminate the overlay session. Collapse, drag/snap, X dismissal, foreground reset, permission handling, and placement persistence must not issue playback transport commands.
- Preserve the fully approved 1.5 lifecycle: `onStop` admission for Home/Recenti/root Back, configuration-change suppression, permission-Settings stop suppression, session-only X, permission/preference separation, lazy overlay WindowManager host, edge snap, normalized vertical placement, and paused-overlay continuity.
- Current repository note: the production 2/2 implementation and unit tests were already introduced historically by `8c934e3a8ae66e677b4dd2191d8e985f7bf0c28c` and survived subsequent changes including Drive removal. This cycle must not duplicate that implementation; it re-certifies the current no-Drive tree and may add only regression coverage required to protect the contract.
- Required verification: real GitHub Actions must pass `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, plus structural checks proving shared-controller wiring and absence of another player/session/service. The persistent debug signer v1 and APK SHA-256 must be verified before promotion.
- After green validation: promote only the validated clean regression commit to `main`, append a spec-after validation record, publish the exact final `main` snapshot through GitHub Releases (not Actions artifacts), then delete every temporary 2/2 branch/workflow.

### Validation record — floating overlay playback controls 2/2 recertification

- [x] **2/2 recertified on current no-Drive tree — 2026-08-31.** The production transport implementation already introduced historically by `8c934e3a8ae66e677b4dd2191d8e985f7bf0c28c` was intentionally retained rather than duplicated. This cycle added only the architecture regression guard commit `570b26163aaab8e333470334c4836f9eaf3cefed` (`test: lock overlay shared playback architecture`).
- [x] The expanded overlay exposes Previous / Play-Pause / Next through the existing `OverlayPlaybackControls` path. State is projected from the process-shared `PlaybackController.state`; transport actions delegate back to that same controller and existing Media3 session/player.
- [x] Structural gate `OVERLAY_PLAYBACK_SHARED_ARCHITECTURE=PASS`: no overlay-owned `ExoPlayer.Builder`, `MediaBrowser.Builder`, `MediaSession.Builder`, `MediaSessionService`, or `startForegroundService` construction is present in the overlay production path.
- [x] Real GitHub Actions validation run `33379843742`, job `99449471900`, passed `:core:preferences:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 2m 10s, 152 actionable tasks (116 executed, 36 from cache).
- [x] The same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 53s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] Validation APK: 23,516,532 bytes; SHA-256 `838ca84811180532243b6f4f4c1861eaf97ea7723d9e0a8f655cac1d489018d3`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] APK permission inspection retained only the expected app permissions including `SYSTEM_ALERT_WINDOW`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `ACCESS_NETWORK_STATE`, and `WAKE_LOCK`; no Google Drive/OAuth integration is reintroduced.
- [x] The exact validated regression commit was promoted fast-forward to `main` by GitHub Actions run `33380268097`.
- [x] Overlay 1.5 physical prerequisite remains closed at 17/17 PASS. The next gate is physical verification of the 2/2 transport controls from the newly published final-main debug APK.
### Physical closure — floating overlay playback controls 2/2 (2026-08-31)

- [x] **Overlay 2/2 physically approved — 10/10 PASS.** Real-device verification passed transport commands, notification synchronization, repeated-use stability, Radio behavior, X/collapse/drag isolation, and shared-state coherence when returning to TamalutRadio.
- [x] The physical result confirms that Previous / Play-Pause / Next use the existing shared `PlaybackController` / Media3 state, with no duplicate player/session/service behavior observed.
- [x] The floating-overlay cycle is therefore formally closed in CI and on device. Future changes must preserve the validated 1.5 lifecycle, placement, session-only dismiss, shared transport state, and Radio/LOCAL behavior as regression boundaries.

### Sleep Timer v1 implementation contract

- [x] **Sleep Timer v1 — Off / 15 / 30 / 45 / 60 minutes.** Add one shared sleep-timer state owned by the existing playback architecture. The timer must expose exactly the presets `Off`, `15`, `30`, `45`, and `60` minutes in v1.
- [x] **Visible shared countdown.** While active, expose the remaining time in both the expanded `In Riproduzione` / Now Playing surface and the persistent main playback chrome so both surfaces render the same shared timer state rather than independent local countdowns.
- [x] **Single Media3 playback authority.** Expiry must stop the current audio through the existing process-shared `PlaybackController` / Media3 session path. Do not create another ExoPlayer, MediaBrowser/controller, MediaSession, foreground service, alarm-service playback stack, or feature-owned playback source of truth.
- [x] **Expiry behavior.** When the deadline is reached, stop playback without mutating or rebuilding the queue before expiry and without changing repeat mode, shuffle mode, radio fallback plan, or source selection. Expiry must not create duplicate stop paths or a parallel playback lifecycle.
- [x] **Cancel and replace.** Selecting `Off` cancels an active timer immediately. Selecting another preset while a timer is active replaces the previous deadline atomically and the visible countdown must switch to the new deadline without leaving multiple scheduled expiry callbacks active.
- [x] **Pre-expiry non-interference.** Before the deadline, Sleep Timer must have no effect on Previous/Next, play/pause, queue ordering, local repeat/shuffle, Radio fallback/live-edge behavior, overlay controls, notification/lock-screen controls, or audio-focus handling.
- [x] **Deterministic timing abstraction.** Timer logic must use an injectable clock/scheduler (or equivalent deterministic abstraction). Unit tests must advance virtual/fake time directly; no test may wait 15/30/45/60 real minutes and no correctness assertion may depend on wall-clock sleeping.
- [x] **Lifecycle and state consistency.** The timer state must remain coherent while navigating between main UI and Now Playing and while playback continues in background. Ordinary UI recreation must not create a second timer or duplicate expiry callback.
- [x] **Explicit tests.** Cover preset mapping, countdown projection, Off cancellation, replacing one active timer with another, exact expiry stop delegation through the shared playback controller, single-callback behavior, no pre-expiry playback mutation, and UI projection consistency between main playback chrome and Now Playing.
- [x] **Structural verification.** CI must prove the sleep-timer path does not construct another ExoPlayer, MediaBrowser/controller, MediaSession, or foreground service and that expiry delegates to the existing shared playback controller.
- [x] **Verification requirement.** Before promotion to `main`, a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with persistent debug signer v1, generate and verify a real APK, and record its SHA-256.
- [ ] **Closure workflow.** After green validation, promote only the validated clean product/test commit to `main`, append a spec-after validation record, publish the exact final `main` snapshot as a GitHub prerelease asset (not an Actions artifact), then delete every temporary Sleep Timer branch/workflow.

### Validation record — Sleep Timer v1

- [x] **Validated product commit:** `4e48ced8f86262b4b5b5e25c5ea39acf38355191` (`feat: add shared Sleep Timer v1`), direct child of spec-before `ddadf45886e74c96adf1dc6c872b76b5e7ff4d82`.
- [x] **Shared deterministic timer:** one process-shared `SleepTimerController` exposes exactly Off / 15 / 30 / 45 / 60 minutes, uses an injectable `SleepTimerScheduler`, a monotonic production clock, one cancellable scheduled callback and generation invalidation for stale callbacks. Unit tests advance fake time directly; no real 15/30/45/60-minute waits are used.
- [x] **Shared UI state:** the same `SleepTimerState` is collected once from `TamalutRadioRuntime` and projected into both the persistent mini-player and expanded `In Riproduzione` surface. Active countdown formatting is shared; selecting Off cancels immediately and selecting another preset atomically replaces the deadline.
- [x] **Media3 expiry path:** expiry delegates to the existing process-shared `PlaybackController` through `playback(context)::stopPlayback`; the concrete Media3 controller calls `Player.stop()` on its existing `MediaBrowser`. No second ExoPlayer, MediaBrowser/controller, MediaSession, foreground service or parallel playback stack was introduced.
- [x] **Pre-expiry isolation:** timer scheduling/countdown code contains no queue replacement, repeat/shuffle mutation, Radio fallback/live-edge mutation, transport command, overlay mutation or audio-focus path. Expiry stops audio without clearing/rebuilding the queue.
- [x] **Structural gate:** `SLEEP_TIMER_SHARED_ARCHITECTURE=PASS`.
- [x] **Real GitHub Actions validation:** run `33382559887`, job `99457981273`, SUCCESS on the exact detached product SHA. `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest` passed: BUILD SUCCESSFUL in 1m 37s, 147 actionable tasks (113 executed, 34 from cache).
- [x] **Real APK build:** `:app:assembleDebug` passed in the same run: BUILD SUCCESSFUL in 1m 7s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] **Validated APK:** 23,532,916 bytes; SHA-256 `042a84748d1c64b170ef0bfce2e5459b876c726054b5876caf1188ae9719bab9`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] **Permissions regression:** APK permissions remain the existing functional set (`SYSTEM_ALERT_WINDOW`, `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, plus the app dynamic-receiver permission); Sleep Timer adds no new Android permission.
- [x] **Promotion:** run `33383098497`, job `99459611245`, fast-forward promoted exactly `4e48ced8f86262b4b5b5e25c5ea39acf38355191` to `main` after verifying the spec-before parent and validated product branch.
- GitHub prerelease publication of this final spec snapshot and temporary-branch cleanup follow this spec-after commit as distribution/repository-hygiene steps and do not alter the validated Sleep Timer runtime.

### Sleep Timer custom duration contract — approved 2026-08-31

- [x] **Custom duration — hours + minutes.** Extend the existing shared Sleep Timer v1 with one `Personalizzato…` entry while preserving the quick presets `Off / 15 / 30 / 45 / 60 min`.
- `Personalizzato…` opens a Material 3 dialog titled `Timer personalizzato` with two numeric fields: `Ore` and `Minuti`, numeric keyboard, a human-readable duration preview, and `Annulla` / `Imposta` actions.
- Input range is **1 minute through 12 hours inclusive** (`1..720` total minutes). `0 h 0 min` is invalid and must not silently map to `Off`; the `Imposta` action remains disabled. Hours are `0..12`, minutes are `0..59`, and when hours are `12`, minutes must be `0`.
- Reopening the dialog while a custom timer is active shows the **original custom duration that was set**, not the currently remaining duration. Confirming again replaces/restarts the timer from the newly entered duration.
- Preset and custom inputs must converge on the same internal deadline/countdown path in the existing process-shared `SleepTimerController`; custom duration is only another way to set that controller. Do not duplicate countdown state or expiry scheduling.
- The existing scheduler/clock remain injectable. No new ExoPlayer, MediaBrowser/controller, MediaSession, service, AlarmManager playback stack, or feature-owned playback source of truth is allowed.
- Custom timers must preserve all established v1 behavior: one shared countdown across UI surfaces, replacing an active timer rather than stacking, explicit `Off` cancellation, no queue/repeat/shuffle/radio-fallback mutation before expiry, and exactly one expiry callback through the existing shared Media3 playback authority.
- Countdown formatting becomes `H:MM:SS` for durations of one hour or more and remains `M:SS` below one hour.
- Deterministic tests are mandatory for: minimum `1 min`; maximum `12 h`; rejection of `0`; rejection of values above `12 h`; preset -> custom replacement; custom -> preset replacement; custom -> `Off`; and exactly one expiry callback. Tests must advance an injected fake clock/scheduler and must never wait real minutes/hours.
- Physical validation must include at least one real **1-minute custom expiry** plus replacement/cancellation checks before this objective is declared physically complete.

Known open follow-up — explicitly **out of scope for this custom-duration objective**:
- the Sleep Timer control/countdown presentation in the persistent mini-player does not yet meet the agreed UI contract and will be corrected in the next separate objective;
- the `60 min` preset exists in code but is horizontally off-screen in the current Now Playing chip row; discoverability/layout of all preset choices will be corrected together with the mini-player follow-up;
- do not opportunistically change those two UI issues in this custom-duration cycle.


Validation record — Sleep Timer custom duration implementation:
- Spec-before commit: `2469c893dc255f42f7b2d84e72a309c70a689905` (`docs: define custom Sleep Timer duration`).
- Clean product commit: `1c5c55e73c6e80d8a11f493dc0dbc1c3dabc5788` (`feat: add custom Sleep Timer duration`), with the spec-before commit as its direct parent and exactly five product/test files changed.
- Real GitHub Actions validation run `33394958379`, job `99497167248`, passed `SLEEP_TIMER_CUSTOM_SHARED_ARCHITECTURE=PASS`, deterministic `:core:playback`, `:feature:radio`, `:feature:library`, and `:app` unit/regression tests, plus `:app:assembleDebug`.
- Unit/regression build: `BUILD SUCCESSFUL in 2m 4s`, 147 actionable tasks (114 executed, 33 from cache). APK build: `BUILD SUCCESSFUL in 1m 44s`, 165 actionable tasks (46 executed, 119 up-to-date).
- Deterministic coverage includes 1 minute, 12 hours, rejection of 0 and values above 12 hours, preset -> custom, custom -> preset, custom -> Off, and exactly one expiry callback using the injected fake scheduler/clock path; no test waits real minutes/hours.
- Preset and custom durations converge on the same `SleepTimerController` deadline/scheduling path. Structural validation found no new ExoPlayer, MediaSession/MediaLibrarySession, service, AlarmManager playback stack, or parallel playback source of truth.
- Validated APK size: `23,549,300` bytes. APK SHA-256: `66267059cf1db9525ed9769a5082bcefb137887b425a7085a9a386dcba0d3180`. Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- Product commit was promoted fast-forward to `main` only after the complete validation run passed.
- Physical custom-duration validation is still pending and must include at least one real 1-minute custom expiry before the objective is physically closed.
- Known UI follow-up remains intentionally open and was not changed by this objective: the persistent mini-player timer presentation does not yet meet the agreed explicit-control/countdown UX, and the existing `60 min` quick preset is present in code but can sit off-screen in the horizontally scrollable Now Playing chip row. These two issues are the next separate objective after custom-duration physical validation.


### Sleep Timer expiry shutdown + custom accessibility hotfix contract

- [x] **Sleep Timer expiry shutdown.** When the shared Sleep Timer reaches zero, TamalutRadio must terminate playback through the existing shared Media3 architecture, not merely pause it. Reuse the existing `STOP_EXIT` session command / `TamalutPlaybackService` shutdown path so the player is stopped, media items are cleared, the media notification is removed and the service stops. No second player, MediaSession or service may be introduced.
- [x] **Task + overlay shutdown.** After the shared playback shutdown completes, close any floating overlay session and remove TamalutRadio's app task from Recents using Android task APIs. Do not call `Process.killProcess()` or `exitProcess()`. Reopening TamalutRadio later must be a normal new app launch. The overlay preference remains unchanged.
- [x] **No post-expiry data use.** A radio stream must not remain connected or consume network data after expiry. Playback fallback/reconnect logic must not restart after the timer shutdown.
- [x] **Custom action reachable from Now Playing.** The existing `Personalizzato…` action must be physically reachable in `In Riproduzione` on compact displays. Make the detail surface vertically scrollable without redesigning the mini-player. The separate mini-player Sleep Timer UX issue and 60-minute preset discoverability issue remain explicitly open for the next objective.
- [x] **Preserve validated timer semantics.** Presets and custom duration continue to use the same `SleepTimerController`, injected clock/scheduler, replacement/cancel behavior and deterministic tests. Before expiry, queue/repeat/shuffle/radio fallback remain unaffected.
- Physical validation context: custom-duration checks 1-13 passed functionally, including real 1-minute expiry, except the user reported that `Personalizzato…` was not visible/reachable in `In Riproduzione`; current expiry only stops audio at pause/stop-like state and must be upgraded to full functional shutdown as defined above.
- Verification requirement: real GitHub Actions must pass relevant playback/app tests, structural guards proving reuse of the shared STOP_EXIT path and absence of `killProcess`/`exitProcess`/new playback service, plus `:app:assembleDebug` with persistent debug signer v1 before promotion to `main`.


### Validation record — Sleep Timer expiry shutdown + custom accessibility hotfix

- [x] Spec-before commit: `31d650f995caa95836c9bc50931c264f581e4612` (`docs: define Sleep Timer expiry shutdown hotfix`).
- [x] Clean product commit: `d2ca1216dd4c7243d0dd56188e31a2dba4ff37e4` (`fix: fully shut down after Sleep Timer expiry`), direct child of spec-before; exactly five production/test files changed.
- [x] Expiry now delegates through the existing shared `Media3PlaybackController.stopAndExit()` to `PlaybackCommands.stopExitCommand`; `TamalutPlaybackService` remains the single player/session authority and executes its existing `player.stop() -> clearMediaItems() -> pauseAllPlayersAndStopSelf()` shutdown path. No second player, MediaSession or service was added.
- [x] After the shared playback shutdown callback, the existing floating overlay session is hidden/released, the existing playback controller is released/reset for a later fresh app launch, the same playback service is stopped defensively, and Android `ActivityManager.AppTask.finishAndRemoveTask()` removes TamalutRadio from Recents. No `Process.killProcess()` or `exitProcess()` is used.
- [x] `In Riproduzione` is vertically scrollable so the already-existing `Personalizzato…` action is physically reachable on compact displays. The mini-player timer redesign and 60-minute preset discoverability remain intentionally open and out of scope.
- [x] Structural gate `SLEEP_TIMER_SHUTDOWN_SHARED_ARCHITECTURE=PASS`.
- [x] Real GitHub Actions validation run `33398987833`, job `99510362421`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 2m 7s, 147 actionable tasks (113 executed, 34 from cache).
- [x] The same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 46s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] Validation APK: `23,549,300` bytes; SHA-256 `bfcc24d7e239aceb9bce011bd1e532d91a2541f040b1a46568c54f780278513e`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Exact validated product commit `d2ca1216dd4c7243d0dd56188e31a2dba4ff37e4` was fast-forward promoted to `main` after validation. Physical verification remains required for real 1-minute Radio expiry: audio/network teardown, media notification removal, overlay removal, Recents task removal, and clean relaunch.

### Physical closure — Sleep Timer expiry shutdown + custom accessibility (2026-08-31)

- [x] **Physical validation PASS.** Real-device verification confirmed the approved full functional shutdown behavior at Sleep Timer expiry.
- [x] At expiry, active audio terminates rather than remaining paused; the media notification and floating overlay disappear; TamalutRadio is removed from Recents; no playback reconnect/restart or continuing stream activity was observed.
- [x] Reopening TamalutRadio from the launcher after expiry behaves as a normal fresh app launch with no ghost playback/session.
- [x] The `Personalizzato…` action is reachable from `In Riproduzione` after the compact-display vertical-scroll fix.
- [x] The Sleep Timer expiry-shutdown/custom-accessibility hotfix cycle is therefore physically closed. The next separate objective is the approved `In Riproduzione` UI redesign plus mini-player cleanup; no code for that follow-up is included in this closure.

### In Riproduzione redesign + mini-player cleanup contract — approved 2026-08-31

- [x] **Approved visual direction.** Redesign the `In Riproduzione` destination using the user-approved Atlas Night mockup as the visual reference: clearer hierarchy, better spacing/alignment, compact rounded cards and restrained Sahara Pulse accents. The implementation should follow the design intent rather than copy mockup pixels rigidly.
- [x] **Now Playing information hierarchy.** The scrollable detail surface must organize content in this order: source/cover area; current item title/source/status; Sleep Timer card; source-specific Radio or local-music information. The page must remain usable on compact displays and must not clip lower actions.
- [x] **Sleep Timer card cleanup.** Keep the existing shared Sleep Timer engine unchanged. Render one organized card with current state/countdown plus `Off / 15 / 30 / 45 / 60 / Personalizzato…`; all six choices must be visibly reachable without relying on a hidden horizontal-scroll affordance. Active countdown remains visible in this detail card.
- [x] **Persistent mini-player remains the sole transport chrome.** Do not add a duplicate Previous/Play-Pause/Next row inside `In Riproduzione`; the persistent mini-player remains the global transport surface and continues to use the existing shared `PlaybackController`.
- [x] **Hide Sleep Timer from the mini-player.** Remove `Timer`, timer countdown text and timer menu/dropdown behavior from the persistent mini-player for both Radio and local music. The mini-player subtitle must contain only core playback/source information. Tapping the mini-player title/text area must navigate to `In Riproduzione` instead of opening Sleep Timer controls.
- [x] **Preserve local playback controls.** For local music, existing shuffle/repeat controls remain available in the mini-player and continue to use the same shared Media3 controller/state. Reorganize only presentation as necessary to keep the compact single-row chrome usable on narrow screens.
- [x] **Source-specific detail cleanup.** Radio detail should present concise LIVE/source information. Local music detail should present the current repeat/shuffle state without duplicating transport controls. No Room/schema/catalog change is part of this objective.
- [x] **Architecture boundaries.** Do not modify the Sleep Timer scheduler/deadline/expiry shutdown engine, queue creation, repeat/shuffle semantics, Radio fallback/reconnect logic, overlay lifecycle, Media3 service/session architecture, or notification behavior. No new ExoPlayer, MediaBrowser, MediaSession or service.
- [x] **Regression coverage.** Add tests/structural guards proving: no timer text/menu is rendered from mini-player production code; mini-player text tap routes to `In Riproduzione`; Now Playing remains vertically scrollable; all five quick-duration presets plus `Personalizzato…` are represented without horizontal-scroll dependency; no duplicate transport row is introduced; existing local shuffle/repeat delegation is preserved.
- [x] **Verification requirement.** Before promotion to `main`, a real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest` and `:app:assembleDebug` with persistent debug signer v1, produce and verify a real APK, then promote only the exact validated product commit. Afterward append a spec-after validation record, publish the final `main` snapshot as a GitHub prerelease asset (not an Actions artifact), and delete every temporary branch/workflow for this objective.


### Validation record — In Riproduzione redesign + mini-player cleanup

- [x] **Validated product commit:** `d0e2980e66a1d22874017e403385da2efe3a209d` (`refactor: redesign Now Playing playback chrome`), direct child of spec-before `db290e3b4fe9d6a1dbdf326b579ca506e18aa975`; exactly three product/test files changed.
- [x] **Mini-player cleanup:** Sleep Timer state/text/countdown/dropdown are removed from persistent mini-player production code for Radio and local music. The subtitle renders only shared playback/source information, and tapping the title/text area routes to `In Riproduzione`.
- [x] **Now Playing redesign:** the detail surface remains vertically scrollable with the approved hierarchy (source/cover, title/source/status, Timer card, source-specific details). Timer choices are split across visible rows and `Personalizzato…`; no hidden horizontal-scroll dependency remains for `60 min`.
- [x] **Transport/source preservation:** no duplicate Previous/Play-Pause/Next row was added to Now Playing; the persistent mini-player stays the sole in-app transport chrome. Local shuffle/repeat still delegate through the existing shared `PlaybackController` and Media3 state.
- [x] **Architecture gate:** `NOW_PLAYING_REDESIGN_SHARED_ARCHITECTURE=PASS`; no new ExoPlayer, MediaBrowser, MediaSession or service was introduced, and Sleep Timer scheduling/expiry shutdown, Radio fallback, overlay lifecycle, notification behavior and queue semantics were not modified.
- [x] **Real GitHub Actions validation:** run `33405413605`, job `99531671058`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 2m 2s, 147 actionable tasks (112 executed, 35 from cache).
- [x] **Real APK build:** the same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 53s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] **Validated APK:** 23,549,300 bytes; SHA-256 `77840e1ae14112b05bc4bf0fcca56742f11cfd20400d24c395c68bc2e5b910d6`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] **Promotion:** GitHub Actions run `33405922099` fast-forward promoted exactly `d0e2980e66a1d22874017e403385da2efe3a209d` to `main` after validating the direct spec-before parent.
- [ ] **Physical UI gate:** pending real-device validation of the redesigned `In Riproduzione`, timer-free mini-player, direct mini-player navigation, visible timer choices and playback regressions. Release publication and temporary-branch cleanup follow this spec-after snapshot before the APK is handed off for the physical gate.

### Sleep Timer — Settings-only access contract

- [x] **Move all Sleep Timer controls to Settings only.** Remove the complete Sleep Timer card from `In Riproduzione`: no `Off / 15 / 30 / 45 / 60 / Personalizzato…` controls and no remaining-time countdown may appear there. The persistent mini-player remains Timer-free exactly as validated in the preceding Now Playing redesign cycle.
- [x] **Settings becomes the only in-app control surface.** Add a dedicated `Timer spegnimento` card/section inside `Impostazioni` with the same quick presets `Off / 15 min / 30 min / 45 min / 60 min`, the same `Personalizzato…` entry point, and the same active remaining-time projection. Reuse the already validated custom hours/minutes dialog unchanged in behavior, including 1–720 minute validation and preview semantics.
- [x] **Do not change Sleep Timer behavior or ownership.** Keep the existing `SleepTimerController`, `HandlerSleepTimerScheduler`, elapsed-realtime clock, one-second state tick, replacement/cancel semantics, custom-duration model, and physically validated expiry/shutdown path unchanged. Do not add timer persistence, another scheduler, another clock, another timer controller, or a background timer service.
- [x] **Settings navigation badge while active.** When `SleepTimerState.isActive` is true, render one discreet Material 3 badge/indicator on the bottom-navigation `Impostazioni` icon from every destination. The badge is boolean only: it must not render the remaining duration. It disappears immediately when the timer is Off or after expiry and must derive from the existing shared `SleepTimerState`, with no duplicated boolean state.
- [x] **Remaining time in the existing Android media notification.** While media playback has an existing Media3 notification and the Sleep Timer is active, its subtitle/content text must show `Spegnimento tra <remaining>` using the existing Sleep Timer state (for example `Spegnimento tra 12:34`). When the timer is Off, the notification must return to its ordinary Media3 metadata text rather than retaining stale Timer text. The presentation may refresh from the controller's existing state tick, but must not create a second notification, notification channel, foreground service, `MediaSession`, player, timer, scheduler, or polling loop.
- [x] **Notification integration remains presentation-only.** Any bridge needed between the app-owned Sleep Timer state and `TamalutPlaybackService` must carry presentation state only and must not alter timer scheduling, deadline calculation, expiry semantics, radio fallback, queue/repeat/shuffle behavior, audio focus, notification transport controls, lock-screen controls, Android Auto media browsing, or the single `MediaLibraryService` / `MediaLibrarySession` architecture.
- [x] **Expiry remains authoritative.** At expiry the existing shutdown sequence remains the authority: playback stops and clears, overlay shuts down, playback service stops, notification disappears, and app tasks are removed as already physically validated. No stale badge or `Spegnimento tra 0:00` surface may survive the reset to Off.
- [x] **Regression coverage and validation.** Add explicit tests/structural gates proving: Timer UI is absent from `NowPlayingDestination` and `PersistentMiniPlayer`; Settings contains all presets and `Personalizzato…`; Settings badge is driven only by `SleepTimerState.isActive`; active notification text projects the same remaining-time formatter and Off restores ordinary metadata; no parallel player/session/service/timer architecture is introduced. A real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, verify the persistent debug signer, verify the resulting APK SHA-256, and only then promote the exact validated product commit to `main`.

Implementation note before code: the navigation badge is low-risk because `MainActivity` already observes the shared `SleepTimerState`. The media-notification addition has moderate presentation complexity because Media3 owns the existing notification; implement it by refreshing that same notification from existing timer state only, avoiding metadata mutation that would leak Timer text into unrelated media surfaces where practical. No change to Sleep Timer scheduling or shutdown logic is authorized by this objective.

Validation record — Sleep Timer Settings-only access:
- [x] Spec-before commit: `7cc390f4f5d4b83d4e1ed59438aefbb0ade2a767` (`docs: move Sleep Timer controls to Settings contract`).
- [x] Exact validated product commit: `33a0ddc3088fc77238c1e33a47d17b38beac880a` (`refactor: move Sleep Timer controls to Settings`), direct child of the spec-before commit and fast-forward promoted to `main`.
- [x] Structural gate `SLEEP_TIMER_SETTINGS_ONLY_ARCHITECTURE=PASS`: `SleepTimer.kt` is unchanged; Timer controls are absent from `In Riproduzione` and the persistent mini-player; Settings contains presets plus `Personalizzato…`; the bottom-navigation Settings badge derives from `SleepTimerState.isActive`; notification presentation reuses the existing Media3 session/player and adds no parallel notification/player/session/service/timer architecture.
- [x] Real GitHub Actions validation run `33479372047`, job `99765329460`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 2m 4s, 147 actionable tasks (111 executed, 36 from cache).
- [x] The same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 51s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] Validation APK: `23,565,684` bytes; SHA-256 `2a3c37924483808a47d42ca646d93a45d3598755b1c24cffe3d5e1d65fc3b2e8`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Automated cycle is closed through validation and promotion. The remaining gate is physical verification of Settings-only Timer controls, boolean Settings badge, media-notification countdown/restoration, and preservation of the already validated expiry/shutdown behavior.

Physical closure — Sleep Timer Settings-only access (2026-09-01):
- [x] **12/12 physical PASS.** Real-device verification confirmed Timer controls are absent from `In Riproduzione` and the persistent mini-player, present and fully usable in `Impostazioni`, and the custom hours/minutes dialog remains behaviorally unchanged.
- [x] The active-state indicator is visible on the `Impostazioni` bottom-navigation destination from every app destination and disappears immediately when the Timer returns to Off.
- [x] The existing Media3 notification shows the live Sleep Timer countdown while active and restores ordinary media metadata after cancellation.
- [x] Real 1-minute expiry regression passed without crash or duplicate audio and preserved the previously validated full shutdown path. The Settings-only functional cycle is physically closed.

### Sleep Timer active indicator — hourglass refinement contract

- [x] **Replace the generic dot with a semantic hourglass.** When the existing shared `SleepTimerState.isActive` is true, the bottom-navigation `Impostazioni` icon must show a small hourglass glyph rather than the current generic Material badge dot. The glyph must read as “timer active” at a glance, be slightly larger than a dot, and remain compact/proportional to the Settings navigation icon.
- [x] **Presentation-only change.** Preserve the exact existing boolean behavior: the hourglass appears for active preset or custom timers, is visible from every destination, and disappears immediately for Off/expiry. Do not add duplicated timer state, countdown text in the navigation bar, animation, persistence, scheduler changes, or another Timer controller.
- [x] **Keep all validated Sleep Timer surfaces and behavior unchanged.** Settings remains the only in-app Timer control surface; `In Riproduzione` and the mini-player remain Timer-free; the custom dialog, notification countdown/restoration, expiry/shutdown sequence, overlay shutdown, queue/repeat/shuffle/radio fallback and single Media3 player/session/service architecture are regression boundaries.
- [x] **Accessible and restrained Material 3 implementation.** Use an existing Material hourglass icon from the already-present Compose Material icons dependency, with an explicit timer-active accessibility description where appropriate. Do not add a new icon library or asset solely for this refinement.
- [x] **Regression coverage and validation.** Add a structural/unit test proving the active Settings indicator uses an hourglass icon, remains gated only by `SleepTimerState.isActive`, and no generic `Badge()` dot remains in that path. A real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, verify the persistent debug signer and APK SHA-256, and only then promote the exact validated product commit to `main`.

Validation record — Sleep Timer hourglass indicator:
- [x] Spec-before commit: `7e9db6554861b0e5a32ad2a602b734d27ae61581` (`docs: close timer physical gate and define hourglass indicator`), which also records the preceding Settings-only physical result as **12/12 PASS**.
- [x] Exact validated product commit: `f8d64aaeb12898b9a70536cd47f40278a373798d` (`ui: replace timer badge with hourglass indicator`), direct child of the spec-before commit and fast-forward promoted to `main`. Only `MainActivity.kt` and `SleepTimerSettingsOnlyArchitectureTest.kt` differ from spec-before.
- [x] Structural gate `SLEEP_TIMER_HOURGLASS_INDICATOR_STRUCTURE=PASS`: the Settings navigation indicator is still gated solely by `sleepTimerState.isActive`, now renders `Icons.Filled.HourglassBottom` at `14.dp` with `Timer attivo` accessibility text, and the old generic `Badge()` dot/import is absent. No Timer countdown or duplicate Timer state was added to the navigation bar.
- [x] Real GitHub Actions validation run `33482022640`, job `99773550294`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 1m 48s, 147 actionable tasks (113 executed, 34 from cache).
- [x] The same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 16s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] Validation APK: `23,565,684` bytes; SHA-256 `fbd866a3f7bc8dede452f8336a1141099a63f1ce5eab5020004fc02bce4e2fca`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Earlier temporary validation attempts are not product results: one stopped before build because the helper workflow lacked signer-access permission; another exposed and led to correction of an over-broad test assertion that matched `BadgedBox` as if it were `Badge`. The final product behavior was unchanged by that test-only correction and the complete final validation run is green.

Physical closure — Sleep Timer hourglass indicator (2026-09-01):
- [x] **5/5 physical PASS.** Real-device verification confirmed the hourglass appears whenever Sleep Timer is active, remains visible while changing app destinations, disappears immediately with `Off`, and disappears at real timer expiry.
- [x] The validated boolean semantics remain correct and continue to derive only from the shared `SleepTimerState.isActive`; no stale indicator survived cancellation or expiry.
- [x] Functional behavior is therefore physically closed. A separate presentation refinement is required because the current 14dp badge-overlay placement is too small, visually compressed/cropped against the Settings icon, and loses contrast on the selected green navigation state.

### Sleep Timer active indicator — adjacent hourglass placement refinement contract

- [x] **Move the hourglass out of the badge overlay.** The active Sleep Timer indicator must no longer be rendered through `BadgedBox` or overlap the Settings gear. Render `Icons.Filled.HourglassBottom` adjacent to the Settings navigation icon, preferably immediately to its right inside the `NavigationBarItem` icon slot, so both glyphs remain visually separate.
- [x] **Compact but readable geometry.** Use a 16dp hourglass with a small fixed gap (about 4dp) from the 24dp Settings icon. The combined icon group must remain comfortably inside one of the four equal bottom-navigation destinations and must not alter the bar height, label position, or spacing of the other destinations.
- [x] **Selected/unselected contrast follows Material navigation state.** Do not hard-code the hourglass to `MaterialTheme.colorScheme.primary`; let it inherit the `NavigationBarItem` content color (or an equivalent state-aware content color) so it remains legible on both the selected green indicator background and the unselected bar surface.
- [x] **Preserve exact Timer semantics and accessibility.** The adjacent hourglass remains gated solely by `item == MainDestination.SETTINGS && sleepTimerState.isActive`, keeps the explicit `Timer attivo` accessibility description, contains no countdown, and disappears immediately for `Off` or expiry. No Sleep Timer engine, scheduler, notification, shutdown, playback, overlay, queue, repeat/shuffle, Radio fallback, or Media3 architecture change is authorized.
- [x] **Regression coverage and validation.** Update the structural test to prove the Settings active indicator uses `HourglassBottom` at 16dp adjacent to the Settings icon, that no `BadgedBox`/badge overlay remains in this path, that the hourglass does not hard-code `MaterialTheme.colorScheme.primary`, and that the boolean gate remains only the shared `SleepTimerState.isActive`. A real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, verify persistent debug signer v1 and the APK SHA-256, then promote only the exact validated product commit to `main`. After promotion, append a spec-after validation record, publish the exact final `main` snapshot as a GitHub prerelease asset, and delete every temporary branch/workflow for this objective.

Validation record — adjacent Sleep Timer hourglass placement:
- [x] Spec-before commit: `cdb133ef3f528af8dfd83566150dc2d5f7b2ab13` (`docs: close hourglass physical gate and define adjacent indicator`), which records the preceding overlay-style hourglass physical result as **5/5 PASS** and defines this separate presentation-only follow-up.
- [x] Exact validated product commit: `c3e65479509611fcebf9713a8b11b6871bb95b00` (`ui: place timer hourglass beside Settings icon`), direct child of spec-before. Only `MainActivity.kt` and `SleepTimerSettingsOnlyArchitectureTest.kt` differ from spec-before.
- [x] Presentation result: active Settings renders the normal gear plus `Icons.Filled.HourglassBottom` in one centered `Row`, with `4.dp` spacing and a `16.dp` hourglass. `BadgedBox`/badge overlay is removed from this path. The hourglass has no hard-coded `MaterialTheme.colorScheme.primary` tint and therefore follows the `NavigationBarItem` state-aware content color for selected/unselected contrast. The gate remains only `item == MainDestination.SETTINGS && sleepTimerState.isActive` and accessibility remains `Timer attivo`.
- [x] Structural gate `SLEEP_TIMER_ADJACENT_HOURGLASS_STRUCTURE=PASS`. No Sleep Timer scheduling, notification, shutdown, playback, overlay, queue, repeat/shuffle, Radio fallback, or Media3 architecture was changed.
- [x] Real GitHub Actions validation run `33487445668`, job `99790720914`, passed `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`: BUILD SUCCESSFUL in 1m 57s, 147 actionable tasks (114 executed, 33 from cache).
- [x] The same run passed `:app:assembleDebug`: BUILD SUCCESSFUL in 1m 46s, 165 actionable tasks (46 executed, 119 up-to-date).
- [x] Validation APK: `23,565,684` bytes; SHA-256 `46d976b65de4f64f87f4df03ac29afc5a952fc4a8ae6e79d1dfc10cf8f6c8237`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Promotion run `33487948416`, job `99792350211`, fast-forward promoted exactly `c3e65479509611fcebf9713a8b11b6871bb95b00` to `main` only after the complete validation run passed.
- [x] **Physical UI gate for adjacent placement — PASS.** Real-device verification confirmed the 16dp adjacent hourglass is clearly readable with Settings both selected and unselected, has no clipping or compression, and leaves the four-destination bottom-bar layout unchanged. Timer on/off/expiry semantics remain covered by the preceding 5/5 physical gate.
- GitHub prerelease publication of this final spec snapshot and temporary-branch cleanup follow this spec-after commit as distribution/repository-hygiene steps and do not alter the validated runtime.


### Scrollable Radio navigation contract

- [x] **Context-derived Radio queue.** Selecting a station must create a real Media3 Radio queue from the exact visible list/context that produced the tap, preserving that list's deterministic visible order. A tap from `Tutte` uses the complete flat station list; a tap while `Marocco`, `Italia`, or `Sport` is selected uses only that filtered list; a tap from `Preferiti` uses only the stations visible in Favorites at that moment. The selected station must become the current queue item at its exact index.
- [x] **Stable queue snapshot until the next explicit Radio selection.** The active Radio queue is a snapshot captured at station selection time. Merely switching Radio tab/filter while playback continues must not mutate or replace that queue. Later favorite additions/removals or repository refreshes likewise affect future station selections, not the already playing queue. Consequently, if a station was started from `Preferiti` and is then removed from favorites while playing, it continues to play and remains part of that active snapshot until the user explicitly selects another Radio station; the Favorites UI updates immediately, but playback does not jump or silently rebuild.
- [x] **Wrap-around Previous/Next.** For Radio snapshots containing at least two stations, Previous/Next must be meaningful at every position: Next from the last station wraps to the first, and Previous from the first wraps to the last. A one-station Radio snapshot exposes no meaningful skip capability and Previous/Next remain disabled/no-op. Wrap-around must work through the shared Media3 session so in-app mini-player, floating overlay, compatible notification/system/Auto surfaces and media controllers observe the same queue/capabilities instead of implementing separate Radio navigation.
- [x] **Radio loop is transport plumbing, not a user repeat mode.** Media3 may use playlist repeat-all internally to obtain boundary wrap-around, but shared/UI Radio state must continue to expose repeat as OFF and shuffle as disabled/hidden. Local-music repeat/shuffle semantics remain unchanged.
- [x] **Fallback remains per station and must preserve the queue.** Each Radio queue item owns its existing primary/fallback endpoint plan. Previous/Next moves between stations only and never advances fallback endpoints. Fatal stream errors may retry the current station's fallback URL exactly as today, but retrying must replace/reprepare only the current queue item and must never collapse, clear, or reorder the surrounding Radio queue. Selecting a station anew starts a fresh queue and fresh primary-first fallback plans.
- [x] **Live reconnect, Sleep Timer metadata and stop/exit preserve queue semantics.** Radio live-edge reconnects and Sleep Timer notification metadata replacement must not destroy or reorder the Radio playlist. `Stop/Esci`, switching to local music, or an explicit new Radio selection may replace/clear the active queue as already appropriate. Automatic fallback exhaustion must not auto-skip to another station unless the user explicitly invokes Next/Previous.
- [x] **Shared state and regressions.** `PlaybackState.stationId`, title, playing marker and `canSkipPrevious`/`canSkipNext` must follow the actual current Radio queue item after every skip/wrap/fallback transition. Existing radio favorites UI, filters, LIVE presentation, fallback budget/order, notification launch, overlay behavior, Sleep Timer, local SAF queues, local repeat/shuffle and single `TamalutPlaybackService` / `MediaLibrarySession` architecture must remain unchanged.
- [x] **Required tests/validation.** Add explicit coverage for queue derivation from `Tutte`, each category filter and `Preferiti`; selected start index/order; queue snapshot stability after filter/tab/favorite changes; one-item disabled skip; first/last wrap-around; shared capability projection; fallback retry preserving queue/current index; live reconnect preserving queue; and local playback regressions. A real GitHub Actions run must pass `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, verify persistent debug signer v1 and APK SHA-256, then promote only the exact validated product commit to `main`. After promotion, append spec-after evidence, publish the exact final `main` snapshot as a GitHub prerelease APK, and delete every temporary branch/workflow for this objective.

### Validation record — Scrollable Radio navigation

- [x] Spec-before commit: `3ed07e122b9eb9f8db86e0dbb2ab917d518623d8` (`docs: define scrollable radio navigation`).
- [x] Exact validated product commit: `6848d13cee5acbdba1d91230e4a0cb42bd7a8a15` (`feat: add context-aware radio station navigation`), direct child of spec-before. Exactly eight production/test files differ from spec-before: Radio queue policy/controller/service preservation plus Radio gateway/ViewModel and focused tests.
- [x] Context snapshot semantics are implemented at the Radio selection boundary: the ViewModel captures the exact current `visibleStations.toList()` and selected index. `Tutte`, filtered `Marocco / Italia / Sport`, and `Preferiti` therefore create distinct ordered snapshots; later tab/filter/favorite/repository changes do not silently rebuild the active queue. A new explicit Radio selection replaces the snapshot.
- [x] Radio queues with at least two stations use internal Media3 playlist repeat-all for boundary wrap while shared/UI Radio repeat remains exposed as OFF and shuffle remains disabled/hidden. One-item Radio queues expose no meaningful Previous/Next capability.
- [x] Existing per-station primary/fallback behavior is preserved without collapsing the queue: fallback retries replace only the current MediaItem/current index. Radio live-edge reconnect likewise preserves the surrounding playlist/current item instead of rebuilding it. Previous/Next remains station navigation, never fallback navigation.
- [x] Initial validation run `33491223126`, job `99802926383`, is explicitly **not** a product approval. It passed product-isolation/architecture/signing setup but stopped at `:core:playback:compileDebugKotlin` because the first generated snapshot `b73f868d0d56b1054d20774cb4460da6384adf0e` contained an escaped-quote syntax defect in the default `playRadioQueue` error string. `assembleDebug` and APK checksum/signature verification were skipped. The defective snapshot was not promoted.
- [x] The product was reconstructed cleanly from the same spec-before parent as `6848d13cee5acbdba1d91230e4a0cb42bd7a8a15`, rather than stacking a repair commit on the defective snapshot. Corrected GitHub Actions validation run `33492867459`, job `99808199546`, passed the dedicated `:core:playback:compileDebugKotlin` preflight (`BUILD SUCCESSFUL in 1m 21s`), then `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 1m 7s`; 147 actionable tasks: 99 executed, 40 from cache, 8 up-to-date).
- [x] The same corrected run passed `:app:assembleDebug` (`BUILD SUCCESSFUL in 1m 49s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: `23,565,684` bytes; SHA-256 `92a1970083a52853cb8fe1c6e2f981dd6131956612f0b82b80f17ac69eea0b3d`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Exact corrected product commit `6848d13cee5acbdba1d91230e4a0cb42bd7a8a15` was fast-forward promoted to `main` only after the complete corrected validation run passed.
- [ ] **Physical Radio navigation gate:** pending real-device verification of context-derived queues, first/last wrap-around, single-item disabled skip, stable snapshot across filter/tab/favorite changes, fallback/live-reconnect queue preservation, shared transport surfaces, and Radio/LOCAL/Sleep-Timer regressions. GitHub prerelease publication and temporary-branch cleanup follow this spec-after snapshot before physical testing.

## Verified radio catalog expansion — contract

This objective expands the built-in Radio catalog only with stations whose identity and live endpoint have been independently verified. Radio Browser discovery is accepted only when the `homepage` field matches the broadcaster's official domain; ambiguous or missing Radio Browser entries require an official-site correlation. Every admitted endpoint must have returned final HTTP 200 and exposed a decodable audio stream in GitHub Actions. A requested station that cannot satisfy both identity and stream-health gates is explicitly excluded rather than seeded with a doubtful URL.

### Approved catalog additions

| Queue order | Stable ID | Display name | Category | Identity/provenance | Verified primary stream |
|---:|---|---|---|---|---|
| 1 | `medi1-radio` | Medi1 Radio | Marocco | Radio Browser homepage `https://www.medi1.com/` | `https://cdn.live.easybroadcast.io/live/83_medi1radio-maghreb_8s9i4bn/playlist.m3u8` |
| 2 | `hit-radio-maroc` | HIT RADIO Maroc | Marocco | existing approved seed | existing approved stream |
| 3 | `chada-fm` | Chada FM | Marocco | Radio Browser homepage `https://chada.ma/fr/` | `https://stream.bodkas.com/playlist?id=chadafmradio` |
| 4 | `atlantic-radio` | Atlantic Radio | Marocco | Radio Browser homepage `https://atlanticradio.ma/` | `https://atlantic-sonic.nindohost.net:9300/stream` |
| 5 | `cap-radio` | Cap Radio | Marocco | official `https://capradiotv.com/` embeds RadioKing radio 710810 | `https://listen.radioking.com/radio/710810/stream/776366` |
| 6 | `radio-mars` | Radio Mars | Marocco | existing approved seed | existing approved stream |
| 7 | `radio-plus-agadir` | Radio Plus Agadir 92.4 | Marocco | existing approved seed; remains distinct from unverified Casablanca request | existing approved stream |
| 8 | `radio-azawan` | Radio Azawan | Marocco | existing approved seed | existing approved stream |
| 9 | `aswat-fm` | Aswat FM | Marocco | existing approved seed | existing approved stream |
| 10 | `mfm-radio` | MFM Radio | Marocco | existing approved seed | existing approved stream |
| 11 | `medina-fm-amazigh` | Medina FM Amazigh | Marocco | existing approved seed | existing approved stream |
| 12 | `rtl-102-5` | RTL 102.5 | Italia | Radio Browser homepage `https://www.rtl.it/` | `https://dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S97044836/tbbP8T1ZRPBL/playlist_audio.m3u8` |
| 13 | `radio-deejay` | Radio Deejay | Italia | Radio Browser homepage `https://www.deejay.it/` | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiodeejay/radiodeejay/master_ma.m3u8` |
| 14 | `radio-105` | Radio 105 | Italia | Radio Browser homepage `https://105.net/` | `https://icecast.unitedradio.it/Radio105.mp3` |
| 15 | `rds-100-grandi-successi` | RDS 100% Grandi Successi | Italia | Radio Browser homepage `https://www.rds.it/` | `https://stream.rds.radio/audio/rds.stream_aac64/chunklist.m3u8` |
| 16 | `radio-italia-smi` | Radio Italia Solo Musica Italiana | Italia | existing approved seed | existing approved stream |
| 17 | `virgin-radio-italia` | Virgin Radio Italia | Italia | Radio Browser homepage `http://www.virginradio.it/`; HTTPS endpoint independently verified | `https://icecast.unitedradio.it/Virgin.mp3` |
| 18 | `radio-capital` | Radio Capital | Italia | Radio Browser homepage `https://www.capital.it/` | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiocapital/radiocapital/master_ma.m3u8` |
| 19 | `m2o` | m2o | Italia | Radio Browser homepage `https://www.m2o.it/` | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiom2o/radiom2o/master_ma.m3u8` |
| 20 | `radio-monte-carlo` | Radio Monte Carlo (RMC) | Italia | Radio Browser homepage `https://www.radiomontecarlo.net/radio-onair/` | `https://icy.unitedradio.it/RMC.aac` |
| 21 | `r101` | R101 | Italia | Radio Browser homepage `http://www.r101.it/`; HTTPS endpoint independently verified | `https://icecast.unitedradio.it/r101_mp3` |
| 22 | `rai-radio-1` | Rai Radio 1 | Italia | Radio Browser homepage `https://www.raiplaysound.it/radio1` | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S16355530/Q4zh3NTu28Rx/icecast` |
| 23 | `rai-radio-2` | Rai Radio 2 | Italia | Radio Browser homepage `https://www.raiplaysound.it/radio2` | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S35942484/yp5F67151K92/icecast` |
| 24 | `rai-radio-3` | Rai Radio 3 | Italia | Radio Browser homepage `https://www.raiplaysound.it/radio3` | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S56630579/yEbkcBtIoSwd/icecast` |
| 25 | `bbc-radio-1` | BBC Radio 1 | UK | Radio Browser identity correlated to BBC official service | `https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/ak/bbc_radio_one.m3u8` |
| 26 | `bbc-radio-2` | BBC Radio 2 | UK | Radio Browser identity correlated to BBC official service | `https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cf/bbc_radio_two.m3u8` |
| 27 | `bbc-radio-4` | BBC Radio 4 | UK | Radio Browser identity correlated to BBC official service | `https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8` |
| 28 | `capital-fm-london` | Capital FM London | UK | Radio Browser homepage `https://capitalfm.com/` | `https://media-ssl.musicradio.com/CapitalMP3` |
| 29 | `heart-uk` | Heart UK | UK | Radio Browser homepage `https://www.heart.co.uk/` | `https://media-ssl.musicradio.com/HeartUK` |
| 30 | `classic-fm` | Classic FM | UK | Radio Browser homepage `https://www.classicfm.com/` | `https://media-ssl.musicradio.com/ClassicFMMP3` |
| 31 | `radio-sportiva` | Radio Sportiva | Sport | existing approved seed | existing approved stream |

The built-in catalog contains exactly **31** stations after this objective: 11 `Marocco`, 13 `Italia`, 6 `UK`, and 1 `Sport`. `Tutte` uses exactly the built-in order above. Filtered category queues preserve the relative order shown above. The new `UK` filter is added alongside `Tutte / Marocco / Italia / Sport` so English stations form a real queue rather than appearing only as uncategorized entries.

### Explicitly rejected requested stations

- [ ] **Radio 2M — not seeded.** Official `https://bo.radio2m.ma/api/live` returned the current `link_ecouter`, proving identity, but the resulting official Globecast HLS endpoint returned HTTP 403 and no decodable audio in final probe run `33498432282`; it fails the reachability gate.
- [ ] **Radio Kiss Kiss — not seeded.** Radio Browser identity matches official `kisskiss.it`, but the available Fluidstream endpoints are cleartext-era endpoints; HTTPS playlist/direct probes failed TLS/connection setup in final probe run `33498432282`. No working HTTPS endpoint with equally strong provenance was found.
- [ ] **Radio 24 — not seeded.** Radio Browser and the official site correlate to the legacy Shoutcast endpoint on port 8000, but HTTPS probes fail TLS. The app does not opt into cleartext traffic, and this objective does not weaken Android network security merely to admit one station.
- [ ] **SNRT Radio Chaîne Inter — not seeded.** The official SNRT page itself exposes its Globecast HLS URL, but that URL returned HTTP 400 and no decodable audio in both the broad and final probes, including a browser-like User-Agent/Referer attempt.
- [ ] **Radio Plus Casablanca — not seeded.** No Radio Browser record with official `radioplus.ma` provenance could be tied specifically to Casablanca. The official site exposes no distinct Casablanca live endpoint, while a candidate `radio.co` stream is contradicted by independent identity evidence pointing to Radio Plus Mauritius. Existing `Radio Plus Agadir 92.4` remains unchanged.

### Ordering, persistence and regression contract

- [ ] **Catalog order is playback order.** `InitialRadioCatalog.stations` becomes the authoritative built-in order for `Tutte` and category-derived Radio queues. Repository reads must project Room rows back into this order even though the DAO currently returns rows alphabetically. Custom stations remain after all built-ins in deterministic `name` then `id` order. `RadioFeatureController` must preserve source order and must not alphabetically re-sort the list.
- [ ] **Seed stays additive and idempotent.** Seeding inserts only missing stable IDs and never overwrites an already stored built-in/custom row. No Room schema migration is required. Running seeding repeatedly must result in exactly one row per built-in ID, no duplicate primary URL, and the same 31-station catalog.
- [ ] **Category model.** `RadioStationGrouping` adds `UK("UK")` and assigns exactly the six approved British station IDs to it. Existing presentation semantics remain: Radio Mars stays `Marocco`; Radio Sportiva stays `Sport`; unknown/custom IDs remain visible in `Tutte` and are not silently assigned to a built-in category.
- [ ] **Existing playback architecture is unchanged.** The context-snapshot Radio queue, wrap-around Previous/Next, per-station fallback logic, live reconnect behavior, favorites snapshot behavior, Sleep Timer, notification/overlay/Android Auto projections and local Music semantics remain unchanged apart from seeing the expanded ordered station lists.
- [ ] **Required tests.** Add/expand tests for: exact 31 built-in IDs and order; unique IDs and primary URLs; seed twice/multiple times remains exactly 31 with no duplicate insertions; pre-existing rows are not overwritten; repository built-in-order projection plus deterministic custom tail; `Tutte`/Marocco/Italia/UK/Sport membership and relative order; and existing scrollable Radio queue tests remaining green.
- [ ] **Required validation.** A real GitHub Actions run must pass `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify persistent debug signer v1 and APK SHA-256; promote only the exact validated product commit to `main`; append spec-after evidence; publish the exact final `main` snapshot through the permanent debug-prerelease workflow; then delete all temporary research/spec/product/validation branches and helper workflows.

### Research evidence before implementation

- Radio Browser discovery run `33497315070`, job `99822372091`: verified candidates by `homepage` and exposed duplicate/mislabel hazards such as non-Italian stations returned ahead of the correct Radio Deejay entry.
- Corrected full stream probe run `33498209411`, job `99825185495`: all 22 approved additions returned final HTTP 200 and decodable audio; Radio 24 HTTPS and the then-current SNRT endpoint failed.
- Official endpoint resolver run `33498209214`, job `99825183945`: resolved Radio 2M via `bo.radio2m.ma/api/live`; confirmed no usable official endpoint was exposed for Radio Plus Casablanca or Radio 24.
- Final ambiguous probe run `33498432282`, job `99825898961`: Radio 2M failed with HTTP 403; Radio Kiss Kiss HTTPS candidates failed connection/TLS; SNRT Chaîne Inter remained HTTP 400. Those failures are deliberate exclusion evidence, not accepted product endpoints.


### Catalog expansion amendment — radio.co.ma discovery and Atbir correction

This amendment supersedes the earlier provisional 31-built-in count in this objective before any product commit. The final built-in catalog target is **39 stations**: **19 Marocco + 13 Italia + 6 UK + 1 Sport**. `Tutte` remains the complete flat view; the four category filters remain `Marocco / Italia / Sport / UK`.

- [ ] **Correct the existing mislabeled seed without changing its stream identity.** Stable ID `radio-plus-agadir` currently displays `Radio Plus Agadir 92.4` but its existing Zeno endpoint `https://stream-158.zeno.fm/bqdbb6hd0neuv` is the stream identified during this research as **Radio Atbir**. In this objective the built-in row keeps the same stable ID and exact stream URL but changes display name to **Radio Atbir**. This is an additive catalog correction only; it does not authorize overwriting an already persisted user-modified row during idempotent seeding.
- [ ] **Do not add a separate Radio Plus Agadir without a distinct reliable endpoint.** `radioplus.ma` is the correct broadcaster identity, but the HTTPS directory candidate resolves to the same Zeno stream now attributed to Atbir. A separate candidate found at `hosting.radiomedia.fr:2840/live` is cleartext HTTP and lacks sufficient official endpoint correlation. Therefore the real Radio Plus Agadir remains excluded from the built-in catalog for now.
- [ ] **radio.co.ma is discovery/cross-check, not automatic stream authority.** Its Morocco directory was used to expand the candidate set, but admission still requires broadcaster/homepage correlation plus a successful stream probe. `radio-italiane.it` is used only as an Italy discovery/name cross-check; it does not broaden the approved Italian set in this objective.
- [ ] **Additional Morocco admissions.** Final probe run `33502285129`, job `99838159397`, verified final HTTP 200 plus decodable audio for eight additional Moroccan stations: Med Radio, Ness Radio, Radio Manarat, Radio Achkid FM, Radio Star Maroc FM, Radio Tanger Med, Radio Yabiladi, and Radio Medina FM. The same gate rejected Al Amazighia because its official SNRT HLS candidate returned HTTP 400 and could not be decoded.
- [ ] **Previously confirmed exclusions remain exclusions.** Radio 2M, Radio Kiss Kiss, Radio 24, SNRT Radio Chaîne Inter and Radio Plus Casablanca remain out for the previously recorded reasons. The newly investigated real Radio Plus Agadir and Al Amazighia are also excluded until a distinct reliable HTTPS endpoint passes the same identity/audio gate.

#### Final authoritative built-in queue order

`InitialRadioCatalog.stations` must use exactly this order, and repository/UI projection must preserve it for `Tutte`; category queues are stable subsequences of this order. Custom stations remain after all 39 built-ins in deterministic `name.lowercase()` then stable `id` order.

| # | Stable ID | Display name | Category | Primary stream |
|---:|---|---|---|---|
| 1 | `medi1-radio` | Medi1 Radio | Marocco | `https://cdn.live.easybroadcast.io/live/83_medi1radio-maghreb_8s9i4bn/playlist.m3u8` |
| 2 | `hit-radio-maroc` | HIT RADIO Maroc | Marocco | `https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3` |
| 3 | `chada-fm` | Chada FM | Marocco | `https://stream.bodkas.com/playlist?id=chadafmradio` |
| 4 | `atlantic-radio` | Atlantic Radio | Marocco | `https://atlantic-sonic.nindohost.net:9300/stream` |
| 5 | `cap-radio` | Cap Radio | Marocco | `https://listen.radioking.com/radio/710810/stream/776366` |
| 6 | `med-radio` | Med Radio | Marocco | `https://medradio.ice.infomaniak.ch/medradio-128.mp3` |
| 7 | `radio-mars` | Radio Mars | Marocco | `https://radiomars.ice.infomaniak.ch/radiomars-128.mp3` |
| 8 | `radio-plus-agadir` | Radio Atbir | Marocco | `https://stream-158.zeno.fm/bqdbb6hd0neuv` |
| 9 | `radio-azawan` | Radio Azawan | Marocco | `https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3` |
| 10 | `aswat-fm` | Aswat FM | Marocco | `https://broadcast.ice.infomaniak.ch/aswat-high.mp3` |
| 11 | `mfm-radio` | MFM Radio | Marocco | `https://a5.asurahosting.com:7980/radio.mp3` |
| 12 | `radio-medina-fm` | Radio Medina FM | Marocco | `https://medinafm.ice.infomaniak.ch/medinafm-128.mp3` |
| 13 | `medina-fm-amazigh` | Medina FM Amazigh | Marocco | `https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3` |
| 14 | `ness-radio` | Ness Radio | Marocco | `https://radio.nessradio.net:8212/nessradio-hd` |
| 15 | `radio-manarat` | Radio Manarat | Marocco | `https://listen.radioking.com/radio/252934/stream/297385` |
| 16 | `radio-tanger-med` | Radio Tanger Med | Marocco | `https://radiotangermed-22.ice.infomaniak.ch/radiotangermed-22-128.mp3` |
| 17 | `radio-yabiladi` | Radio Yabiladi | Marocco | `https://radio.yabiladi.com:8002/;stream.mp3` |
| 18 | `radio-achkid-fm` | Radio Achkid FM | Marocco | `https://stream.zeno.fm/7nqu31p6xg0uv` |
| 19 | `radio-star-maroc-fm` | Radio Star Maroc FM | Marocco | `https://a2.asurahosting.com:6100/radio.mp3` |
| 20 | `rtl-102-5` | RTL 102.5 | Italia | `https://dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S97044836/tbbP8T1ZRPBL/playlist_audio.m3u8` |
| 21 | `radio-deejay` | Radio Deejay | Italia | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiodeejay/radiodeejay/master_ma.m3u8` |
| 22 | `radio-105` | Radio 105 | Italia | `https://icecast.unitedradio.it/Radio105.mp3` |
| 23 | `rds-100-grandi-successi` | RDS 100% Grandi Successi | Italia | `https://stream.rds.radio/audio/rds.stream_aac64/chunklist.m3u8` |
| 24 | `radio-italia-smi` | Radio Italia Solo Musica Italiana | Italia | `https://radioitaliasmi.akamaized.net/hls/live/2093120/RISMI/stream01/streamPlaylist.m3u8` |
| 25 | `virgin-radio-italia` | Virgin Radio Italia | Italia | `https://icecast.unitedradio.it/Virgin.mp3` |
| 26 | `radio-capital` | Radio Capital | Italia | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiocapital/radiocapital/master_ma.m3u8` |
| 27 | `m2o` | m2o | Italia | `https://4c4b867c89244861ac216426883d1ad0.msvdn.net/radiom2o/radiom2o/master_ma.m3u8` |
| 28 | `radio-monte-carlo` | Radio Monte Carlo (RMC) | Italia | `https://icy.unitedradio.it/RMC.aac` |
| 29 | `r101` | R101 | Italia | `https://icecast.unitedradio.it/r101_mp3` |
| 30 | `rai-radio-1` | Rai Radio 1 | Italia | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S16355530/Q4zh3NTu28Rx/icecast` |
| 31 | `rai-radio-2` | Rai Radio 2 | Italia | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S35942484/yp5F67151K92/icecast` |
| 32 | `rai-radio-3` | Rai Radio 3 | Italia | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S56630579/yEbkcBtIoSwd/icecast` |
| 33 | `bbc-radio-1` | BBC Radio 1 | UK | `https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/ak/bbc_radio_one.m3u8` |
| 34 | `bbc-radio-2` | BBC Radio 2 | UK | `https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/audio/simulcast/hls/nonuk/pc_hd_abr_v2/cf/bbc_radio_two.m3u8` |
| 35 | `bbc-radio-4` | BBC Radio 4 | UK | `https://as-hls-ww-live.akamaized.net/pool_55057080/live/ww/bbc_radio_fourfm/bbc_radio_fourfm.isml/bbc_radio_fourfm-audio%3d128000.norewind.m3u8` |
| 36 | `capital-fm-london` | Capital FM London | UK | `https://media-ssl.musicradio.com/CapitalMP3` |
| 37 | `heart-uk` | Heart UK | UK | `https://media-ssl.musicradio.com/HeartUK` |
| 38 | `classic-fm` | Classic FM | UK | `https://media-ssl.musicradio.com/ClassicFMMP3` |
| 39 | `radio-sportiva` | Radio Sportiva | Sport | `https://sportiva.inmystream.it/stream/sportiva` |

#### Superseding test/validation contract

- [ ] Replace every provisional `31` expectation for this objective with **39 built-ins** and assert category counts/order: **Marocco 19, Italia 13, UK 6, Sport 1**.
- [ ] Idempotence must seed repeatedly to exactly 39 unique built-in IDs with no duplicate primary URL and no duplicate rows; pre-existing stored rows, including `radio-plus-agadir`, must not be overwritten solely by seeding.
- [ ] Repository ordering tests must prove all built-ins follow `InitialRadioCatalog.stations`, then custom stations follow in deterministic `name.lowercase()` + `id` order. `RadioFeatureController` must not re-sort the built-in sequence alphabetically.
- [ ] Feature tests must prove `Tutte` returns the 39 built-ins in exact catalog order (plus deterministic custom tail when present), and each of the four category filters is the exact relative subsequence of that order. UK Previous/Next queue derivation must therefore use `BBC Radio 1 → BBC Radio 2 → BBC Radio 4 → Capital FM London → Heart UK → Classic FM` with wrap-around supplied by the already validated Radio queue architecture.
- [ ] Existing fallback, live reconnect, favorites snapshot, Sleep Timer, overlay/system transport, local Music, signer and APK regression boundaries remain unchanged.
- [ ] Real GitHub Actions validation remains: `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug`, persistent signer verification and APK SHA-256 before exact product promotion.


### Catalog safety clarification — Medina distinct feeds and Atbir legacy-label repair

This clarification is still spec-before and precedes every product-code change for the catalog objective.

- [ ] **Radio Medina FM and Medina FM Amazigh are confirmed distinct feeds.** Safety run `33524208667`, job `99910765420`, probed `https://medinafm.ice.infomaniak.ch/medinafm-128.mp3` and `https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3` concurrently. Both returned HTTP 200, `audio/mpeg`, MP3 audio at 128 kbps. Concurrent decoded PCM samples produced different SHA-256 values (`11ba2467b334e9b23e17f95b599ca9d273d5633ec8e91cf9346a34bd41f63e13` vs `fd57e2e8538db2db802a19716f6fb4c0e86720f37ec5da2698559fedb5603537`); Chromaprint comparison reported sequence ratio `0.0000` and longest identical block `0`. The official Medina FM site also presents the general station and Medina Amazigh as separate channels in the same network. Both entries therefore remain in the 39-station catalog.
- [ ] **Targeted legacy Atbir label repair is the sole exception to generic non-overwrite seeding.** Existing installations that contain the exact historical built-in row with stable ID `radio-plus-agadir`, display name `Radio Plus Agadir 92.4`, primary stream `https://stream-158.zeno.fm/bqdbb6hd0neuv`, and `isCustom=false` must be repaired in place to display name `Radio Atbir`, preserving the same stable ID, primary stream and fallback data. This repair must not alter any row whose name, stream, custom-state or other identifying legacy conditions differ; user-modified/custom rows remain protected.
- [ ] **Idempotence includes the repair.** Running catalog initialization repeatedly after the repair must keep exactly 39 built-ins, perform the legacy label change at most once, introduce no duplicate IDs/primary URLs, and preserve deterministic built-in plus custom ordering.

### Validation record — verified radio catalog expansion

- [x] Final spec-before chain: `38ec6a524ea99647d0143aea4dd00785b3239566` defined the initial verified expansion, `1f93d7e7de676441b6b0b548cc72bba604d3d72b` superseded the provisional 31-station count with the authoritative 39-station order after the `radio.co.ma` review, and `6800c82c6ff0ecb0859a0ed58232ccd3575cf44a` clarified the Medina distinct-feed proof plus the exact legacy Atbir label-repair exception before any product code was committed.
- [x] Medina duplicate-safety gate: GitHub Actions run `33524208667`, job `99910765420`, verified `Radio Medina FM` (`https://medinafm.ice.infomaniak.ch/medinafm-128.mp3`) and `Medina FM Amazigh` (`https://medinaamazigh.ice.infomaniak.ch/medinaamazigh-128.mp3`) simultaneously. Both returned HTTP 200, `audio/mpeg`, MP3 audio at 128 kbps. Concurrent decoded samples had different SHA-256 values (`11ba2467b334e9b23e17f95b599ca9d273d5633ec8e91cf9346a34bd41f63e13` vs `fd57e2e8538db2db802a19716f6fb4c0e86720f37ec5da2698559fedb5603537`); Chromaprint sequence ratio was `0.0000` with longest identical block `0`. They are retained as distinct stations.
- [x] Exact product commit: `13008127492e82a0abd2ff530c6e42478136181c` (`feat: expand verified radio catalog`), direct child of final spec-before `6800c82c6ff0ecb0859a0ed58232ccd3575cf44a`. Exactly nine production/test files differ from the spec parent; no workflow/helper files are part of the product commit.
- [x] Built-in catalog is exactly 39 unique stations in authoritative `InitialRadioCatalog.stations` order: 19 `Marocco`, 13 `Italia`, 6 `UK`, 1 `Sport`. Stable IDs and primary stream URLs are unique. `UK` is a first-class presentation filter with queue order `BBC Radio 1 -> BBC Radio 2 -> BBC Radio 4 -> Capital FM London -> Heart UK -> Classic FM`.
- [x] Repository reads now project known built-ins back into exact `InitialRadioCatalog.stations` order even though Room returns rows alphabetically. Custom stations remain after all built-ins in deterministic `name.lowercase()` then stable-ID order. `RadioFeatureController` preserves repository order instead of introducing an alphabetical re-sort, so `Tutte` and category snapshots use the catalog contract as Previous/Next order.
- [x] Existing historical `radio-plus-agadir` seed is corrected in place to display `Radio Atbir` while retaining its stable ID and exact Zeno stream. The repair is intentionally narrow: it runs only for the exact non-custom legacy row with old display name `Radio Plus Agadir 92.4` and the known Zeno primary stream. Modified/custom rows are not overwritten; fallbacks are preserved; repeated seeding does not repeat the repair or create duplicates. A separate true Radio Plus Agadir is not seeded because no distinct reliable HTTPS endpoint was verified.
- [x] Idempotence and ordering tests cover: exactly 39 built-ins after repeated seeding; unique built-in IDs and primary URLs; non-overwrite of pre-existing modified rows; exact Atbir legacy repair plus fallback preservation; built-in order projection; deterministic custom tail; exact category membership/count/order; all-filter preservation; and UK context queue derivation/start index/capabilities. Existing scrollable Radio navigation, playback, local-library and app regressions remain green.
- [x] Real GitHub Actions validation run `33525636711`, job `99915623423`, passed product isolation (`CATALOG_PRODUCT_ISOLATION=PASS`) and structural catalog gate (`CATALOG_EXPANSION_STRUCTURE=PASS`), then Kotlin compile preflight `:core:data:compileDebugKotlin :feature:radio:compileDebugKotlin` (`BUILD SUCCESSFUL in 1m 32s`).
- [x] The same validation run passed `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 45s`; 152 actionable tasks: 84 executed, 27 from cache, 41 up-to-date).
- [x] The same run passed `:app:assembleDebug` (`BUILD SUCCESSFUL in 1m 31s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: `23,565,684` bytes; SHA-256 `6ab6c840e8885b5905fb17e86976edc313cafd8a6597a0db191412f03ae68003`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] Promotion run `33526207570` fast-forward promoted the exact validated product commit `13008127492e82a0abd2ff530c6e42478136181c` to `main` only after the complete validation run passed.
- [ ] **Physical catalog/navigation gate:** pending real-device verification of all four filters and `Tutte`, authoritative visible/Previous/Next order, UK queue Previous/Next/wrap-around, one-item Sport no-skip behavior, Atbir corrected label/playback, Medina FM vs Medina FM Amazigh distinct playback, favorites/snapshot stability, and existing fallback/live-reconnect/local-Music/Sleep-Timer regressions. The debug prerelease is published only after this spec-after snapshot is committed; physical PASS must not be recorded until the user reports device results.


### Radio runtime playback compatibility recovery contract

Physical gate status: **FAILED on device (2026-09-01)**. The catalog-expansion APK was reported to leave many station taps without audible playback or a useful final error. This objective supersedes physical approval of that APK; the catalog/navigation work remains CI-validated but is not physically approved until a corrected APK passes the checks below.

Root-cause evidence before product code:
- Exhaustive GitHub Actions runtime probe run `33549428766`, job `99994937004`, tested all 39 built-in stream URLs from the catalog: every endpoint returned HTTP 200 and `ffprobe` identified real MP3/AAC/HLS audio. The broad device failure is therefore not explained by dead catalog URLs alone.
- Eleven built-ins currently resolve to HLS: Medi1 Radio, Chada FM, RTL 102.5, Radio Deejay, RDS 100% Grandi Successi, Radio Italia Solo Musica Italiana, Radio Capital, m2o, BBC Radio 1, BBC Radio 2, and BBC Radio 4.
- `:core:playback` currently depends on `media3-exoplayer` and `media3-session` 1.11.0 but not `media3-exoplayer-hls`; HLS support must be added using the same Media3 version.
- Chada FM returns an HLS manifest from `https://stream.bodkas.com/playlist?id=chadafmradio`, whose URI does not end in `.m3u8`; the radio MediaItem path must explicitly mark this known endpoint as HLS instead of relying only on URI-extension inference.
- The current Aswat FM catalog URL `https://broadcast.ice.infomaniak.ch/aswat-high.mp3` redirects to cleartext `http://aswat.ice.infomaniak.ch/aswat-high.mp3` during the runtime probe. Do not enable global cleartext traffic to accommodate one station. Use a separately verified HTTPS endpoint that remains HTTPS, or exclude Aswat if no reliable HTTPS endpoint passes audio validation.
- The existing radio UI only receives synchronous `play()` operation failures. ExoPlayer failures occurring after `prepare()` are asynchronous, so a failed station can visually remain on `Connessione…` with no meaningful explanation. The shared playback state must expose an asynchronous playback error that Radio can render.

Acceptance contract:
- Add `androidx.media3:media3-exoplayer-hls:1.11.0` to `:core:playback`; keep Media3 versions aligned and retain the single ExoPlayer / MediaLibrarySession architecture.
- Radio MediaItems must set HLS MIME deterministically for known HLS cases that cannot be inferred from the URI extension, including Chada FM. Normal direct MP3/AAC streams must not be mislabeled as HLS.
- Preserve the active radio queue snapshot, Previous/Next wrap behavior, station-local fallback retry semantics, live-edge reconnect behavior, notification/lock-screen/Android Auto controls, mini-player, floating overlay, and LOCAL music behavior.
- Resolve Aswat without broad cleartext permission: prefer a verified direct HTTPS stream that stays HTTPS and carries real audio; otherwise remove the built-in station and update catalog counts/order/idempotency tests accordingly.
- Add a shared asynchronous playback-error projection. Starting a new playback attempt clears the previous error; a successful/ready playback clears stale errors; a terminal Media3 playback failure is exposed to Radio as a concise user-visible message rather than leaving the UI apparently inert. Error reporting must not create a second player/session or mutate the active radio queue.
- Unit/structural tests must cover: HLS dependency presence; HLS MIME classification including Chada and ordinary `.m3u8`; direct MP3/AAC non-HLS classification; asynchronous player-error projection and clearing; Radio ViewModel rendering of the shared error; radio queue/fallback/live-reconnect regressions; and catalog idempotency/no-duplicates/order if Aswat changes.
- Real CI gate before promotion: `:core:data:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, with persistent debug signer v1 verification and APK SHA-256 recorded.
- No product commit may be promoted to `main` unless the exact validated commit passes the full gate. After promotion, record spec-after evidence, publish a signed debug prerelease through the permanent Release workflow, and remove all temporary research/product/validation branches.

Physical re-test gate (remains **PENDING/FAILED** until explicit device confirmation):
- HLS Marocco: Medi1 Radio and Chada FM both start audibly.
- HLS Italia: test at least RTL 102.5 plus one of Radio Deejay / RDS / Radio Capital / m2o.
- HLS UK: BBC Radio 1 plus one of BBC Radio 2 / BBC Radio 4.
- Direct streams: test representative MP3/AAC stations in Marocco, Italia, UK and Sport (for example HIT RADIO, Radio 105 or RMC, Capital FM or Heart UK, Radio Sportiva).
- Previous/Next must still traverse the exact selected category snapshot, including UK, with wrap-around and no queue collapse after a failed/retried endpoint.
- A genuinely unavailable station/network failure must produce a visible error instead of an indefinitely inert `Connessione…` state.
- LOCAL music, notification, mini-player, Now Playing, floating overlay and Sleep Timer remain regression checks.


### Validation record — radio runtime playback compatibility recovery

- [x] Spec-before commit: `4db6e393769fc52df281be94e93b2d12454ca7f6` (`docs: define radio playback compatibility recovery`).
- [x] Exact product commit: `212d5c1aecd3de6c38543c9b8393264c45c4578c` (`fix: restore radio playback compatibility`), direct child of the spec-before commit and the only product commit in the objective.
- [x] Runtime compatibility fix: `androidx.media3:media3-exoplayer-hls:1.11.0` added alongside Media3 1.11.0; known HLS streams are classified deterministically, including explicit HLS MIME for Chada FM whose URI does not end in `.m3u8`.
- [x] Aswat FM now uses the separately verified direct HTTPS stream `https://aswat.ice.infomaniak.ch/aswat-high.mp3`; the repository repairs only the exact legacy built-in Aswat row so existing installs migrate without global cleartext traffic or overwriting custom rows.
- [x] Terminal asynchronous playback failures are propagated from the playback service only after station-local fallback exhaustion and projected through shared playback state to the Radio UI; a new playback attempt or READY state clears stale errors. No second player/session is introduced and the active radio queue is not mutated by error reporting.
- [x] GitHub Actions validation run `33551020947`, job `100000189350`, checked out the exact product commit and passed isolated-parent/file-list checks, structural HLS/Chada/Aswat/error checks, Java 17/signing setup, and Android 37 SDK setup.
- [x] Kotlin preflight passed `:core:data:compileDebugKotlin`, `:core:playback:compileDebugKotlin`, and `:feature:radio:compileDebugKotlin` (`BUILD SUCCESSFUL in 1m 41s`).
- [x] Full relevant unit-test suite passed: `:core:data:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 49s`; 152 actionable tasks: 85 executed, 26 from cache, 41 up-to-date).
- [x] `:app:assembleDebug` passed (`BUILD SUCCESSFUL in 1m 48s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: `23,696,756` bytes; SHA-256 `725355a083ab40f097cefd2c5fe4b519a99409dcfa3717f29277bc1e031aebfe`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The validation workflow deleted its temporary validation branch after all gates passed. The exact validated product commit was then fast-forward promoted to `main`; no fixup or merge commit changed the product snapshot.
- [ ] **Physical radio playback recovery gate:** remains PENDING until explicit real-device confirmation on the published recovery APK. Required targeted checks: Medi1 Radio and Chada FM; RTL 102.5 plus Deejay/RDS/Capital/m2o coverage; BBC Radio 1 plus BBC Radio 2/4 coverage; representative direct MP3/AAC streams; Previous/Next snapshot/wrap behavior; visible error on genuine terminal failure; and LOCAL music/notification/mini-player/Now Playing/floating overlay/Sleep Timer regressions.


### Verified radio catalog expansion — radio.co.ma / radio-italiane.it

This objective starts from the 39-station built-in catalog at `fba17da820c1ae30f20da87f541db88a42f977ee`. The supplied directory pages are discovery/identity hints only; no station is admitted from aggregator metadata alone. Admission requires a sufficiently strong station identity/provenance plus an HTTPS endpoint that returns HTTP 200 and exposes decodable audio. Ambiguous identity is a rejection even when transport works.

Final admission decision before product code: **13 new built-ins**, producing **52 built-ins total: 21 Marocco / 23 Italia / 6 UK / 2 Sport**. `Radio Italia Solo Musica Italiana` is already present and remains a single built-in row; the supplied Radio Italia directory candidate is therefore a verified duplicate, not a new station.

Accepted additions, in authoritative within-category insertion order:

| ID | Display name | Category | Verified primary stream |
| --- | --- | --- | --- |
| `adwaa-fm-one` | Adwaa FM One | Marocco | `https://stream.zeno.fm/5bxh2nh0x1zuv` |
| `radio-monte-carlo-doualiya` | Radio Monte Carlo Doualiya | Marocco | `https://montecarlodoualiya128k.ice.infomaniak.ch/mc-doualiya.mp3` |
| `rds-relax` | RDS Relax | Italia | `https://stream.rds.radio/audio/rdsrelax.stream_aac/playlist.m3u8` |
| `radio-subasio` | Radio Subasio | Italia | `https://icy.unitedradio.it/Subasio.mp3` |
| `radio-zeta` | Radio Zeta | Italia | `https://streamingv2.shoutcast.com/radio-zeta_48.aac` |
| `radio-bruno` | Radio Bruno | Italia | `https://router.xdevel.com/audio4s975355-254/stream/icecast.audio` |
| `radiofreccia` | Radiofreccia | Italia | `https://dd782ed59e2a4e86aabf6fc508674b59.msvdn.net/live/S3160845/0tuSetc8UFkF/playlist_audio.m3u8` |
| `rai-isoradio` | Rai Isoradio | Italia | `https://icecdn-19d24861e90342cc8decb03c24c8a419.msvdn.net/icecastRelay/S3822289/9T4F68Q3TT4m/icecast` |
| `rai-radio-3-classica` | Rai Radio 3 Classica | Italia | `https://radiotreclassica-live.akamaized.net/hls/live/2032595/radiotreclassica/radiotreclassica/playlist.m3u8` |
| `radio-maria` | Radio Maria | Italia | `https://dreamsiteradiocp4.com/proxy/rmitaliamontecarlo?mp=/stream` |
| `radio-radicale` | Radio Radicale | Italia | `https://live.radioradicale.it/live.mp3` |
| `radio-cuore` | Radio Cuore | Italia | `https://stream10.xdevel.com/audio32s975552-1839/stream/icecast.audio` |
| `rete-sport` | Rete Sport | Sport | `https://icecast.ithost.it/retesport.ogg` |

Identity/provenance notes for admitted stations:
- Adwaa: current official `adwaafm.com` player identifies the main service as **ADWAA FM ONE** and exposes/correlates the Zeno stream above; the similarly named `Adwaa FM 2` Radio Browser candidate resolves to the same underlying stream and is not a distinct station.
- Radio Monte Carlo Doualiya: Radio Browser identity matches the official `mc-doualiya.com` homepage and the Infomaniak stream.
- Italian admissions were matched against their official station/group identities; `Rai Radio Classica` is seeded under its current official brand **Rai Radio 3 Classica**. Radio Bruno's HTTPS Xdevel feed was additionally fingerprinted against its known legacy official HTTP feed and produced an exact Chromaprint match. Rete Sport's current official site JavaScript exposes the exact `icecast.ithost.it/retesport.ogg` endpoint.

Explicitly excluded from this expansion:
- **4U Classic Rock** — working HTTPS audio exists, but the requested Moroccan identity/provenance is not strong enough.
- **Adwaa FM 2** — no distinct verified station: the discovered candidate shares the exact Adwaa FM One stream.
- **Alpha Radio** — discovered HTTPS candidate currently returns HTTP 404 and no audio; no stronger working replacement was established.
- **Hit Radio 100% TikTok / Classique / Mgharba / Party / Urban** — all five legacy endpoints were individually verified as HTTP 200 HTTPS MP3 and simultaneous Chromaprint proved all five are distinct from HIT RADIO Maroc and from each other. They are nevertheless excluded because the supplied legacy labels cannot be mapped with sufficient certainty to HIT RADIO's current official web-radio taxonomy; transport alone is insufficient identity proof.
- **Hits1 Maroc** — official-family stream resolves to decodable MP3 but returns HTTP 401 rather than the required HTTP 200.
- **Idaa Al Watania** — official SNRT HLS endpoint currently returns HTTP 400/no audio.
- **Marrakech Plus** — a working HTTPS MP3 candidate exists, but no sufficiently strong direct official endpoint correlation was established.
- **Radio Assadisa FM** — official SNRT HLS endpoint currently returns HTTP 400/no audio.
- **Radio Only Raï** — a working HTTPS transport exists, but current official identity/provenance is too weak/legacy.
- **Radio Sawa** — the live radio service was discontinued in 2024; no current live service is admitted.
- **Radio Sawt Alamal** — working Zeno candidates exist but no sufficiently strong official homepage/stream correlation was established.
- **Radio Soleil** — no solid current Moroccan HTTPS identity/endpoint pair was established.
- **Radio Zine Bladi** — the station has a current official web presence and a working HTTPS AAC candidate, but the endpoint could not be directly correlated strongly enough to the official source; reject conservatively.
- **Oxygene FM** — discovery points to Tunisian/legacy or non-HTTPS identities rather than a solid Moroccan HTTPS station.
- **Radio Norba** — Radio Browser's official-homepage main endpoint is HTTP-only; an HTTPS Xdevel candidate decodes audio but lacks sufficiently strong positive identity correlation to replace it, so no cleartext weakening and no seed.
- **Radio Italia** directory candidate — duplicate of existing `radio-italia-smi`; do not create a second row.

Historical rejects **must not be retested or reintroduced by this objective**: Radio 2M, Radio Chaine Inter, Al Amazighia, Radio Plus Casablanca, Radio Kiss Kiss, Radio 24.

Authoritative built-in queue order after implementation (52 IDs):
`medi1-radio`, `hit-radio-maroc`, `chada-fm`, `atlantic-radio`, `cap-radio`, `med-radio`, `radio-mars`, `radio-plus-agadir`, `radio-azawan`, `aswat-fm`, `mfm-radio`, `radio-medina-fm`, `medina-fm-amazigh`, `ness-radio`, `radio-manarat`, `radio-tanger-med`, `radio-yabiladi`, `radio-achkid-fm`, `radio-star-maroc-fm`, `adwaa-fm-one`, `radio-monte-carlo-doualiya`, `rtl-102-5`, `radio-deejay`, `radio-105`, `rds-100-grandi-successi`, `radio-italia-smi`, `virgin-radio-italia`, `radio-capital`, `m2o`, `radio-monte-carlo`, `r101`, `rai-radio-1`, `rai-radio-2`, `rai-radio-3`, `rds-relax`, `radio-subasio`, `radio-zeta`, `radio-bruno`, `radiofreccia`, `rai-isoradio`, `rai-radio-3-classica`, `radio-maria`, `radio-radicale`, `radio-cuore`, `bbc-radio-1`, `bbc-radio-2`, `bbc-radio-4`, `capital-fm-london`, `heart-uk`, `classic-fm`, `radio-sportiva`, `rete-sport`.

Acceptance contract:
- [x] Seed exactly the 13 admitted stations above with stable IDs and the verified primary URLs; do not seed any excluded/duplicate candidate.
- [x] Preserve all existing 39 station IDs, names, primary/fallback semantics and their relative order; append the two admitted Morocco stations at the end of the Morocco block, the ten admitted Italia stations at the end of the Italia block, and Rete Sport after Radio Sportiva. UK remains unchanged.
- [x] Category projection must yield exactly 21 Marocco, 23 Italia, 6 UK and 2 Sport stations, in source/catalog order. Rete Sport is `SPORT`, never `ITALY`; Radio Monte Carlo Doualiya follows the user-requested Morocco directory grouping for this product taxonomy.
- [x] Seeding remains additive/idempotent and never overwrites a pre-existing user/custom row. Running seed repeatedly must not duplicate rows; `radio-italia-smi` remains exactly one built-in identity.
- [x] No Room schema migration or broad network-security/cleartext change is permitted for this catalog-only expansion.
- [x] Existing queue snapshot, Previous/Next/wrap behavior, favorites, fallback/reconnect, asynchronous playback errors, HLS handling, local Music, mini-player, notification/lock-screen, Android Auto, floating overlay and Sleep Timer behavior remain unchanged.
- [x] Unit tests must hard-code the 52 expected built-in IDs/order, uniqueness, seed idempotency/additive behavior, duplicate-Radio-Italia prevention, custom-tail behavior, and exact category counts/order including `Rete Sport -> SPORT`.
- [x] Real CI before promotion must re-probe all 13 admitted primary URLs with HTTP 200 + final HTTPS + decodable audio, then pass `:core:data:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` with persistent debug signer v1 verification and recorded APK SHA-256/size.
- [x] Only the exact validated product commit may be fast-forward promoted to `main`; then record spec-after evidence, publish the signed debug prerelease through the permanent Release workflow, and remove all temporary branches.

Research evidence before spec/code:
- Broad Radio Browser + directory discovery: run `33581688036`, job `100097112239`.
- Targeted official-player discovery: run `33581801578`, job `100097446890`.
- Deep official/Radio Browser resolver: run `33581985831`, job `100097984058`.
- Exact identity resolver: run `33582163782`, job `100098524920`.
- Initial stream/Chromaprint probe: run `33582465767`, job `100099443591`; its live-stream PASS label logic was intentionally superseded because it incorrectly required endless live `curl` transfers to reach EOF.
- Corrected definitive gate: run `33582849231`, job `100100635379`; all 13 proposed admissions passed HTTP 200 + final HTTPS + decodable audio, while Alpha=404, Hits1=401, Watania=400 and Assadisa=400 were confirmed failures. The same run fingerprinted HIT RADIO Maroc plus all five supplied HIT legacy streams simultaneously; all six fingerprints were captured successfully and every pair was distinct.

### Validation record — verified directory catalog expansion

- [x] Spec-before commit: `9c4991a78ab814afa4e6099842ce0cda917bebce` (`docs: define verified directory catalog expansion`), direct child of baseline `fba17da820c1ae30f20da87f541db88a42f977ee`.
- [x] First isolated product attempt `51793435ee183727c5932878f5fd9fc13e58340f` was **not promoted**. Validation run `33593036393`, job `100130762007`, passed product isolation, structure, persistent signing setup, Android 37 setup, the 13/13 live stream re-probe and Kotlin compile, then correctly stopped when the separate `InitialRadioCatalogContractTest` still asserted the previous 39-station count. APK/signature/promotion steps were skipped by the failed gate.
- [x] Corrected clean product commit: `9f287334b856faf5adfa80b24c54bfa939c6744b` (`feat: expand verified radio catalog`), rebuilt directly from the spec-before commit rather than stacked on the failed snapshot. Its product diff is exactly five files: `InitialRadioCatalog.kt`, `CoreDataRepositoriesTest.kt`, `InitialRadioCatalogContractTest.kt`, `RadioStationGrouping.kt`, and `RadioStationGroupingTest.kt`.
- [x] Final catalog contract is exactly **52 built-ins: 21 Marocco / 23 Italia / 6 UK / 2 Sport**. The authoritative 52-ID order is hard-coded in tests; built-in IDs and primary URLs are unique; `radio-italia-smi` remains exactly one identity; `Rete Sport` maps to `Sport`; `Radio Monte Carlo Doualiya` maps to `Marocco`; custom stations remain after the built-in sequence in deterministic order.
- [x] Corrected GitHub Actions validation run `33593656433`, job `100132607669`, re-probed all 13 admitted primary streams and recorded `13/13 PASS` for HTTP 200 + final HTTPS + decodable MP3/AAC audio. Kotlin preflight passed (`BUILD SUCCESSFUL in 1m 53s`).
- [x] Full regression/unit gate passed in the same run: `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest` (`BUILD SUCCESSFUL in 53s`; 152 actionable tasks: 85 executed, 26 from cache, 41 up-to-date).
- [x] `:app:assembleDebug` passed (`BUILD SUCCESSFUL in 1m 50s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: **23,696,756 bytes**; SHA-256 `5a57553e1a242b43999acb1289e06bbfa635f3c53c0fd0d88219e06a3d8c355e`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The exact validated product commit `9f287334b856faf5adfa80b24c54bfa939c6744b` was fast-forward promoted from spec-before to `main` by the successful validation job. Temporary product/research/validation branches were deleted after success.
- [ ] **Physical catalog-expansion smoke gate:** PENDING until the published prerelease is installed on a real device. Verify the 52-station `Tutte` order and category counts; new Morocco/Italia/Sport stations; Previous/Next wrap within category snapshots; rejected/duplicate candidates absent; and local Music / notification / mini-player / Now Playing / overlay / Sleep Timer regressions.

### Physical catalog correction — Radio Maria removal and BBC Radio 1/2 HTTPS playback

This is a new spec-before objective opened from real-device feedback on the catalog-expansion prerelease. It must be completed before the separate Sport-candidate discovery objective.

Research / physical evidence before product code:
- The user explicitly requests removal of built-in `radio-maria` for personal catalog preference; this is not a transport failure.
- BBC support documentation states that after BBC Sounds closed outside the UK on 21 July 2025, BBC Radio 1 and Radio 2 remain available for international live listening; listening to BBC audio on non-BBC platforms is stated to be unaffected. Therefore an intentional blanket geo-block outside the UK is not accepted as the root cause without contrary evidence.
- Real-device report: BBC Radio 1 and BBC Radio 2 fail on a Samsung S25 in Italy with the app's terminal playback error, while BBC Radio 4 was not reported as failing.
- Diagnostic GitHub Actions run `33600551468`, job `100153068553`, compared the current catalog masters with direct worldwide BBC HLS endpoints using normal curl, `ExoPlayerLib/1.11.0` User-Agent, Android-browser User-Agent, and `ffprobe` audio decoding.
- The current BBC Radio 1 master returns HTTP 200 over HTTPS but its only child playlist is absolute cleartext `http://as-hls-ww-live.akamaized.net/pool_01505109/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d96000.norewind.m3u8`.
- The current BBC Radio 2 master returns HTTP 200 over HTTPS but its only child playlist is absolute cleartext `http://as-hls-ww.live.cf.md.bbci.co.uk/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d96000.norewind.m3u8`.
- Desktop `ffprobe` follows those HTTP children and therefore previously produced a false-positive compatibility signal relative to Android, where TamalutRadio intentionally does not enable global cleartext traffic. The device failure is therefore explained by HTTPS-master -> HTTP-child downgrade, not by the top-level 200 response alone.
- Direct worldwide HTTPS BBC Radio 1 `https://as-hls-ww-live.akamaized.net/pool_01505109/live/ww/bbc_radio_one/bbc_radio_one.isml/bbc_radio_one-audio%3d96000.norewind.m3u8` returned HTTP 200 / `application/vnd.apple.mpegurl`, used relative `.ts` segment paths, and decoded AAC successfully with both default and ExoPlayer User-Agent probes.
- Direct worldwide HTTPS BBC Radio 2 `https://as-hls-ww-live.akamaized.net/pool_74208725/live/ww/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d96000.norewind.m3u8` returned HTTP 200 / `application/vnd.apple.mpegurl`, used relative `.ts` segment paths, and decoded AAC successfully with both default and ExoPlayer User-Agent probes.
- BBC Radio 4 already uses the same direct `as-hls-ww-live.akamaized.net` family with HTTPS and relative media segments, providing a working control consistent with the real-device report.

Acceptance contract:
- [x] Remove `radio-maria` from the authoritative built-in catalog and Italia grouping/order. The new built-in total is exactly **51 = 21 Marocco / 22 Italia / 6 UK / 2 Sport**.
- [x] Existing installations must retire only the exact historical built-in Radio Maria row (`id=radio-maria`, `name=Radio Maria`, primary stream `https://dreamsiteradiocp4.com/proxy/rmitaliamontecarlo?mp=/stream`, `isCustom=false`). A custom/user-modified station must never be deleted merely because its ID/name resembles the retired built-in.
- [x] Replace BBC Radio 1 and BBC Radio 2 primary streams with the verified direct worldwide HTTPS HLS URLs above. Do not enable global cleartext traffic and do not add an HTTP fallback.
- [x] Existing installations must repair BBC Radio 1/2 only when the stored row is the exact non-custom legacy built-in with the old `a.files.bbci.co.uk/.../nonuk/...` primary URL. Preserve custom/user-modified rows and unrelated fallback data.
- [x] Repeated catalog initialization must converge idempotently to exactly 51 built-ins, with Radio Maria absent, BBC 1/2 repaired at most once, unique built-in IDs and primary URLs, authoritative built-in ordering, and deterministic custom tail unchanged.
- [x] Automated tests must explicitly cover the 51-ID contract, category counts/order, targeted Radio Maria retirement, protection of modified/custom Radio Maria-like rows, targeted BBC 1/2 migration, protection of modified/custom BBC rows, and no cleartext BBC endpoint in the built-in catalog.
- [x] Validation must re-probe the two final BBC direct HTTPS endpoints with HTTP 200 + final HTTPS + decodable audio, inspect that their manifests do not introduce absolute `http://` child playlists, and keep BBC Radio 4 as a control.
- [x] Full regression gate before promotion remains `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, followed by persistent debug signer and APK SHA-256 verification.
- [x] Promote only the exact validated product commit to `main`, record this docs-only spec-after as its direct child, and delete temporary diagnostic/product/validation branches. Distribution of this exact spec-after commit through the permanent debug prerelease workflow is the immediate post-spec-after step and is verified independently in GitHub Release metadata.
- [x] **Physical re-test gate:** PASS on Samsung S25 / Italian network. User confirmed Radio Maria is absent; BBC Radio 1 and BBC Radio 2 start correctly on the real device; BBC Radio 4 remains working. The corrective physical gate is closed.

The proposed ON Sport FM / talkSPORT / Radio Manà Manà Sport Roma candidates are intentionally deferred to the next separate catalog-discovery objective after this corrective cycle closes.

### Validation record — BBC Radio 1/2 HTTPS correction and Radio Maria retirement

- [x] Real-device failure diagnosis: BBC Radio 1/2 were not rejected as a generic international geo-block. BBC support states Radio 1/2 remain available for international live listening after the 21 July 2025 BBC Sounds international closure, and non-BBC-platform listening remains available.
- [x] Diagnostic run `33600551468`, job `100153068553`, proved the previous HTTPS BBC Radio 1/2 master manifests each introduced an absolute cleartext `http://` child playlist. Desktop `ffprobe` followed those children, while Android correctly blocked cleartext, explaining the Samsung S25 failure without weakening app network security.
- [x] Spec-before commit: `053e0d7467b8fa68f3aa53a3e0c15d1433a6d323` (`docs: define BBC and Radio Maria correction`), direct child of baseline `4ab632297f6200a24044e1a379e1e740f34017b6`.
- [x] Product commit: `39051590b420899e42f7175c2ea0257ab6e35ef1` (`fix: correct BBC streams and retire Radio Maria`), direct child of the spec-before. Product diff is exactly six files: catalog, repository migration logic, catalog/repository tests, grouping and grouping tests.
- [x] Final catalog contract is exactly **51 built-ins = 21 Marocco / 22 Italia / 6 UK / 2 Sport**. Built-in Radio Maria is absent. Existing exact historical non-custom Radio Maria rows are retired; custom/modified rows are protected.
- [x] BBC Radio 1 and BBC Radio 2 now use direct `https://as-hls-ww-live.akamaized.net/...` worldwide media playlists. Exact legacy non-custom BBC rows are migrated idempotently while modified/custom rows and fallback data are preserved. No global cleartext permission and no HTTP BBC fallback were added.
- [x] Validation run `33601374267`, job `100155602834`, passed the direct BBC Radio 1/2 endpoints plus BBC Radio 4 control with HTTP 200, final HTTPS, HLS manifests containing no absolute `http://` child, and AAC audio decoding with `ExoPlayerLib/1.11.0` User-Agent.
- [x] Kotlin compile preflight passed (`BUILD SUCCESSFUL in 1m 44s`). Full regression/unit gate passed (`BUILD SUCCESSFUL in 53s`; 152 actionable tasks: 85 executed, 26 from cache, 41 up-to-date) for `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`.
- [x] `:app:assembleDebug` passed (`BUILD SUCCESSFUL in 1m 49s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: **23,696,756 bytes**; SHA-256 `5f5d745b448765058857c78adf69385444e1ac83dc2098297662b3ffa7011fc6`; persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The exact validated product `39051590b420899e42f7175c2ea0257ab6e35ef1` was fast-forward promoted from the exact spec-before to `main` by the successful validation job. Temporary `bbc-maria-product`, `bbc-radio-diagnostics`, and `bbc-maria-validation` branches were deleted after success.
- [x] **Physical re-test gate:** PASS on Samsung S25 / Italian network. User confirmed Radio Maria is absent, BBC Radio 1 and BBC Radio 2 play correctly without the previous terminal error, and BBC Radio 4 continues to work. Physical validation for this corrective cycle is complete.
- [ ] Separate next objective after this correction closes: discovery/verification of ON Sport FM, talkSPORT, and optional Radio Manà Manà Sport Roma.

### Search fields — Radio and local Music

This is a new standalone objective approved after the BBC/Radio Maria physical correction gate passed on Samsung S25 / Italian network. The deferred Sport-station discovery objective remains separate and must not be mixed into this work.

Approved UI / behavior contract before product code:
- [x] Radio exposes a search affordance in the `Radio` header. Tapping the search icon opens a full-width `Cerca radio` field below the `Preferiti / Tutte le radio` tabs and, when `Tutte le radio` is active, below the country/category filter chips. Opening search focuses the field and opens the keyboard; the close `X` exits search and clears the query; an in-field clear action clears non-empty text; IME Done hides only the keyboard.
- [x] Local Music exposes the same search affordance in the `Musica` header. `Cerca brani` appears below the selected-folder panel in a stable position, before the track list / local playback controls. Opening/closing/clearing follows the same semantics as Radio.
- [x] Search updates in real time for every text edit, trims surrounding whitespace, is case-insensitive, and uses substring/contains matching rather than prefix or exact matching. Radio matches station display name. Local Music matches the displayed `LocalAudioTrack.title`, which is currently derived from the file display name without extension. No fuzzy matching, typo correction, accent folding, remote search, or ID3 re-scan is introduced in this objective.
- [x] Radio search is constrained by the existing browsing context: `Preferiti` searches only favorites; `Tutte le radio` searches only the current country/category filter (`Tutte / Marocco / Italia / UK / Sport`). Changing tab or category while search is open preserves the query and immediately re-filters in the new context.
- [x] Query-empty state is the normal unfiltered list. A non-empty query with zero matches has a dedicated empty-result state (`Nessuna radio trovata per …` / `Nessun brano trovato per …`) with `Cancella ricerca`; it must remain distinct from `Nessuna radio preferita`, no-folder, empty-library, loading, and data-error states.
- [x] Selecting a search result closes search, clears the query, and removes keyboard focus. Search is a locate-and-select UI, not a playback queue definition.
- [x] Radio explicitly separates `queueStations` from `visibleStations`: `queueStations` is the full station list for the selected section/category before text search; `visibleStations` is that queue filtered by the query. `playStation()` must locate the tapped station in `queueStations` and pass the complete queue to playback. Example acceptance case: one visible BBC result inside UK still launches the full six-station UK queue at the matching index; Previous/Next and wrap therefore remain category-based and are never reduced to search results.
- [x] Local Music search filters only the displayed list. `playTrack()` continues to pass the complete scanned `tracks` collection to `LocalPlaybackGateway`; Previous/Next, repeat and shuffle semantics therefore remain library-based even when only one search result is visible. Selecting a result closes/clears search after dispatching playback.
- [x] `Riscansiona` keeps the active local-Music query and re-applies it to the rescanned library. Selecting a different SAF folder clears the Music query because the underlying library identity changes.
- [x] Search state is in-memory UI/ViewModel state only. No new Room schema, DataStore preference, Media3 architecture, playback service, queue persistence, or network dependency is authorized.

Required automated coverage before promotion:
- [x] Radio: case-insensitive substring matching, surrounding-whitespace normalization, query restricted to Favorites/category context, zero-search-results distinct from truly empty Favorites, clear/close behavior, and changing section/category while query remains active.
- [x] Radio queue invariant: explicit regression proving `1 visible search result -> complete pre-search context queue unchanged`, correct selected index, and existing Previous/Next ordering/wrap contract preserved.
- [x] Local Music: case-insensitive substring filtering on displayed title/file-derived name, zero-search-results distinct from empty library/no folder, clear/close behavior, query preserved across refresh/rescan and cleared on folder change.
- [x] Local Music queue invariant: explicit regression proving `1 visible search result -> playback receives the complete scanned library`, preserving current order, repeat and shuffle semantics.
- [x] Full regression gate remains `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, followed by persistent debug signer SHA-256 and APK SHA-256 verification.
- [x] Promote only the exact validated product commit to `main`; add a docs-only spec-after direct child; publish that exact spec-after through the permanent debug prerelease workflow; verify Release/tag/asset size/digest/signer; delete all temporary branches.
- [ ] **Physical search-field gate:** PENDING. Verify Radio and Music search interaction on device, context filtering, no-result states, queue behavior after selecting a search result, and regressions for radio/local playback, mini-player/notification/Now Playing/overlay/Sleep Timer.

### Validation record — Radio and local Music search fields

- [x] Spec-before commit: `3409c6b24093da9c6e9d96f06aea2e548e3234e2` (`docs: define search field contract`), direct child of physical-gate baseline `29d578185a3fa4c4d6d955f12ab53c95af1cd010`.
- [x] A first product-builder workflow attempt was rejected before job creation because of helper YAML parsing; it created no product and did not modify `main`. A first isolated product snapshot was then superseded before validation after static review found a missing Compose `TextButton` import. The clean validated product was rebuilt directly from the spec-before rather than stacked on that snapshot.
- [x] Product commit: `9b4dc61dfc0e5d76820337c010e303dd418f9ed6` (`feat: add radio and music search fields`), direct child of the spec-before. Product diff is exactly six files: Radio screen/ViewModel/tests and Library screen/ViewModel/tests; no Room, DataStore, scanner, Media3 service, or playback architecture files changed.
- [x] Radio now separates full browsing-context `queueStations` from search-filtered `visibleStations`. Search is trim-aware, case-insensitive substring matching and remains constrained to Favorites or the selected country/category. Selecting a result closes/clears search but sends the complete pre-search context queue and real index to playback.
- [x] Local Music now exposes search-filtered `visibleTracks` while `playTrack()` continues to send the complete scanned `tracks` collection to playback. Refresh/rescan preserves the active query; changing the SAF folder clears/closes search; selecting a result closes/clears search without changing repeat/shuffle queue semantics.
- [x] Dedicated zero-result states are distinct from truly empty Favorites/no-folder/empty-library states. Header close clears/exits search; in-field clear clears text while search remains open; IME Done only hides the keyboard.
- [x] Validation run `33605163879`, job `100167367948`, passed exact product isolation/structure, Java 17, persistent signing setup and Android 37 setup. Kotlin preflight passed (`BUILD SUCCESSFUL in 1m 49s`; 55 actionable tasks: 40 executed, 15 from cache).
- [x] Full regression/search gate passed (`BUILD SUCCESSFUL in 48s`; 152 actionable tasks: 77 executed, 20 from cache, 55 up-to-date) for `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, and `:app:testDebugUnitTest`. This includes explicit tests for case-insensitive substring matching, context restriction, zero-result distinction, clear/close, refresh/folder semantics, and both `1 visible result -> complete queue unchanged` invariants.
- [x] `:app:assembleDebug` passed (`BUILD SUCCESSFUL in 1m 48s`; 165 actionable tasks: 46 executed, 119 up-to-date). Validation APK: **23,729,524 bytes**; SHA-256 `431496905628e033644ae21619d7095278b2cdff4938830a5b9689f8db5149fb`; persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The exact validated product `9b4dc61dfc0e5d76820337c010e303dd418f9ed6` was fast-forward promoted from the exact spec-before to `main` in the successful validation job. Temporary product and validation branches were deleted after success.
- [ ] **Physical search-field gate:** PENDING. Install the published prerelease and verify Radio/Music search interaction, context filtering and no-result states, search-close/clear behavior, full Radio/category and local-Music queue behavior after selecting a filtered result, and shared playback/mini-player/notification/Now Playing/overlay/Sleep Timer regressions.

### Physical closure — Radio and local Music search fields (2026-09-02)

- [x] **Physical search-field gate: PASS on Samsung S25.** Real-device verification passed the complete approved checklist: case-insensitive/substring matching with surrounding-whitespace trim; Favorites/category-scoped Radio search; full pre-search Radio queue preserved after selecting a single visible result including correct Previous/Next wrap; dedicated zero-result states distinct from genuinely empty states; in-field clear versus header close semantics; IME Done keyboard-only behavior; and equivalent local-Music search behavior including rescan preservation and SAF-folder-change reset.
- [x] Shared playback regressions also passed on device: Radio and local Music playback, mini-player, notification, Now Playing, floating overlay and Sleep Timer remain operational. The Campo di Ricerca objective is physically closed.

### Shared full-shutdown Stop control — mini-player and floating player contract


This objective is intentionally completed before the separate ON Sport FM / talkSPORT / possible Radio Manà Manà Sport Roma discovery cycle. It is a playback-lifecycle/UI extension only; no catalog change is authorized here.

Approved behavior before product code:
- [x] **One shared shutdown mechanism.** A user tap on Stop must execute the same full shutdown sequence already used and physically validated for Sleep Timer expiry. Refactor only as needed so Sleep Timer expiry and manual Stop converge on one app-owned shutdown entry point; do not duplicate the sequence in UI code.
- [x] **Authoritative shutdown effects.** Stop must terminate/clear playback through the existing shared `PlaybackController.stopAndExit` path, shut down and remove the floating overlay, release the shared playback controller, stop `TamalutPlaybackService` so the Media3 notification disappears, and finish/remove all app tasks from Recents. No radio reconnect/fallback attempt or hidden media/network activity may survive shutdown.
- [x] **Sleep Timer interaction.** Manual Stop must cancel/reset any active Sleep Timer to Off before/while entering the common shutdown path so no delayed expiry callback or stale Timer notification/badge remains. Sleep Timer natural expiry continues to reset itself to Off and invoke the same common shutdown entry point.
- [x] **Cold reopen semantics.** Launching TamalutRadio again from its launcher icon after Stop must create a normal fresh app/playback runtime. It must not resume the stopped radio/track or previous position merely because the prior session was manually stopped.
- [x] **Persistent mini-player placement.** Whenever the shared persistent mini-player has a current Radio or local-Music item, expose a standard filled-square Material Stop icon as the fourth transport control immediately after `Successivo`: `Precedente / Play-Pausa / Successivo / Stop`. Existing local shuffle/repeat controls remain unchanged and before the transport group. Stop is an app/session shutdown action, not ordinary pause.
- [x] **Expanded floating-player placement.** Extend the expanded floating transport strip from three to four controls in the same order `Precedente / Play-Pausa / Successivo / Stop`, and expand only the transport width required for that fourth button. The collapsed edge tab, app-entry button, dismiss-X semantics, drag behavior, auto-collapse and saved position remain unchanged.
- [x] **Overlay Stop delegates upward.** The floating overlay must not recreate shutdown logic. Add only the minimal Stop action/callback needed to route a tap back to the same app-owned shutdown entry point used by the mini-player and Sleep Timer expiry.
- [x] **No architecture expansion.** Keep the existing single `MediaLibraryService` / `MediaLibrarySession`, shared Media3 player/controller, `SleepTimerController`, `HandlerSleepTimerScheduler`, overlay coordinator/window and notification channel. No new service, player, MediaSession, scheduler, broadcast receiver, persistence layer, background worker, or shutdown controller is authorized.
- [x] **No catalog/queue behavior changes.** Radio catalog/grouping, fallback URLs/reconnect behavior during normal playback, favorites, search `queueStations/visibleStations`, local Music scanning/search, queue order, Previous/Next, repeat and shuffle semantics remain unchanged outside the explicit terminal Stop action.

Required automated coverage before promotion:
- [x] Structural/runtime gate proving Sleep Timer expiry and manual Stop reference the same common shutdown entry point, whose terminal sequence still contains shared `stopAndExit`, overlay shutdown/release, playback-controller release, `stopService(TamalutPlaybackService)` and `finishAndRemoveTask`.
- [x] Manual Stop with an active Sleep Timer resets the shared Timer state to Off/cancels its scheduled callback before shutdown; normal expiry still invokes shutdown once.
- [x] Mini-player tests prove a dedicated Stop callback/action is exposed for a current Radio/local item while Previous/Play-Pause/Next and local shuffle/repeat delegation remain unchanged.
- [x] Floating-player tests prove `STOP` is the fourth expanded transport action, delegates exactly once to the supplied shutdown callback, and does not mutate ordinary playback controls. Expanded-width/geometry tests must account for four transport buttons without changing collapsed width/height or edge placement behavior.
- [x] Regression tests preserve Radio/local playback state projection, overlay dismiss-X semantics, overlay auto-collapse/session behavior, Sleep Timer preset/custom behavior, notification presentation, queue/repeat/shuffle and search behavior.
- [x] Real CI before promotion must pass `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`, then verify persistent debug signer v1 and record APK SHA-256/size.
- [x] Promote only the exact validated product commit to `main`; append a docs-only spec-after direct child with validation evidence. For this closure, after the prior transient APK was no longer retained and by explicit user instruction, publish the exact validated runtime product `1047e60c838099a6a22023b3e24fe2c5476d437d` through the permanent debug prerelease workflow; verify Release/tag/asset size/digest/signer; delete every temporary product/validation/release-helper branch.
- [x] **Physical Stop gate: PASS (7/7).** Physical test completed on the published `debug-20260902-094218-1047e60` APK: Stop passed from mini-player and expanded overlay for both Radio and local Music; an active Sleep Timer reset to Off without residual badge/callback; audio, media notification and overlay terminated; app task disappeared from Recents; no ghost reconnect/data activity remained; launcher relaunch started a clean non-resumed session.

### Validation record — shared full-shutdown Stop control

- [x] Spec-before commit: `08e4f38fc7d08584ad6e10cc07f53ab13ad98375`.
- [x] First product attempt `50f30ad0efa993a9ced7960c318e191aa8b34db7` was **not promoted**. Validation run `33608429297`, job `100177688042`, checked out that exact product and failed `:app:testDebugUnitTest` with 2 failures out of 58: `OverlayPlaybackArchitectureRegressionTest` and `SleepTimerShutdownArchitectureTest`; APK build, checksum/signature verification and promotion were correctly skipped.
- [x] Root cause of those two failures was stale source-text expectations after a legitimate shared-shutdown refactor, **not** an architectural regression. The overlay guard still forbids a second `ExoPlayer`, `MediaBrowser`, `MediaSession`, `MediaSessionService` or foreground service; its obsolete assertion expected the old three-argument `performOverlayPlaybackAction(...)` call and was updated for the new Stop callback. The Sleep Timer guard still protects the existing shared `stopAndExit`, overlay teardown, controller release and task removal path; its obsolete assertion expected `Intent(context, ...)` while the shared shutdown correctly normalizes to `applicationContext` and uses `Intent(appContext, ...)`.
- [x] Corrected clean product commit: `1047e60c838099a6a22023b3e24fe2c5476d437d` (`feat: add shared full-shutdown stop controls`), direct child of the same spec-before. The only additions beyond the first product snapshot are the two narrowly updated architectural regression tests required to follow the legitimate signatures/context variable while preserving their architecture prohibitions.
- [x] Manual Stop and Sleep Timer expiry converge on the same `TamalutRadioRuntime.shutdown(context)` entry point. Manual Stop first resets the shared Sleep Timer to Off, then the common terminal sequence delegates to the existing `PlaybackController.stopAndExit`, tears down/releases the floating overlay, releases the shared playback controller, defensively stops the existing `TamalutPlaybackService`, and removes app tasks from Recents. Mini-player and expanded overlay add Stop as the fourth transport action without introducing a second player/session/service/scheduler/controller.
- [x] Validation run `33609062627`, job `100179683013`, had helper `head_sha=2dfb32d9a54bfae13b5615a960323e0777b6339f` but explicitly set `PRODUCT_SHA=1047e60c838099a6a22023b3e24fe2c5476d437d`, verified its direct spec-before parent, and executed `git checkout --detach "$PRODUCT_SHA"` before every compile/test/build step. Therefore the code actually validated and built was exactly `1047e60c838099a6a22023b3e24fe2c5476d437d`, not the helper commit.
- [x] The exact detached product passed Kotlin compile preflight, `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`. Unit/regression gate: BUILD SUCCESSFUL with 152 actionable tasks; APK build: BUILD SUCCESSFUL with 165 actionable tasks.
- [x] Validation APK from that run: **23,729,524 bytes**; SHA-256 `31757410e828e19d56752cfd85db50e45b8a07f57249af73cc14c7162fbb15db`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`. The transient Actions artifact is no longer retained and is not used for physical distribution.
- [x] The same successful validation job fast-forward promoted exactly `1047e60c838099a6a22023b3e24fe2c5476d437d` from `08e4f38fc7d08584ad6e10cc07f53ab13ad98375` to `main` and deleted all temporary product/validation branches from that attempt.
- [x] Permanent Release closure: workflow run `33615204254`, job `100199325144`, executed from docs-only `main` head `9ee4b87912c0b60d6ab9806171ac32fd98d05f16` but resolved input `ref=1047e60c838099a6a22023b3e24fe2c5476d437d`, detached exactly that product commit, and passed `:app:assembleDebug` (BUILD SUCCESSFUL; 165 actionable tasks). GitHub prerelease `debug-20260902-094218-1047e60` targets exactly `1047e60c838099a6a22023b3e24fe2c5476d437d` and contains `TamalutRadio-debug-1047e60.apk`, 23,729,524 bytes, SHA-256 `31757410e828e19d56752cfd85db50e45b8a07f57249af73cc14c7162fbb15db`, signed by persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`. Distribution uses the GitHub Release asset, not Actions artifact storage.
- [x] **Physical Stop gate: PASS (7/7).** User physically verified the published prerelease across all required cases: mini-player and expanded floating-player Stop for Radio and local Music; active Sleep Timer reset Off with no residual badge/callback; audio/notification/overlay teardown; Recents removal; no ghost reconnect/data activity; clean cold relaunch without auto-resume. Shared full-shutdown Stop objective is formally CLOSED.

### Sport radio discovery — ON Sport FM / talkSPORT / Radio Manà Manà Sport Roma

This is the next catalog-only objective after the physically closed shared Stop cycle. Product code must not change until the research gate below resolves the candidate set and this spec-before is on `main`.

Research and admission contract before implementation:
- [x] **Candidates.** Investigate exactly these pending Sport radios first: `ON Sport FM` (Egyptian Arabic), `talkSPORT` (English, UK), and `Radio Manà Manà Sport Roma` (Italian, Rome/AS Roma). Other stations are out of scope unless needed only to disambiguate identity/duplicates.
- [x] **Radio Browser identity gate.** Query current Radio Browser records and require station name plus `homepage`/country/language metadata to correlate to the broadcaster. Directory stream URLs are discovery evidence, not sufficient authority by themselves.
- [x] **HTTPS transport gate.** The admitted primary stream must be HTTPS at the final media request. Follow redirects explicitly and reject any candidate whose HTTPS URL downgrades to HTTP or whose HLS master/media playlist embeds HTTP segment/key/child-playlist URLs. This repeats the BBC lesson: an HTTPS outer manifest is not enough.
- [x] **Reachability/audio gate.** From a real GitHub Actions Linux runner, require successful final HTTP status plus at least several seconds of decodable audio using `ffprobe`/`ffmpeg` (or equivalent). Record codec/bitrate/container when observable. A directory “online” flag alone does not pass.
- [x] **ON Sport FM identity.** Prefer a stream that Radio Browser associates with the Egyptian sports station and cross-check the station identity independently (93.7 FM / Egyptian Arabic / sports format). Do not admit a similarly named Greek/other `Sport FM` service.
- [x] **talkSPORT identity.** Require `talksport.com` broadcaster correlation and probe the current HTTPS form of the live stream, following every redirect. Do not seed a legacy cleartext-only URL merely because older directories list it.
- [x] **Radio Manà Manà Sport Roma editorial gate.** Technically qualify it under the same identity/HTTPS/audio rules, then include it only if it remains an active radio service and its narrowly AS-Roma-focused scope is still judged useful in the app’s broad `Sport` category. If admitted, its display name must make the local/team specificity explicit; if excluded, record that as an editorial-scope decision rather than a stream failure.
- [x] **No architecture change.** This objective may only extend the built-in Radio catalog/grouping/tests and resulting queues. Playback engine, fallback/reconnect machinery, search model, favorites, local Music, Media3 service/session/controller, overlay, Stop and Sleep Timer behavior remain unchanged.
- [x] **Stable catalog order.** New accepted Sport entries are appended to the existing built-in catalog in the explicit order fixed by the research result. `Tutte` remains built-in order; `Sport` remains the stable subsequence of that order; custom stations remain in the existing deterministic tail.
- [x] **Persistence remains additive/idempotent.** New stable IDs are inserted only when missing; existing stored/custom rows are never overwritten. No Room schema migration is authorized.
- [x] **Automated coverage.** Update exact built-in count/order/IDs, unique primary URLs, Sport membership/order, seed-idempotency and repository projection tests. Existing Radio queue/search/favorites/fallback and app architecture regression suites must remain green.
- [x] **Standard closure.** After research is recorded in this section, create one clean product commit; validate the exact commit in real GitHub Actions with `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify persistent debug signer and APK SHA-256/size; promote only that exact product to `main`; append docs-only spec-after; publish the exact validated runtime snapshot via the permanent GitHub debug-prerelease workflow; then remove every temporary research/spec/product/validation helper branch.

Research status at spec-before: **RESOLVED — all three candidates admitted for the product commit.**

#### Resolved Sport research evidence before product code

- [x] Radio Browser discovery run `33616899640`, job `100204713313`, identified the intended services rather than similarly named stations: `On Sport FM` UUID `79282606-a5a9-467b-88dc-7a63fc642f80`, homepage `https://www.onsportfm.com/`, country `EG`, primary `https://carina.streamerr.co:2020/stream/OnSportFM`; `TalkSPORT` UUID `177dda8f-ce5f-4f18-a19e-c6c8b6f5319a`, homepage `https://talksport.com/`, country `GB`, language English, primary `https://radio.talksport.com/stream`; `Radio Manà Manà Sport Roma` UUID `32c1aa82-c639-4c73-9236-948bbda511d7`, homepage `https://www.manamanasportroma.it/`, country `IT`, language Italian, primary `https://stream10.xdevel.com/audio2s975363-2142/stream/icecast.audio`. The run later stopped on helper-shell quoting before any media probe; that is tooling evidence only, not a candidate failure.
- [x] Diagnostic stream run `33617139395`, job `100205490003`, showed that a bounded `curl` against a continuous live stream returns code 28 when its time cap expires; inherited shell `errexit` stopped that helper at the first candidate. This is not a station failure and is superseded by the corrected probe below.
- [x] Authoritative transport/audio probe run `33617365307`, job `100206204525`, passed all three candidates on a real GitHub Actions Ubuntu runner. ON Sport FM returned HTTP 200 directly over HTTPS, `audio/mpeg`, no redirect, MP3 44.1 kHz stereo at 96 kbps, `ffprobe` exit 0 and five-second `ffmpeg` decode exit 0. talkSPORT redirected exactly once from `https://radio.talksport.com/stream` to `https://talksport.live.stream.broadcasting.news/stream` (HTTPS to HTTPS), then returned HTTP 200 `audio/aac`; AAC 44.1 kHz mono at about 64.7 kbps; `ffprobe` and five-second decode both exit 0. Radio Manà Manà Sport Roma returned HTTP 200 directly over HTTPS, `audio/aacp`, no redirect; AAC 88.2 kHz stereo at about 96 kbps; `ffprobe` and five-second decode both exit 0.
- [x] All three accepted endpoints are direct audio streams, not HLS/M3U (`Radio Browser hls=0`; final content types are audio), so there is no child-playlist/segment/key URL in which an HTTP downgrade can be hidden. The only redirect observed is talkSPORT’s HTTPS-to-HTTPS redirect above.
- [x] Radio Manà Manà Sport Roma is admitted despite its AS-Roma focus. The current broad `Sport` category already includes the Rome/team-focused `Rete Sport`, the service is active, and retaining the full display name `Radio Manà Manà Sport Roma` makes its local/team scope explicit rather than misleading users.
- [x] Final product target is exactly **54 built-in stations**: existing 51 plus three appended Sport stations. The authoritative Sport subsequence becomes exactly `radio-sportiva`, `rete-sport`, `on-sport-fm`, `talksport`, `radio-mana-mana-sport-roma`, corresponding to `Radio Sportiva → Rete Sport → ON Sport FM → talkSPORT → Radio Manà Manà Sport Roma`.
- [x] New stable IDs and primary streams fixed before code: `on-sport-fm` → `https://carina.streamerr.co:2020/stream/OnSportFM`; `talksport` → `https://radio.talksport.com/stream`; `radio-mana-mana-sport-roma` → `https://stream10.xdevel.com/audio2s975363-2142/stream/icecast.audio`.

#### Validation record — Sport radio expansion

- [x] Final research-resolved spec-before commit: `62bf499df6cd84ed5accf705985753e72d20be98`, direct child of the initial Sport spec-before `0379c7afc40881875b46a2a664a5a80823cf5706`. Product code remained untouched until the candidate set and exact endpoints/order were resolved in `PROJECT_SPEC.md`.
- [x] Clean product commit: `83628093e7261d23a7013f5a4106c48c31a43c3f` (`feat: add verified Sport radio stations`), direct child of `62bf499df6cd84ed5accf705985753e72d20be98`. Diff is limited to five expected files: built-in catalog, Radio grouping, and their catalog/order/idempotency tests; no playback/service/session/overlay/Stop/Sleep Timer or Room-schema file changed.
- [x] Product result: catalog is exactly **54 built-ins** and `Sport` is exactly **5** entries in stable order `Radio Sportiva → Rete Sport → ON Sport FM → talkSPORT → Radio Manà Manà Sport Roma`. New IDs are `on-sport-fm`, `talksport`, `radio-mana-mana-sport-roma`; seeding remains additive/idempotent and custom-tail ordering is unchanged.
- [x] Exact validation run `33617993127`, job `100208198916`, had helper head `72162a356adc706923f97a1972766ced05096c87` but explicitly verified `sport-radio-product=83628093e7261d23a7013f5a4106c48c31a43c3f`, verified its parent `62bf499df6cd84ed5accf705985753e72d20be98`, and executed `git checkout --detach "$PRODUCT_SHA"` before build/test. Therefore every gate ran on the exact product commit.
- [x] Exact detached product passed `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; Gradle result `BUILD SUCCESSFUL` with 208 actionable tasks (163 executed, 35 from cache, 10 up-to-date).
- [x] Validation APK: **23,729,524 bytes**, SHA-256 `3cd430ccbae536882f7dc727fcf35b1013daeba4f62293c1f754d8a474749eac`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The same successful validation job fast-forward promoted exactly `83628093e7261d23a7013f5a4106c48c31a43c3f` from `62bf499df6cd84ed5accf705985753e72d20be98` to `main`, then removed the temporary product and validation branches. The spec-after is documentation-only and does not alter the validated runtime snapshot.
- [x] **Permanent Release evidence:** exact Sport runtime `83628093e7261d23a7013f5a4106c48c31a43c3f` was published as prerelease `debug-20260902-101941-8362809`; asset `TamalutRadio-debug-8362809.apk` is 23,729,524 bytes with SHA-256 `3cd430ccbae536882f7dc727fcf35b1013daeba4f62293c1f754d8a474749eac` and persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] **Physical Sport radio gate:** PASS on the final device recheck. ON Sport FM, talkSPORT and Radio Manà Manà Sport Roma appear after Radio Sportiva/Rete Sport; all three start real audio; Previous/Next follows the five-station Sport order with wrap-around; background playback, notification and overlay behavior remain normal.

### Active radio list visibility refinement contract

This UX correction was discovered during the physical Sport-radio gate. The radio queue behavior itself was rechecked physically and is not part of this change: when a station is explicitly started from a filtered category, that category snapshot remains the Media3 Next/Previous queue. Merely changing the visible filter while an older queue is already playing does not rewrite that queue.

- [x] **Active card auto-visibility.** When the Radio screen is entered/re-entered, when the active radio changes through Next/Previous, or when the rendered category/section changes, if the currently playing radio belongs to the rendered non-search list, its card must be brought into the visible LazyColumn viewport so the highlighted row and `LIVE` badge can be seen without manual scrolling.
- [x] **No category hijack.** If the active radio is not present in the currently rendered category/section, the UI must not switch category, alter the playback queue, or scroll to another station merely to expose the active radio.
- [x] **Respect manual browsing.** Auto-scroll must be event-driven, not continuous: ordinary recomposition, play/pause state updates, or the user manually scrolling a stable list must not repeatedly snap the list back to the active station.
- [x] **Search remains user-controlled.** While a text search is active, automatic active-station scrolling is suppressed; clearing/closing search may restore active-card visibility if the station belongs to the resulting list.
- [x] **Playback architecture unchanged.** Do not change `playRadioQueue`, Media3 queue construction, repeat policy, fallback/reconnect, notification/lock-screen controls, overlay, Stop, Sleep Timer, favorites persistence, Room schema, or the newly verified Sport catalog.
- [x] **Automated coverage.** Add deterministic tests for selecting the active station index only when it is present, returning no target when absent, and suppressing the target while search is active. Add a structural/UI guard proving `RadioList` owns a remembered `LazyListState` and performs event-keyed scrolling rather than unconditional scrolling.
- [x] **Standard closure.** Create one clean product commit directly from this spec-before; validate the exact commit on a real GitHub Actions runner with `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify persistent debug signer plus APK SHA-256/size; promote only that exact product to `main`; append a docs-only spec-after; publish that exact runtime snapshot through the permanent debug-prerelease workflow; remove every temporary helper/product/validation branch; then repeat the physical Sport gate including active-card visibility.

Physical Sport gate status: **PASS**. The active-card visibility refinement and the previously qualified Sport streams/category queue were confirmed together on device.

Validation record — active radio list visibility refinement:
- Spec-before commit: `2b9f6af3cc7cf13404f69d306b7c1ee1319824f8`, direct child of the prior Sport spec-after. The first temporary spec helper run `33624898084` was rejected at workflow-parse time and created no job or repository change; corrected spec helper run `33625000778`, job `100230547699`, wrote the contract before product code.
- Validated runtime product: `9690beb788384be0a58d9a98f0a9841e27948431` (`fix: keep active radio card visible`), direct child of the spec-before. Its diff is exactly three files: `RadioScreen.kt`, `ActiveRadioVisibilityPolicyTest.kt`, and `ActiveRadioVisibilityArchitectureTest.kt`; playback, queue policy, catalog, persistence and service architecture are untouched.
- Behavior: `RadioList` owns a remembered `LazyListState`; event keys are active station id, rendered station-id list, search auto-scroll enablement, and transient-error leading-item state. If the active station exists but is outside the current viewport, the list animates to its card; if already visible, absent from the rendered category/section, or search is active, no forced scroll occurs. Ordinary play/pause recomposition and manual scrolling of a stable list do not retrigger the effect.
- Category queue semantics were physically rechecked before this correction: selecting a category, explicitly starting a station from it, then using Next/Previous remains within that category snapshot. Merely changing the visible category while an older queue is already playing intentionally does not rebuild that queue.
- Exact validation: GitHub Actions run `33625336178`, job `100231632314`. Although the workflow run head is helper commit `40fd914b6493aa43683a568c48280a202760147e`, the job verified spec/product lineage and the exact three-file diff, then detached `HEAD` to `9690beb788384be0a58d9a98f0a9841e27948431` before any Android build or test.
- Regression/build gate passed `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: `BUILD SUCCESSFUL in 3m 38s`, 208 actionable tasks (162 executed, 36 from cache, 10 up-to-date).
- Validation APK: 23,729,524 bytes; SHA-256 `1d44f270ebdbcd75a37ba6ea3cdaa2422f9e45e0a1bb0a6e0a6127138a1dbbec`; persistent debug signer SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- Promotion occurred only after the gates above: exact runtime `9690beb788384be0a58d9a98f0a9841e27948431` became `main`; product/spec/validation helper branches were then removed. Permanent exact-product debug prerelease publication follows this docs-only record.
- Physical Sport gate: **PASS**. Final device recheck confirmed active-card auto-visibility, stable manual browsing without snap-back, no category hijack, search-time auto-scroll suppression, five-station Sport Previous/Next order with wrap-around, and real audio from ON Sport FM, talkSPORT and Radio Manà Manà Sport Roma.

Permanent Release record — active radio list visibility refinement:
- Permanent publisher run `33626026033`, job `100233848785`, used workflow source from docs-only `main` `e4862834259dc65b0b5a1fdbfe501c6435b85a9a` but resolved input `ref=9690beb788384be0a58d9a98f0a9841e27948431`, detached `HEAD` to that exact runtime, and logged `Building exact commit: 9690beb788384be0a58d9a98f0a9841e27948431`.
- `:app:assembleDebug` passed with `BUILD SUCCESSFUL in 3m 8s` (165 actionable tasks: 131 executed, 34 from cache). Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- GitHub prerelease tag `debug-20260902-114616-9690beb` targets exactly `9690beb788384be0a58d9a98f0a9841e27948431`. Asset `TamalutRadio-debug-9690beb.apk` is uploaded, 23,729,524 bytes, digest `sha256:1d44f270ebdbcd75a37ba6ea3cdaa2422f9e45e0a1bb0a6e0a6127138a1dbbec`, exactly matching the prior validation APK hash.
- Temporary closure/release helper branches were removed. Physical Sport gate is now **PASS** after the final device recheck of the three Sport stations, five-station queue behavior, active-card auto-visibility, manual browsing stability, category isolation, search suppression, background playback, notification and overlay behavior.


Final physical validation record — Sport expansion + active radio visibility:
- [x] User-reported device recheck completed with all requested cases passing on 2026-09-02.
- [x] Active radio card is brought into view when appropriate on Radio entry/re-entry and active-station changes, while stable manual browsing does not snap back.
- [x] Changing to a category that does not contain the active station does not hijack the filter or rewrite the existing Media3 radio queue; search suppresses automatic active-card scrolling until the normal list is restored.
- [x] Sport queue order passed physically as `Radio Sportiva → Rete Sport → ON Sport FM → talkSPORT → Radio Manà Manà Sport Roma`, including Previous/Next wrap-around.
- [x] ON Sport FM, talkSPORT and Radio Manà Manà Sport Roma all produced real audio; background playback, media notification and floating overlay remained normal during the recheck.
- [x] Runtime under test remained exact product `9690beb788384be0a58d9a98f0a9841e27948431`, published as `debug-20260902-114616-9690beb`; no new runtime build is required for this physical-gate closure.

### User-managed custom radio stations — Add / Edit / Delete

This is the next standalone product objective after the fully closed Sport expansion/active-card cycle. The existing 54 built-in stations and their ordering/grouping remain authoritative and must not be rewritten by this feature.

Approved product/UX contract before code:
- [x] **Add custom radio.** The Radio header exposes an explicit add action. The user enters a display name and one primary stream URL. On success a new user-owned station receives a collision-safe stable ID with a `custom-` prefix, is persisted with the existing Room `is_custom=true` flag, and appears in `Tutte` after all 54 built-ins in the repository's existing deterministic custom tail.
- [x] **Dedicated `Personali` filter.** Add `Personali` after the existing `Tutte / Marocco / Italia / Sport / UK` filters. It contains exactly user-managed custom stations and therefore defines a custom-only playback queue when a station is explicitly started from that filter. Built-in country/Sport filters remain unchanged; custom stations remain visible in `Tutte` and are never silently assigned to a built-in category.
- [x] **Edit only user stations.** Only `is_custom=true` rows expose `Modifica`. Editing preserves the existing station ID and favorite relationship, updates trimmed display name + primary stream, and does not mutate any built-in row even if a caller supplies a built-in ID. An edit does not forcibly restart or rewrite an already-playing Media3 queue; the updated definition is used the next time a queue is created from the Radio UI.
- [x] **Delete only user stations.** Only custom rows expose `Elimina`, with an explicit confirmation dialog. Repository/controller defense-in-depth must reject deletion of built-ins. Room's existing foreign-key cascade may remove a favorite record for the deleted custom station. Deleting a station is a library mutation only: it does not introduce a new Stop path or rewrite the currently active Media3 queue; every subsequently created Radio queue must exclude the deleted row.
- [x] **HTTPS syntax gate.** Name and URL are trimmed before validation. Name must be non-empty. URL must be a syntactically valid absolute URI with scheme exactly `https` and a non-empty host; cleartext `http`, relative URLs and unsupported schemes are rejected before any network request.
- [x] **Reachability/redirect gate before persistence.** Add/edit must perform a bounded network probe off the main thread before saving. Follow redirects explicitly with a small finite limit; every hop and final URL must remain HTTPS. Reject redirect loops/excess redirects, cleartext downgrade, network/TLS failure and non-success final HTTP response. Validation is reachability/security validation, not a guarantee that every exotic stream codec is supported.
- [x] **HLS cleartext-child guard.** When the probed resource is identifiable as HLS by final URL/content type or `#EXTM3U`, inspect a bounded manifest body and reject absolute `http://` child-playlist/segment/key references. This preserves the BBC lesson without enabling global cleartext traffic. Do not persist or log response bodies or credentials.
- [x] **No duplicate exact primary URL.** Add must reject an exact normalized primary-stream URL already present in any built-in/custom station. Edit ignores the station being edited when checking duplicates. Duplicate display names are allowed because broadcaster names are not guaranteed unique.
- [x] **Dialog state and errors.** Add/Edit use one Atlas Night Material 3 dialog/form with name + HTTPS URL, visible inline validation/network errors, cancel, and a saving/verifying state that prevents duplicate submission. Successful save closes the dialog and refreshes the snapshot. Delete uses a separate explicit confirmation dialog. No silent destructive action.
- [x] **Favorites/search/queue integration.** Custom stations can be favorited, searched by display name, started from `Tutte`, `Personali`, `Preferiti`, or search results, and participate in the existing pre-search context queue invariant. Search remains display-only filtering: selecting one visible result still launches the complete pre-search queue of the current section/filter. Custom additions/edits/deletions must not change built-in queue order or the existing 54-station count contract.
- [x] **Active-card behavior preserved.** Adding/editing/deleting must retain the approved LIVE-card auto-visibility rules: no category hijack, no continuous snap-back during manual browsing, and no forced active-card scroll while search is active.
- [x] **Persistence architecture unchanged.** Reuse the existing `radio_stations.is_custom`, `RadioStationRepository.saveCustomStation()` / `removeCustomStation()` and current foreign keys. No Room schema migration, DataStore field, new service, player, MediaSession, foreground service, background worker or additional network library is authorized solely for this objective.
- [x] **Automated coverage.** Cover repository custom save/update/delete protection and deterministic custom tail; custom-ID set projection; `Personali` filter membership/order; add/edit/delete controller behavior; duplicate URL protection; HTTPS/redirect/HLS validation with deterministic fake/local connections; ViewModel dialog state/error/success flows; and regressions for favorites/search/full-context queue/active-card behavior. Built-in catalog tests must remain exactly 54.
- [x] **Standard closure.** Create one clean product commit directly from this spec-before. Validate the exact detached product in real GitHub Actions with `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify persistent debug signer v1 plus APK SHA-256/size; promote only that exact product to `main`; append docs-only spec-after; publish the exact runtime through the permanent GitHub debug-prerelease workflow; verify Release target/asset/digest/signer; remove all temporary helper/product/validation branches. Physical custom-radio gate remains pending until add/edit/delete + playback are exercised on device.


Validation record — user-managed custom radio stations:
- [x] Spec-before commit: `2536d288b07ba5768d1b220eeebbc395fbba7871` (`docs: define custom radio management contract`). Two earlier helper attempts failed before any spec commit/push (one YAML parse rejection; one helper payload path error); neither modified `main`. Corrected spec run `33632306083`, job `100254510190`, recorded the contract before product code and removed its helper branch.
- [x] Clean product commit: `cdeca8294a6cc50ab8ca99380295d5d5263ecd24` (`feat: add custom radio management`), direct child of the spec-before. Product diff is exactly ten expected files: existing radio repository/data-source/grouping/ViewModel/UI, one HTTPS stream validator, and focused data/feature validator-management tests. No Room schema, Media3 service/session/controller, playback engine, overlay, Stop or Sleep Timer file changed.
- [x] Persistence reuses the existing `radio_stations.is_custom` field and repository APIs. New custom IDs use a collision-safe `custom-` prefix; repository defense-in-depth rejects attempts to overwrite/delete built-ins. Built-ins remain exactly 54; custom stations remain a deterministic name/id tail after them.
- [x] Radio now exposes `Personali` after `Tutte / Marocco / Italia / Sport / UK`. Custom stations remain visible in `Tutte`, can be favorited/searched/played, and a station started from `Personali` receives the complete custom-only pre-search context queue. Built-in country/Sport queues and active-card auto-visibility rules are unchanged.
- [x] Add/Edit perform trimmed name + HTTPS-only syntax validation, reject duplicate normalized primary URLs, then run a bounded off-main-thread HTTPS GET probe with explicit finite redirects. Every redirect target must remain HTTPS; final status must be 2xx. HLS resources are inspected within a bounded prefix and absolute `http://` references are rejected. No new network library or cleartext opt-in was added.
- [x] Custom-only edit/delete actions are explicit Material 3 UI. Edit preserves station ID/favorite linkage. Delete requires confirmation; later queues exclude the row while an already-active Media3 queue is not forcibly rewritten.
- [x] Exact validation run `33633802837`, job `100259488320`, verified spec/product lineage plus the exact ten-file diff, detached `HEAD` to `cdeca8294a6cc50ab8ca99380295d5d5263ecd24`, then passed `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`. Gradle: `BUILD SUCCESSFUL in 3m 5s`; 208 actionable tasks (161 executed, 37 from cache, 10 up-to-date).
- [x] Validation APK: **23,778,676 bytes**; SHA-256 `14e8a16bf5caaee86461a5ff1c2f165fe4ad83d8ec52da546b9ac26b24fdcb9d`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] The same successful validation job promoted exactly `cdeca8294a6cc50ab8ca99380295d5d5263ecd24` from the spec-before to `main`, then removed the temporary product and validation branches.
- [x] Permanent Release evidence: VERIFIED below for exact runtime `cdeca8294a6cc50ab8ca99380295d5d5263ecd24`.
- [x] Physical custom-radio gate: SUPERSEDED by the later Settings/category revision and its completed physical gate; the obsolete `cdeca829…` UI is not a separate remaining gate.

Permanent Release record — user-managed custom radio stations:
- [x] Permanent publisher run `33634279546`, job `100261104037`, started from docs-only workflow source `bbe81a6b8d7b33c2d860891dacba562ce2ecd741` but resolved explicit input `ref=cdeca8294a6cc50ab8ca99380295d5d5263ecd24`, detached `HEAD` to that exact runtime, and logged `Building exact commit: cdeca8294a6cc50ab8ca99380295d5d5263ecd24`.
- [x] `:app:assembleDebug` passed with `BUILD SUCCESSFUL in 3m 39s` (165 actionable tasks: 131 executed, 34 from cache). Persistent debug signer SHA-256 remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`.
- [x] GitHub prerelease `debug-20260902-131555-cdeca82` targets exactly `cdeca8294a6cc50ab8ca99380295d5d5263ecd24`. Asset `TamalutRadio-debug-cdeca82.apk` is `uploaded`, **23,778,676 bytes**, digest `sha256:14e8a16bf5caaee86461a5ff1c2f165fe4ad83d8ec52da546b9ac26b24fdcb9d`, exactly matching the prior validation APK SHA-256.
- [x] Release URL: `https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/tag/debug-20260902-131555-cdeca82`. Direct APK: `https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/download/debug-20260902-131555-cdeca82/TamalutRadio-debug-cdeca82.apk`.
- [x] Temporary product, validation, spec-after and release-record helper branches are removed at automated closure. This older physical gate was superseded by the later Settings/category revision and is not independently pending.


### Custom radio settings + assignable categories revision — contract

This revision supersedes the custom-radio **UI/category** contract implemented by runtime `cdeca8294a6cc50ab8ca99380295d5d5263ecd24`. Its HTTPS validation, custom ownership protection and basic persistence remain useful implementation foundations, but the `Personali` filter, Radio-header add action and per-card edit/delete menu are not the final approved UX and must be removed before the custom-radio physical gate can close.

Approved behavior before revised product code:
- [x] **Management lives in Settings only.** The Radio screen exposes no add button and no edit/delete menu on station cards. `Impostazioni` exposes a compact `Gestione radio` card with exactly two entry actions: `Aggiungi radio` and `Modifica radio`. There is no permanent list/grid of custom stations in Settings.
- [x] **Add flow.** `Aggiungi radio` opens the editor directly and requires three user fields: trimmed display name, HTTPS stream URL and category. Existing URL syntax/reachability/redirect/HLS security checks remain mandatory before persistence.
- [x] **Edit flow uses only a transient selector.** `Modifica radio` first opens a temporary picker containing only user-created stations. Selecting one opens the same editor prefilled with name, URL and category. If no custom stations exist, show a concise empty state rather than a persistent station list. The editor exposes `Elimina radio` only while editing, with explicit destructive confirmation.
- [x] **Assignable standard categories.** Custom stations may be assigned to `Marocco`, `Italia`, `Sport` or `UK`. In a standard category queue, built-ins retain their authoritative relative order and assigned custom stations follow the built-ins in deterministic name/id order.
- [x] **User-defined category.** The category selector also offers existing user-defined categories plus `+ Nuova categoria…`. Choosing it reveals a required category-name field. Names are trimmed, compared case-insensitively for identity, may contain normal user-facing text, and may not duplicate/reserve `Marocco`, `Italia`, `Sport`, `UK`, `Tutte`, or `Preferiti` under case-insensitive comparison. A canonical existing spelling is reused when the user enters an equivalent existing custom category.
- [x] **Dynamic Radio filters.** Remove the fixed `Personali` chip. Radio filter chips are `Tutte / Marocco / Italia / Sport / UK`, followed by currently used user-defined category names in deterministic case-insensitive order. A custom category disappears automatically when no station references it; no separate category-management list is introduced.
- [x] **Queue semantics.** Starting a station from a standard or user-defined category snapshots the complete current category queue before text search, preserving the existing search invariant and Previous/Next wrap behavior. Starting a custom station from `Tutte` or `Preferiti` likewise uses the complete corresponding pre-search context. Merely changing the visible filter does not rewrite an already-active Media3 queue.
- [x] **Edit/category move semantics.** Editing preserves stable station ID and favorite relationship. Changing category does not force-stop/restart the current station or rewrite an active queue; future snapshots use the new category. Deleting remains a library mutation with the already-approved foreign-key favorite cascade and no new Stop path.
- [x] **Persistence migration v1→v2.** Add one nullable custom-category column to `radio_stations` and bump Room schema to v2 with an explicit migration registered by the app. Built-in rows remain null. Any pre-existing v1 custom row is retained and migrated to category `Altro`, so upgrades from the already-published `cdeca829…` build lose no user-created station. New/edited custom rows must always persist a non-blank category. No separate categories table, DataStore field or new persistence subsystem is authorized.
- [x] **Architecture boundaries.** Keep one Media3 player/session/service, existing playback/fallback/live-edge/search/overlay/Stop/Sleep-Timer behavior, the 54 built-in catalog and signer contract unchanged. No new network library is needed solely for this revision.
- [x] **Required automated coverage.** Cover Room v1→v2 migration with built-ins null and legacy custom→`Altro`; repository category save/update/delete projection; standard-category custom tail; dynamic custom-category discovery/order/removal; reserved/case-insensitive category normalization; Settings add/edit transient-picker behavior; no Radio-screen management controls/`Personali`; full-context queues/search/favorites/active-card regressions; built-in count remains exactly 54.
- [x] **Standard closure.** Create one clean revised product commit directly from this spec-before; validate exact detached product in GitHub Actions with `:core:database:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; verify APK size/SHA-256 and persistent debug signer; promote only that exact product; append docs-only spec-after; publish that exact runtime through the permanent debug-prerelease workflow; verify Release target/asset/digest/signer; remove all temporary branches/helpers. Physical custom-radio gate remains pending until Settings add/edit/delete, category assignment/dynamic filtering and playback queues are exercised on device.


Validation record — custom radio settings + assignable categories revision:
- [x] Spec-before commit: `61546efbd203bda6d43695a8599ea1ad2e70a73c` (`docs: define custom radio settings categories`).
- [x] Clean runtime product: `9176ced98b886c61464b1ffc4859ca8a7483dac6` (`feat: move custom radio management to settings`), direct child of the spec-before. The product diff is exactly 17 expected production/test/schema files and contains no temporary CI helper.
- [x] Revised UX removes Radio-header add, station-card edit/delete and fixed `Personali`; Settings owns `Gestione radio` with direct Add and transient Edit selection. Add/Edit use name + HTTPS stream + category; Delete remains edit-only with confirmation.
- [x] Category semantics support `Marocco / Italia / Sport / UK`, existing user-defined categories and `+ Nuova categoria…`; dynamic custom-category filters are derived from currently used categories, standard queues keep built-ins first and deterministic custom tails, and queue/search/favorites behavior remains context-snapshot based.
- [x] Persistence is Room schema v2 with nullable `custom_category` plus explicit `MIGRATION_1_2`; built-ins remain null and pre-existing v1 custom rows migrate to `Altro`. Repository compatibility defaults legacy internal save calls to `Altro`, while the revised UI always supplies an explicit category.
- [x] Builder/preflight run `33640881730`, job `100283342250`, passed focused database/data/radio/app tests, generated and verified Room schema v2, verified the exact 17-file product diff, then created the single clean product commit.
- [x] Exact detached validation run `33641947895`, job `100286955037`, verified spec/product lineage, checked out `9176ced98b886c61464b1ffc4859ca8a7483dac6` detached, and passed `:core:database:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:radio:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:feature:library:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug`: `BUILD SUCCESSFUL in 4m 3s`, 213 actionable tasks (167 executed, 36 from cache, 10 up-to-date).
- [x] Validation APK: **23,795,060 bytes**; SHA-256 `dadaf967fc49fc40197e5e1cc170f1a0b336dc68a08366485a94e58deee9c1fb`; persistent debug signer v1 SHA-256 `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6` (`CN=Android Debug, O=TamalutRadio, C=IT`).
- [x] The same successful validation job promoted exactly `9176ced98b886c61464b1ffc4859ca8a7483dac6` to `main` only after all gates passed and removed product/validation helper branches; repository branch cleanup left only `main`.
- [x] Permanent Release evidence: VERIFIED for exact runtime `9176ced98b886c61464b1ffc4859ca8a7483dac6`; see permanent release record below.
- [x] Physical custom-radio settings/category gate: PASS on permanent prerelease `debug-20260902-144400-9176ced` for exact runtime `9176ced98b886c61464b1ffc4859ca8a7483dac6` (physical user report, 2026-09-02).

Physical closure record — custom radio settings + assignable categories revision:
- [x] Radio surface: no add button, no per-card edit/delete management and no fixed `Personali`; Radio remains listening/filter/search/favorites only.
- [x] Settings ownership: `Gestione radio` exposes direct Add plus transient custom-only Edit selection and no persistent custom-radio list.
- [x] Add/category behavior: standard-category custom stations appear after canonical built-ins; `+ Nuova categoria…` creates a dynamic Radio filter containing the saved station.
- [x] Edit behavior: custom-only picker opens the prefilled editor; name/category changes persist, category moves are reflected in future lists, and favorite linkage remains intact.
- [x] Delete behavior: deletion requires explicit confirmation; deleted rows disappear from subsequent lists/queues and an empty user-defined category filter disappears.
- [x] Queue/search behavior: Previous/Next and wrap use the explicitly started category snapshot; merely switching visible category does not rewrite the active Media3 queue; search remains display-only and starting a search result retains the full pre-search category context.
- [x] Playback regressions: custom-station playback, background continuation, media notification, floating overlay, play/pause and Previous/Next all passed with no double audio or stale-title regression reported.
- [x] Migration note: the v1→v2 legacy-row case was included in the requested physical checklist. The user reported the complete requested gate PASS; because presence of an actual pre-existing v1 custom row was not separately stated, no sampled legacy row is claimed here. Automated migration coverage for legacy custom→`Altro` remains PASS.

Permanent Release record — custom radio settings + assignable categories revision:
- [x] Permanent publisher run `33643555539`, job `100292482083`, used workflow source from docs-only `main` `6e2b3abb0715e2216b836e310b49f6ff615eeeee` but explicit input `ref=9176ced98b886c61464b1ffc4859ca8a7483dac6`; the job detached `HEAD` to that exact runtime and logged `Building exact commit: 9176ced98b886c61464b1ffc4859ca8a7483dac6`.
- [x] `:app:assembleDebug` passed with `BUILD SUCCESSFUL in 3m 3s` (165 actionable tasks: 131 executed, 34 from cache). Persistent debug signer remained `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6` (`CN=Android Debug, O=TamalutRadio, C=IT`).
- [x] GitHub prerelease `debug-20260902-144400-9176ced` targets exactly `9176ced98b886c61464b1ffc4859ca8a7483dac6`. Asset `TamalutRadio-debug-9176ced.apk` is uploaded, **23,795,060 bytes**, digest `sha256:dadaf967fc49fc40197e5e1cc170f1a0b336dc68a08366485a94e58deee9c1fb`, exactly matching the prior detached validation APK.
- [x] Release URL: `https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/tag/debug-20260902-144400-9176ced`. Direct APK: `https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/download/debug-20260902-144400-9176ced/TamalutRadio-debug-9176ced.apk`.
- [x] Automated/distribution closure and the physical custom-radio settings/category gate are complete for exact runtime `9176ced98b886c61464b1ffc4859ca8a7483dac6`; roadmap item 2 (Radio personalizzate) is closed.

Sport regression note on the superseded custom-radio runtime:
- [x] User physically rechecked ON Sport FM, talkSPORT and Radio Manà Manà Sport Roma on `debug-20260902-131555-cdeca82`; real audio, five-station Sport order/wrap, background playback, notification, overlay and absence of `Personali` side effects on Sport all passed. This confirms Sport remained regression-safe before the present custom-radio UX revision.

### Android Auto phone-projection discovery + baseline physical certification — contract

This objective starts from the existing single `MediaLibraryService` / `MediaLibrarySession` Android Auto browsing foundation. Repository inspection before code found that the phone APK does not yet declare the official Android Auto application metadata and does not package `res/xml/automotive_app_desc.xml`; therefore the current prerelease must not be treated as physically certifiable in Android Auto until discovery is declared.

- [ ] Add only the official Android Auto media discovery declaration to the phone app: application metadata `com.google.android.gms.car.application` referencing `@xml/automotive_app_desc`, with `<automotiveApp><uses name="media"/></automotiveApp>` in the referenced XML resource.
- [ ] Preserve the existing exported Media3 `MediaLibraryService`, legacy `android.media.browse.MediaBrowserService` action, single ExoPlayer/session/service architecture, notification controls, Stop/Exit policy, radio fallback/live reconnect, local Music playback, custom-radio behavior, overlay and Sleep Timer.
- [ ] Do not introduce Android for Cars App Library templates, a custom driving UI, a second car/player service, Android Automotive OS packaging, new permissions, or catalog/database changes in this prerequisite.
- [ ] Preserve the current minimal browse tree exactly for this baseline certification: `TamalutRadio -> Radio di test -> Radio Azawan / HIT RADIO Maroc / Radio Mars`. Production catalog expansion is not part of this prerequisite and must not be implied by a successful baseline discovery test.
- [ ] Automated validation must run `:core:playback:testDebugUnitTest`, `:app:testDebugUnitTest`, and `:app:assembleDebug` on the exact detached product commit, verify the merged APK manifest contains the Android Auto metadata, verify the packaged `automotive_app_desc` declares media support, and verify the persistent debug signer v1 plus APK SHA-256/size before promotion.
- [ ] Publish the exact validated runtime as a permanent GitHub debug prerelease before physical testing. Physical baseline gate must verify: TamalutRadio appears in Android Auto's media apps, the `Radio di test` node and its three stations are browsable, selecting a station starts the shared Media3 session, Play/Pause and Previous/Next operate the same queue, metadata stays synchronized with the phone/notification, background/phone interaction does not create double audio, and disconnect/reconnect does not create a second session.
- [ ] Only after that physical baseline passes may Android Auto discovery/compatibility be recorded as physically verified. A separate production-library browsing objective is required before claiming that Android Auto exposes the full TamalutRadio radio/custom/local-music catalog.
