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

- [x] UI polish sub-step 1/3: application shell and bottom navigation. Replace the provisional top buttons with a Material 3 bottom navigation bar for `Radio`, `Musica`, `In Riproduzione`, and `Impostazioni`; keep Radio and Musica wired to their real routes, and provide restrained Atlas Night placeholders for the latter two destinations. Preserve current ViewModel instances, playback services, repository wiring, system-following theme behavior, and edge-to-edge-safe content padding. Use Material icons and coherent destination labels, with selection styling driven by the existing Material 3 color scheme. Verification: `./gradlew :app:assembleDebug` plus structural checks for all four destinations and no playback/data regressions.

- [x] UI polish sub-step 2/3: Radio visual refinement. Keep existing catalog, favorites, tabs, playback gateway, and errors unchanged while refining header hierarchy, spacing, station cards, leading radio icon treatment, favorite action, borders/elevation, and current-station emphasis. A currently playing radio station must display a compact `LIVE` badge and an accessible `In riproduzione` state. Visuals must use Atlas Night semantic colors through `MaterialTheme`, with restrained Sahara Pulse sand/gold, Atlas green, and terracotta accents rather than hard-coded unrelated colors. Verification: `:feature:radio:testDebugUnitTest :app:assembleDebug` and structural checks that playback/favorites behavior remains wired.

- [x] UI polish sub-step 3/3: local Music visual refinement. Keep SAF selection/persisted permission/scanning/playback behavior unchanged while refining the screen header, selected-folder panel, actions, empty/loading/error presentation, local-track cards, leading music icon treatment, metadata hierarchy, borders/elevation, and current-track emphasis. The current local item must retain an accessible `In riproduzione` indication. Verification: `:feature:library:testDebugUnitTest :app:assembleDebug` plus merged-manifest checks confirming no broad storage permission was added.

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

## Proposte future approvate, da pianificare

### Overlay flottante di controllo sopra altre app

- **Sotto-passaggio 1/2 e raffinamento 1.5/2 completati e verificati in CI; sotto-passaggio 2/2 (controlli Media3 condivisi) resta da implementare dopo il test fisico del 1.5.**
- Prevedere un overlay flottante che possa restare visibile sopra altre app, incluse Google Maps e Waze, anche dopo l'uscita da TamalutRadio tramite tasto Home.
- L'overlay dovrà offrire controlli minimi `precedente / pausa-play / successivo`, collegati alla stessa sessione Media3 condivisa e senza creare un secondo player.
- L'utente dovrà poter chiudere l'overlay senza fermare la riproduzione in corso; la chiusura è temporanea per la sessione esterna corrente e non disattiva la preferenza permanente.
- Richiede il permesso speciale Android **Visualizza sopra altre app** (`SYSTEM_ALERT_WINDOW`), da richiedere solo con azione e consenso espliciti dell'utente e con UX dedicata per stato permesso/negazione/revoca. La preferenza permanente resta separata dallo stato del permesso.
- Il raffinamento 1.5 introduce edge-tab minimale, drag/snap ai bordi, posizione persistita e lifecycle Coordinator/SessionState; i controlli di trasporto restano esclusivamente nel 2/2.

## Decision log

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
